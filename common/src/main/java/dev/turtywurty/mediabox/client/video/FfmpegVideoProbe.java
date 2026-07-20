package dev.turtywurty.mediabox.client.video;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.client.ytdlp.YtDlpVideoResolver;
import dev.turtywurty.mediabox.ffmpeg.FfmpegNatives;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
        return probe(gameDirectory, mediaLocation).durationSeconds();
    }

    public static ProbeResult probe(
            Path gameDirectory,
            String mediaLocation
    ) {
        Process process = null;
        try {
            Path ffprobe = FfmpegNatives.requireFfprobe(gameDirectory);
            List<String> command = new ArrayList<>(List.of(
                    ffprobe.toString(),
                    "-v", "error",
                    "-rw_timeout", "10000000",
                    "-show_entries", "stream=index,codec_type:format=duration",
                    "-of", "default=noprint_wrappers=1"
            ));
            command.addAll(YtDlpVideoResolver.inputOptions(mediaLocation));
            command.add(mediaLocation);
            process = new ProcessBuilder(command).redirectErrorStream(true).start();

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                MediaBox.LOGGER.warn("FFprobe timed out while checking a video media input");
                return ProbeResult.NOT_PLAYABLE;
            }

            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();
            if (process.exitValue() != 0) {
                MediaBox.LOGGER.warn(
                        "FFprobe could not open a video media input"
                );
                return ProbeResult.NOT_PLAYABLE;
            }

            boolean hasVideo = output.lines().anyMatch("codec_type=video"::equals);
            if (!hasVideo)
                return ProbeResult.NOT_PLAYABLE;
            boolean hasAudio = output.lines().anyMatch("codec_type=audio"::equals);

            OptionalDouble duration = output.lines()
                    .filter(line -> line.startsWith("duration="))
                    .map(line -> line.substring("duration=".length()))
                    .mapToDouble(FfmpegVideoProbe::parseDuration)
                    .filter(value -> Double.isFinite(value) && value > 0.0)
                    .findFirst();
            return new ProbeResult(true, hasAudio, duration);
        } catch (IOException exception) {
            MediaBox.LOGGER.warn("Could not probe a video media input", exception);
            return ProbeResult.NOT_PLAYABLE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ProbeResult.NOT_PLAYABLE;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static double parseDuration(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    public record ProbeResult(boolean playable, boolean hasAudio, OptionalDouble durationSeconds) {
        private static final ProbeResult NOT_PLAYABLE = new ProbeResult(false, false, OptionalDouble.empty());
    }
}
