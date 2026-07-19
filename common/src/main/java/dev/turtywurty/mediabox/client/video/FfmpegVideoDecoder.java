package dev.turtywurty.mediabox.client.video;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.ffmpeg.FfmpegNatives;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class FfmpegVideoDecoder implements AutoCloseable {
    private final Process process;
    private final Thread decoderThread;
    private final Thread progressThread;
    private final int frameBytes;

    private final AtomicReference<byte[]> latestFrame = new AtomicReference<>();
    private final AtomicLong outputTimeMicros = new AtomicLong(-1L);

    private volatile boolean closed;

    public FfmpegVideoDecoder(
            Process process,
            int width,
            int height
    ) {
        this.process = process;
        this.frameBytes = Math.multiplyExact(
                Math.multiplyExact(width, height),
                4
        );
        this.decoderThread = new Thread(
                this::decodeLoop,
                "MediaBox Video Decoder"
        );
        this.decoderThread.setDaemon(true);
        this.progressThread = new Thread(
                this::progressLoop,
                "MediaBox Video Progress"
        );
        this.progressThread.setDaemon(true);
        this.decoderThread.start();
        this.progressThread.start();
    }

    public static FfmpegVideoDecoder open(
            Path gameDirectory,
            String mediaLocation,
            int width,
            int height,
            int frameRate,
            boolean looping,
            double startPositionSeconds,
            double playbackRate
    ) throws IOException {
        if (!Double.isFinite(playbackRate) || playbackRate <= 0.0)
            throw new IllegalArgumentException("Playback rate must be positive and finite");

        Path ffmpeg = FfmpegNatives.requireFfmpeg(gameDirectory);

        String videoFilter = String.format(
                Locale.ROOT,
                "scale=%d:%d:force_original_aspect_ratio=decrease,"
                        + "pad=%d:%d:(ow-iw)/2:(oh-ih)/2:black,"
                        + "fps=%d",
                width,
                height,
                width,
                height,
                frameRate
        );

        List<String> command = new ArrayList<>();
        command.add(ffmpeg.toString());
        command.add("-nostdin");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.add("-nostats");
        command.add("-stats_period");
        command.add("0.5");
        command.add("-progress");
        command.add("pipe:2");

        /*
         * Input options must appear before -i.
         */
        command.add("-readrate");
        command.add(String.format(Locale.ROOT, "%.5f", playbackRate));

        if (looping) {
            command.add("-stream_loop");
            command.add("-1");
        }

        if (startPositionSeconds > 0.0) {
            command.add("-ss");
            command.add(String.format(
                    Locale.ROOT,
                    "%.3f",
                    startPositionSeconds
            ));
        }

        command.add("-i");
        command.add(mediaLocation);

        command.addAll(List.of(
                "-map", "0:v:0",
                "-an",
                "-vf", videoFilter,
                "-pix_fmt", "rgba",
                "-f", "rawvideo",
                "pipe:1"
        ));

        Process process = new ProcessBuilder(command).start();

        MediaBox.LOGGER.info(
                "Started FFmpeg video decoder for {}",
                mediaLocation
        );

        return new FfmpegVideoDecoder(process, width, height);
    }

    private void decodeLoop() {
        try (InputStream rawVideo = this.process.getInputStream()) {
            while (!this.closed) {
                byte[] frame = new byte[this.frameBytes];

                int bytesRead = rawVideo.readNBytes(
                        frame,
                        0,
                        frame.length
                );

                if (bytesRead != frame.length) {
                    if (!this.closed) {
                        MediaBox.LOGGER.warn(
                                "FFmpeg stopped during a video frame: {}/{} bytes",
                                bytesRead,
                                frame.length
                        );
                    }

                    break;
                }

                this.latestFrame.set(frame);
            }
        } catch (IOException exception) {
            if (!this.closed) {
                MediaBox.LOGGER.error(
                        "Could not read FFmpeg video output",
                        exception
                );
            }
        } finally {
            this.process.destroy();
        }
    }

    private void progressLoop() {
        try (var reader = new BufferedReader(new InputStreamReader(
                this.process.getErrorStream(),
                StandardCharsets.UTF_8
        ))) {
            String line;
            while (!this.closed && (line = reader.readLine()) != null) {
                if (line.startsWith("out_time_us=")) {
                    String value = line.substring("out_time_us=".length());
                    try {
                        this.outputTimeMicros.set(Long.parseLong(value));
                    } catch (NumberFormatException ignored) {
                        // FFmpeg can report N/A before the first output timestamp.
                    }
                } else if (!line.isBlank() && !line.contains("=")) {
                    MediaBox.LOGGER.warn("FFmpeg video decoder: {}", line);
                }
            }
        } catch (IOException exception) {
            if (!this.closed) {
                MediaBox.LOGGER.error("Could not read FFmpeg video progress", exception);
            }
        }
    }

    public byte @Nullable [] takeLatestFrame() {
        return this.latestFrame.getAndSet(null);
    }

    public OptionalDouble outputTimeSeconds() {
        long micros = this.outputTimeMicros.get();
        return micros < 0L
                ? OptionalDouble.empty()
                : OptionalDouble.of(micros / 1_000_000.0);
    }

    @Override
    public void close() {
        if (this.closed)
            return;

        this.closed = true;
        this.latestFrame.set(null);

        this.process.destroyForcibly();
        this.decoderThread.interrupt();
        this.progressThread.interrupt();
    }
}
