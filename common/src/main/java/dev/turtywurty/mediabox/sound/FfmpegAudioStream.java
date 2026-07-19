package dev.turtywurty.mediabox.sound;

import dev.turtywurty.mediabox.MediaBox;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class FfmpegAudioStream implements AudioStream {
    private static final int DEFAULT_SAMPLE_RATE = 44_100;
    private static final int INITIAL_PCM_BYTES = 3_840;
    private static final long STARTUP_TIMEOUT_SECONDS = 10;
    private static final long FFPROBE_TIMEOUT_SECONDS = 10;

    private final Process process;
    private final InputStream pcm;
    private final AudioFormat format;
    private byte[] initialPcm;
    private int initialPcmOffset;

    private FfmpegAudioStream(Process process, int sampleRate, String mediaLocation) throws IOException {
        this.process = process;
        this.pcm = process.getInputStream();
        this.format = new AudioFormat(sampleRate, 16, 1, true, false);
        this.initialPcm = readInitialPcm(mediaLocation);
    }

    public static AudioStream open(String mediaLocation) throws IOException {
        return open(AudioPlaybackState.streaming(mediaLocation, true));
    }

    public static AudioStream open(AudioPlaybackState playbackState) throws IOException {
        String mediaLocation = playbackState.mediaLocation();
        Path ffmpeg = FfmpegNatives.requireFfmpeg(Minecraft.getInstance().gameDirectory.toPath());
        int sampleRate = detectSampleRate(mediaLocation);
        List<String> command = new ArrayList<>();
        command.addAll(List.of(
                ffmpeg.toString(),
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-rw_timeout", "5000000"
        ));

        if (playbackState.looping()) {
            command.add("-stream_loop");
            command.add("-1");
        }

        if (playbackState.startPositionSeconds() > 0.0) {
            command.add("-ss");
            command.add(String.format(Locale.ROOT, "%.3f", playbackState.startPositionSeconds()));
        }

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
            var stream = new FfmpegAudioStream(process, sampleRate, mediaLocation);
            MediaBox.LOGGER.info(
                    "FFmpeg is decoding audio at {} Hz from {} seconds: {}",
                    sampleRate,
                    String.format(Locale.ROOT, "%.3f", playbackState.startPositionSeconds()),
                    mediaLocation
            );
            return stream;
        } catch (IOException | RuntimeException exception) {
            process.destroyForcibly();
            logFfprobeDiagnostics(mediaLocation);
            throw exception;
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
            Process process = new ProcessBuilder(
                    ffprobe.toString(),
                    "-v", "error",
                    "-rw_timeout", "5000000",
                    "-select_streams", "a:0",
                    "-show_entries", "stream=sample_rate",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    mediaLocation)
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
            Process process = new ProcessBuilder(
                    ffprobe.toString(),
                    "-v", "error",
                    "-rw_timeout", "5000000",
                    "-show_entries", "format=format_name,format_long_name:stream=codec_type,codec_name,profile,sample_rate",
                    "-of", "default=noprint_wrappers=1",
                    mediaLocation)
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
