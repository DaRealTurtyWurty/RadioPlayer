package dev.turtywurty.mediabox.client.video;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.ffmpeg.FfmpegNatives;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public final class FfmpegVideoDecoder implements AutoCloseable {
    private final Process process;
    private final Thread decoderThread;
    private final int frameBytes;

    private final AtomicReference<byte[]> latestFrame = new AtomicReference<>();

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
        this.decoderThread.start();
    }

    public static FfmpegVideoDecoder open(
            Path gameDirectory,
            String mediaLocation,
            int width,
            int height,
            int frameRate,
            boolean looping
    ) throws IOException {
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

        /*
         * Input options must appear before -i.
         */
        command.add("-re");

        if (looping) {
            command.add("-stream_loop");
            command.add("-1");
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

        Process process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();

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

    public byte @Nullable [] takeLatestFrame() {
        return this.latestFrame.getAndSet(null);
    }

    @Override
    public void close() {
        if (this.closed)
            return;

        this.closed = true;
        this.latestFrame.set(null);

        this.process.destroyForcibly();
        this.decoderThread.interrupt();
    }
}
