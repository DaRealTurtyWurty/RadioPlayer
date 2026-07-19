package dev.turtywurty.mediabox.client.video;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.ffmpeg.FfmpegNatives;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;

public final class FfmpegVideoProbe {
    private static final long TIMEOUT_SECONDS = 15L;

    private FfmpegVideoProbe() {
    }

    public static OptionalDouble durationSeconds(
            Path gameDirectory,
            String mediaLocation
    ) {
        Process process = null;
        try {
            Path ffprobe = FfmpegNatives.requireFfprobe(gameDirectory);
            process = new ProcessBuilder(
                    ffprobe.toString(),
                    "-v", "error",
                    "-rw_timeout", "10000000",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    mediaLocation
            ).redirectErrorStream(true).start();

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                MediaBox.LOGGER.warn("FFprobe timed out while reading video duration for {}", mediaLocation);
                return OptionalDouble.empty();
            }

            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();
            if (process.exitValue() != 0) {
                MediaBox.LOGGER.warn(
                        "FFprobe could not read video duration for {}: {}",
                        mediaLocation,
                        output
                );
                return OptionalDouble.empty();
            }

            double duration = Double.parseDouble(output.lines().findFirst().orElse(""));
            return Double.isFinite(duration) && duration > 0.0
                    ? OptionalDouble.of(duration)
                    : OptionalDouble.empty();
        } catch (IOException | NumberFormatException exception) {
            MediaBox.LOGGER.warn("Could not determine video duration for {}", mediaLocation, exception);
            return OptionalDouble.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return OptionalDouble.empty();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
