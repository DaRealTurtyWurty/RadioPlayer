package dev.turtywurty.radioplayer.sound;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.format.Pcm16AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import dev.turtywurty.radioplayer.Radioplayer;
import dev.turtywurty.radioplayer.client.FfprobeNativeExtractor;
import net.minecraft.client.sounds.AudioStream;
import org.jspecify.annotations.NonNull;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LavaPlayerAudioStream implements AudioStream {
    private static final int DEFAULT_SAMPLE_RATE = 44_100;
    private static final int FRAME_TIMEOUT_MS = 250;
    private static final long FFPROBE_TIMEOUT_MS = 10_000;

    private final AudioPlayer player;
    private final AudioFormat format;
    private byte[] pendingFrame;
    private int pendingFrameOffset;
    private boolean closed;

    public LavaPlayerAudioStream(String url) throws IOException {
        AudioPlayerManager playerManager = createPlayerManager(detectSampleRate(url));
        AudioTrack track = loadTrack(playerManager, url);
        this.player = playerManager.createPlayer();
        this.player.playTrack(track);
        AudioFrame firstFrame = provideFrame(10, TimeUnit.SECONDS);
        if (firstFrame == null)
            throw new IOException("Timed out waiting for initial audio data from URL: " + url);

        this.format = createAudioFormat(firstFrame.getFormat());
        this.pendingFrame = firstFrame.getData();
    }

    public static AudioStream open(String url) throws IOException {
        try {
            return new LavaPlayerAudioStream(url);
        } catch (IOException | RuntimeException exception) {
            AudioStream ffmpegStream = FfmpegAudioStream.tryCreate(url, detectSampleRate(url));
            if (ffmpegStream != null)
                return ffmpegStream;

            throw exception;
        }
    }

    public static void validate(String url) throws IOException {
        Radioplayer.LOGGER.info("Validating radio stream: {}", url);
        try (AudioStream _ = open(url)) {
            // Opening the stream and receiving an initial frame proves the URL is playable (via Lavaplayer or the optional FFmpeg fallback).
            Radioplayer.LOGGER.info("Radio stream validation succeeded: {}", url);
        } catch (IOException | RuntimeException exception) {
            Radioplayer.LOGGER.warn("Radio stream validation failed: {}", url, exception);
            logFfprobeDiagnostics(url);
            throw exception;
        }
    }

    private static AudioPlayerManager createPlayerManager(int sampleRate) {
        var manager = new DefaultAudioPlayerManager();
        manager.getConfiguration().setOutputFormat(new Pcm16AudioDataFormat(1, sampleRate, 960, false));
        manager.setFrameBufferDuration(1000);
        manager.registerSourceManager(new HttpAudioSourceManager(MediaContainerRegistry.DEFAULT_REGISTRY));
        return manager;
    }

    private static AudioTrack loadTrack(AudioPlayerManager playerManager, String url) throws IOException {
        AudioItem item;
        try {
            item = playerManager.loadItemSync(url);
        } catch (RuntimeException exception) {
            Radioplayer.LOGGER.warn("Lavaplayer failed while resolving radio stream: {}", url, exception);
            throw exception;
        }

        if (item instanceof AudioTrack track) {
            Radioplayer.LOGGER.info("Lavaplayer resolved radio stream as track: {} ({} ms)", track.getInfo().title,
                    track.getDuration());
            return track;
        }

        if (item instanceof AudioPlaylist playlist) {
            AudioTrack selectedTrack = playlist.getSelectedTrack();
            if (selectedTrack != null) {
                Radioplayer.LOGGER.info("Lavaplayer resolved playlist with {} tracks; using selected track: {}",
                        playlist.getTracks().size(), selectedTrack.getInfo().title);
                return selectedTrack;
            }

            List<AudioTrack> tracks = playlist.getTracks();
            if (!tracks.isEmpty()) {
                Radioplayer.LOGGER.info("Lavaplayer resolved playlist with {} tracks; using first track: {}", tracks.size(),
                        tracks.getFirst().getInfo().title);
                return tracks.getFirst();
            }

            Radioplayer.LOGGER.warn("Lavaplayer resolved an empty playlist for radio stream: {}", url);
        } else {
            Radioplayer.LOGGER.warn("Lavaplayer did not resolve a radio track for {} (result type: {})", url,
                    item == null ? "none" : item.getClass().getName());
        }

        throw new IOException("No playable audio stream found for URL: " + url);
    }

    private static int detectSampleRate(String url) {
        Path ffprobe = FfprobeNativeExtractor.getExecutablePath();
        if (ffprobe == null || !Files.isRegularFile(ffprobe)) {
            Radioplayer.LOGGER.warn("FFprobe is unavailable; using the default sample rate for {}", url);
            return DEFAULT_SAMPLE_RATE;
        }

        try {
            Process process = new ProcessBuilder(
                    ffprobe.toString(),
                    "-v", "error",
                    "-rw_timeout", "5000000",
                    "-select_streams", "a:0",
                    "-show_entries", "stream=sample_rate",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    url)
                    .redirectErrorStream(true)
                    .start();

            if (!process.waitFor(FFPROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("FFprobe timed out");
            }

            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0)
                throw new IOException("FFprobe exited with code " + process.exitValue() + ": " + output);

            int sampleRate = Integer.parseInt(output.lines().findFirst().orElse(""));
            if (sampleRate > 0) {
                Radioplayer.LOGGER.info("FFprobe detected {} Hz audio for {}", sampleRate, url);
                return sampleRate;
            }
        } catch (IOException | InterruptedException | NumberFormatException exception) {
            if (exception instanceof InterruptedException)
                Thread.currentThread().interrupt();

            Radioplayer.LOGGER.warn("Could not detect the sample rate with FFprobe for {}; using {} Hz", url,
                    DEFAULT_SAMPLE_RATE, exception);
        }

        return DEFAULT_SAMPLE_RATE;
    }

    private static void logFfprobeDiagnostics(String url) {
        Path ffprobe = FfprobeNativeExtractor.getExecutablePath();
        if (ffprobe == null || !Files.isRegularFile(ffprobe)) {
            Radioplayer.LOGGER.warn("Cannot collect FFprobe diagnostics; executable is unavailable");
            return;
        }

        try {
            Process process = new ProcessBuilder(
                    ffprobe.toString(),
                    "-v", "error",
                    "-rw_timeout", "5000000",
                    "-show_entries", "format=format_name,format_long_name:stream=codec_type,codec_name,profile,sample_rate",
                    "-of", "default=noprint_wrappers=1",
                    url)
                    .redirectErrorStream(true)
                    .start();

            if (!process.waitFor(FFPROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                Radioplayer.LOGGER.warn("FFprobe diagnostic timed out for {}", url);
                return;
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            Radioplayer.LOGGER.warn("FFprobe diagnostic for {} (exit {}): {}", url, process.exitValue(),
                    output.isEmpty() ? "no metadata returned" : output);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException)
                Thread.currentThread().interrupt();

            Radioplayer.LOGGER.warn("Could not collect FFprobe diagnostics for {}", url, exception);
        }
    }

    @Override
    public @NonNull AudioFormat getFormat() {
        return this.format;
    }

    @Override
    public @NonNull ByteBuffer read(int expectedSize) throws IOException {
        ByteBuffer output = BufferUtils.createByteBuffer(expectedSize);

        while (!this.closed && output.hasRemaining()) {
            if (this.pendingFrame != null) {
                copyPendingFrame(output);
                continue;
            }

            AudioFrame frame = provideFrame(FRAME_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (frame == null) {
                writeSilence(output);
                continue;
            }

            this.pendingFrame = frame.getData();
            this.pendingFrameOffset = 0;
        }

        output.flip();
        return output;
    }

    private AudioFrame provideFrame(long timeout, TimeUnit unit) throws IOException {
        try {
            return this.player.provide(timeout, unit);
        } catch (TimeoutException exception) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for audio data", exception);
        }
    }

    private static void writeSilence(ByteBuffer output) {
        while (output.hasRemaining()) {
            output.put((byte) 0);
        }
    }

    private static AudioFormat createAudioFormat(AudioDataFormat format) {
        return new AudioFormat(format.sampleRate, 16, format.channelCount, true, false);
    }

    private void copyPendingFrame(ByteBuffer output) {
        int bytesToCopy = Math.min(output.remaining(), this.pendingFrame.length - this.pendingFrameOffset);
        output.put(this.pendingFrame, this.pendingFrameOffset, bytesToCopy);
        this.pendingFrameOffset += bytesToCopy;

        if (this.pendingFrameOffset >= this.pendingFrame.length) {
            this.pendingFrame = null;
            this.pendingFrameOffset = 0;
        }
    }

    @Override
    public void close() {
        this.closed = true;
        this.player.destroy();
    }
}
