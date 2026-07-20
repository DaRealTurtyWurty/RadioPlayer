package dev.turtywurty.mediabox.sound;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.client.video.ClientVideoManager;
import dev.turtywurty.mediabox.client.ytdlp.YtDlpVideoResolver;
import dev.turtywurty.mediabox.ffmpeg.FfmpegNatives;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.util.Util;
import org.jspecify.annotations.NonNull;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class FfmpegAudioStream implements PlaybackStartedAudioStream {
    private static final int DEFAULT_SAMPLE_RATE = 44_100;
    private static final int INITIAL_PCM_BYTES = 3_840;
    private static final long STARTUP_TIMEOUT_SECONDS = 10;
    private static final long FFPROBE_TIMEOUT_SECONDS = 10;

    private final Process process;
    private final InputStream pcm;
    private final AudioFormat format;
    private final AudioPlaybackState playbackState;
    private double audibleStartPositionSeconds;
    private byte[] initialPcm;
    private int initialPcmOffset;

    private FfmpegAudioStream(
            Process process,
            int sampleRate,
            String mediaLocation,
            AudioPlaybackState playbackState
    ) throws IOException {
        this.process = process;
        this.pcm = process.getInputStream();
        this.format = new AudioFormat(sampleRate, 16, 1, true, false);
        this.playbackState = playbackState;
        this.audibleStartPositionSeconds = playbackState.startPositionSeconds();
        this.initialPcm = readInitialPcm(mediaLocation);
    }

    public static AudioStream open(String mediaLocation) throws IOException {
        return open(AudioPlaybackState.streaming(mediaLocation, true));
    }

    public static AudioStream open(AudioPlaybackState playbackState) throws IOException {
        String mediaLocation = playbackState.mediaLocation();
        Path ffmpeg = FfmpegNatives.requireFfmpeg(Minecraft.getInstance().gameDirectory.toPath());
        // A remote FFprobe request can take several seconds. For synchronized
        // video audio, FFmpeg can resample to a known output rate and avoid
        // letting the position snapshot go stale while probing.
        int sampleRate = playbackState.synchronizedVideoSessionId() == null
                ? detectSampleRate(mediaLocation)
                : DEFAULT_SAMPLE_RATE;
        AudioPlaybackState effectiveState = refreshSynchronizedPosition(playbackState);
        mediaLocation = effectiveState.mediaLocation();
        List<String> command = new ArrayList<>();
        command.addAll(List.of(
                ffmpeg.toString(),
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-rw_timeout", "5000000"
        ));

        if (effectiveState.looping()) {
            command.add("-stream_loop");
            command.add("-1");
        }

        if (effectiveState.startPositionSeconds() > 0.0) {
            command.add("-ss");
            command.add(String.format(Locale.ROOT, "%.3f", effectiveState.startPositionSeconds()));
        }

        command.addAll(YtDlpVideoResolver.inputOptions(mediaLocation));
        command.add("-i");
        command.add(mediaLocation);
        command.addAll(List.of(
                "-map", "0:a:0",
                "-vn",
                "-ac", "1",
                "-ar", Integer.toString(sampleRate),
                "-f", "s16le",
                "pipe:1"
        ));

        Process process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();

        try {
            var stream = new FfmpegAudioStream(process, sampleRate, mediaLocation, effectiveState);
            MediaBox.LOGGER.info(
                    "FFmpeg is decoding audio at {} Hz from {} seconds: {}",
                    sampleRate,
                    String.format(Locale.ROOT, "%.3f", effectiveState.startPositionSeconds()),
                    mediaLocation
            );
            return stream;
        } catch (IOException | RuntimeException exception) {
            process.destroyForcibly();
            logFfprobeDiagnostics(mediaLocation);
            throw exception;
        }
    }

    private static AudioPlaybackState refreshSynchronizedPosition(AudioPlaybackState playbackState) {
        if (playbackState.synchronizedVideoSessionId() == null)
            return playbackState;

        return ClientVideoManager.getAudioClock(playbackState.synchronizedVideoSessionId())
                .map(clock -> playbackState.atRuntimePosition(
                        clock.mediaLocation(),
                        clock.positionSeconds(),
                        clock.playbackRate(),
                        clock.discontinuityRevision()
                ))
                .orElse(playbackState);
    }

    /**
     * FFmpeg still needs a short time to connect and produce its first PCM. Drop
     * that elapsed portion so the first sample queued by OpenAL matches the
     * video clock at the point the stream becomes ready.
     */
    public void synchronizeToVideoNow() throws IOException {
        AudioPlaybackState playbackState = this.playbackState;
        if (playbackState.synchronizedVideoSessionId() == null)
            return;

        Optional<ClientVideoManager.AudioClock> currentClock =
                ClientVideoManager.getAudioClock(playbackState.synchronizedVideoSessionId());
        if (currentClock.isEmpty())
            return;

        ClientVideoManager.AudioClock clock = currentClock.get();
        if (clock.discontinuityRevision() != playbackState.revision()
                || !clock.mediaLocation().equals(playbackState.mediaLocation()))
            return;

        double secondsToSkip = clock.positionSeconds() - playbackState.startPositionSeconds();
        if (!Double.isFinite(secondsToSkip) || secondsToSkip <= 0.0)
            return;

        long framesToSkip = (long) Math.floor(secondsToSkip * this.format.getSampleRate());
        long bytesToSkip = Math.multiplyExact(framesToSkip, this.format.getFrameSize());
        discardPcm(bytesToSkip);
        this.audibleStartPositionSeconds += framesToSkip / this.format.getSampleRate();
        MediaBox.LOGGER.debug(
                "Skipped {} seconds of audio produced during synchronized video startup",
                String.format(Locale.ROOT, "%.3f", secondsToSkip)
        );
    }

    private void discardPcm(long bytesToSkip) throws IOException {
        if (bytesToSkip <= 0L)
            return;

        if (this.initialPcm != null) {
            int initialRemaining = this.initialPcm.length - this.initialPcmOffset;
            int initialDiscard = (int) Math.min(bytesToSkip, initialRemaining);
            this.initialPcmOffset += initialDiscard;
            bytesToSkip -= initialDiscard;
            if (this.initialPcmOffset == this.initialPcm.length) {
                this.initialPcm = null;
                this.initialPcmOffset = 0;
            }
        }

        byte[] discardBuffer = new byte[8_192];
        while (bytesToSkip > 0L) {
            int requested = (int) Math.min(bytesToSkip, discardBuffer.length);
            int read = this.pcm.read(discardBuffer, 0, requested);
            if (read == -1)
                throw new IOException("FFmpeg stopped while synchronizing audio to video");

            bytesToSkip -= read;
        }
    }

    public static void validate(String mediaLocation) throws IOException {
        MediaBox.LOGGER.info("Validating audio stream with FFmpeg: {}", mediaLocation);
        try (AudioStream _ = open(mediaLocation)) {
            // Opening the stream and receiving initial PCM proves that FFmpeg can decode it.
            MediaBox.LOGGER.info("Audio stream validation succeeded: {}", mediaLocation);
        } catch (IOException | RuntimeException exception) {
            MediaBox.LOGGER.warn("Audio stream validation failed: {}", mediaLocation, exception);
            throw exception;
        }
    }

    private static int detectSampleRate(String mediaLocation) throws IOException {
        Path ffprobe = FfmpegNatives.requireFfprobe(Minecraft.getInstance().gameDirectory.toPath());
        try {
            List<String> command = new ArrayList<>(List.of(
                    ffprobe.toString(),
                    "-v", "error",
                    "-rw_timeout", "5000000",
                    "-select_streams", "a:0",
                    "-show_entries", "stream=sample_rate",
                    "-of", "default=noprint_wrappers=1:nokey=1"
            ));
            command.addAll(YtDlpVideoResolver.inputOptions(mediaLocation));
            command.add(mediaLocation);
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            if (!process.waitFor(FFPROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("FFprobe timed out");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0)
                throw new IOException("FFprobe exited with code " + process.exitValue() + ": " + output);

            int sampleRate = Integer.parseInt(output.lines().findFirst().orElse(""));
            if (sampleRate > 0) {
                MediaBox.LOGGER.info("FFprobe detected {} Hz audio for {}", sampleRate, mediaLocation);
                return sampleRate;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while probing " + mediaLocation, exception);
        } catch (IOException | NumberFormatException exception) {
            MediaBox.LOGGER.warn("Could not detect the sample rate with FFprobe for {}; using {} Hz", mediaLocation,
                    DEFAULT_SAMPLE_RATE, exception);
        }

        return DEFAULT_SAMPLE_RATE;
    }

    private static void logFfprobeDiagnostics(String mediaLocation) {
        try {
            Path ffprobe = FfmpegNatives.requireFfprobe(Minecraft.getInstance().gameDirectory.toPath());
            List<String> command = new ArrayList<>(List.of(
                    ffprobe.toString(),
                    "-v", "error",
                    "-rw_timeout", "5000000",
                    "-show_entries", "format=format_name,format_long_name:stream=codec_type,codec_name,profile,sample_rate",
                    "-of", "default=noprint_wrappers=1"
            ));
            command.addAll(YtDlpVideoResolver.inputOptions(mediaLocation));
            command.add(mediaLocation);
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            if (!process.waitFor(FFPROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                MediaBox.LOGGER.warn("FFprobe diagnostic timed out for {}", mediaLocation);
                return;
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            MediaBox.LOGGER.warn("FFprobe diagnostic for {} (exit {}): {}", mediaLocation, process.exitValue(),
                    output.isEmpty() ? "no metadata returned" : output);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException)
                Thread.currentThread().interrupt();

            MediaBox.LOGGER.warn("Could not collect FFprobe diagnostics for {}", mediaLocation, exception);
        }
    }

    private byte[] readInitialPcm(String mediaLocation) throws IOException {
        try {
            byte[] bytes = CompletableFuture.supplyAsync(() -> {
                try {
                    return this.pcm.readNBytes(INITIAL_PCM_BYTES);
                } catch (IOException exception) {
                    throw new FfmpegReadException(exception);
                }
            }, Util.nonCriticalIoPool()).get(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (bytes.length == INITIAL_PCM_BYTES)
                return bytes;

            throw new IOException("FFmpeg exited before producing audio for " + mediaLocation);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for FFmpeg audio", exception);
        } catch (TimeoutException exception) {
            throw new IOException("FFmpeg timed out before producing audio for " + mediaLocation, exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof FfmpegReadException readException)
                throw readException.getCause();

            throw new IOException("Could not read FFmpeg audio", cause);
        }
    }

    @Override
    public @NonNull AudioFormat getFormat() {
        return this.format;
    }

    @Override
    public void onPlaybackStarted() {
        publishPlaybackPosition(0L);
    }

    @Override
    public void onPlaybackCursor(long playedFrames) {
        publishPlaybackPosition(playedFrames);
    }

    private void publishPlaybackPosition(long playedFrames) {
        UUID sessionId = this.playbackState.synchronizedVideoSessionId();
        if (sessionId == null)
            return;
        double position = this.audibleStartPositionSeconds
                + Math.max(0L, playedFrames) / this.format.getSampleRate();
        ClientVideoManager.updateAudioPlaybackPosition(
                sessionId,
                position,
                this.playbackState.revision()
        );
    }

    @Override
    public @NonNull ByteBuffer read(int expectedSize) throws IOException {
        ByteBuffer output = BufferUtils.createByteBuffer(expectedSize);
        if (this.initialPcm != null) {
            int count = Math.min(output.remaining(), this.initialPcm.length - this.initialPcmOffset);
            output.put(this.initialPcm, this.initialPcmOffset, count);
            this.initialPcmOffset += count;
            if (this.initialPcmOffset == this.initialPcm.length) {
                this.initialPcm = null;
                this.initialPcmOffset = 0;
            }
        }

        byte[] buffer = new byte[Math.min(output.remaining(), 8_192)];
        while (output.hasRemaining()) {
            int read = this.pcm.read(buffer, 0, Math.min(buffer.length, output.remaining()));
            if (read == -1)
                throw new IOException("FFmpeg stopped producing audio");

            output.put(buffer, 0, read);
        }

        output.flip();
        return output;
    }

    @Override
    public void close() {
        try {
            this.pcm.close();
        } catch (IOException exception) {
            MediaBox.LOGGER.debug("Could not close FFmpeg audio stream", exception);
        }
        this.process.destroyForcibly();
    }

    private static final class FfmpegReadException extends RuntimeException {
        private FfmpegReadException(IOException cause) {
            super(cause);
        }

        @Override
        public IOException getCause() {
            return (IOException) super.getCause();
        }
    }
}
