package dev.turtywurty.radioplayer.sound;

import dev.turtywurty.radioplayer.Radioplayer;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.util.Util;
import org.jspecify.annotations.NonNull;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class FfmpegAudioStream implements AudioStream {
    private static final int INITIAL_PCM_BYTES = 3_840;
    private static final long STARTUP_TIMEOUT_SECONDS = 10;

    private final Process process;
    private final InputStream pcm;
    private final AudioFormat format;
    private byte[] initialPcm;
    private int initialPcmOffset;

    private FfmpegAudioStream(Process process, int sampleRate, String url) throws IOException {
        this.process = process;
        this.pcm = process.getInputStream();
        this.format = new AudioFormat(sampleRate, 16, 1, true, false);
        this.initialPcm = readInitialPcm(url);
    }

    public static AudioStream tryCreate(String url, int sampleRate) throws IOException {
        String configuredPath = Radioplayer.config().ffmpegExecutablePath;
        if (configuredPath == null || configuredPath.isBlank()) {
            Radioplayer.LOGGER.warn("Lavaplayer cannot play {}; no FFmpeg executable is configured", url);
            return null;
        }

        Path executable;
        try {
            executable = Path.of(configuredPath.trim()).toAbsolutePath();
        } catch (InvalidPathException exception) {
            Radioplayer.LOGGER.warn("Configured FFmpeg path is invalid: {}", configuredPath, exception);
            return null;
        }

        if (!Files.isRegularFile(executable)) {
            Radioplayer.LOGGER.warn("Configured FFmpeg executable does not exist: {}", executable);
            return null;
        }

        Radioplayer.LOGGER.info("Lavaplayer cannot play {}; trying configured FFmpeg at {}", url, executable);
        Process process = new ProcessBuilder(
                executable.toString(),
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-rw_timeout", "5000000",
                "-i", url,
                "-map", "0:a:0",
                "-vn",
                "-ac", "1",
                "-ar", Integer.toString(sampleRate),
                "-f", "s16le",
                "pipe:1")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();

        try {
            var stream = new FfmpegAudioStream(process, sampleRate, url);
            Radioplayer.LOGGER.info("FFmpeg fallback is decoding radio stream: {}", url);
            return stream;
        } catch (IOException exception) {
            process.destroyForcibly();
            throw exception;
        }
    }

    private byte[] readInitialPcm(String url) throws IOException {
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

            throw new IOException("FFmpeg exited before producing audio for " + url);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for FFmpeg audio", exception);
        } catch (TimeoutException exception) {
            throw new IOException("FFmpeg timed out before producing audio for " + url, exception);
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
            Radioplayer.LOGGER.debug("Could not close FFmpeg audio stream", exception);
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
