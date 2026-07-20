package dev.turtywurty.mediabox.client.video;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.client.ytdlp.YtDlpVideoResolver;
import dev.turtywurty.mediabox.ffmpeg.FfmpegNatives;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Video-only FFmpeg transport. Frames are NV12 direct buffers kept in a tiny
 * newest-frame queue; steady playback performs no per-frame allocation.
 */
public final class FfmpegVideoDecoder implements AutoCloseable {
    private static final int MAX_QUEUED_FRAMES = 3;
    private static final int READ_CHUNK_BYTES = 64 * 1024;

    private final Process process;
    private final Thread decoderThread;
    private final InputStream rawVideo;
    private final int frameBytes;
    private final int frameRate;
    private final byte[] readBuffer = new byte[READ_CHUNK_BYTES];
    private final Deque<VideoFrame> frames = new ArrayDeque<>();
    private final Deque<ByteBuffer> availableBuffers = new ArrayDeque<>();
    private final Object frameLock = new Object();

    private long decodedFrames;
    private volatile boolean ready;
    private volatile boolean closed;

    private FfmpegVideoDecoder(Process process, int width, int height, int frameRate) {
        this.process = process;
        this.rawVideo = process.getInputStream();
        this.frameBytes = Math.multiplyExact(Math.multiplyExact(width, height), 3) / 2;
        this.frameRate = frameRate;
        this.decoderThread = new Thread(this::decodeLoop, "MediaBox Video Decoder");
        this.decoderThread.setDaemon(true);
        this.decoderThread.start();
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
        if ((width & 1) != 0 || (height & 1) != 0)
            throw new IllegalArgumentException("NV12 video dimensions must be even");
        if (!Double.isFinite(playbackRate) || playbackRate <= 0.0)
            throw new IllegalArgumentException("Playback rate must be positive and finite");

        Path ffmpeg = FfmpegNatives.requireFfmpeg(gameDirectory);
        String videoFilter = String.format(
                Locale.ROOT,
                "scale=%d:%d:force_original_aspect_ratio=decrease,"
                        + "pad=%d:%d:(ow-iw)/2:(oh-ih)/2:black,fps=%d,format=nv12",
                width, height, width, height, frameRate
        );

        List<String> command = new ArrayList<>();
        command.addAll(List.of(
                ffmpeg.toString(), "-nostdin", "-hide_banner", "-loglevel", "error", "-nostats",
                "-readrate", String.format(Locale.ROOT, "%.5f", playbackRate)
        ));
        if (looping) {
            command.add("-stream_loop");
            command.add("-1");
        }
        if (startPositionSeconds > 0.0) {
            command.add("-ss");
            command.add(String.format(Locale.ROOT, "%.3f", startPositionSeconds));
        }
        command.addAll(YtDlpVideoResolver.inputOptions(mediaLocation));
        command.add("-i");
        command.add(mediaLocation);
        command.addAll(List.of(
                "-map", "0:v:0", "-an", "-vf", videoFilter,
                "-pix_fmt", "nv12", "-f", "rawvideo", "pipe:1"
        ));

        Process process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        MediaBox.LOGGER.info("Started pooled NV12 FFmpeg video decoder for {}", mediaLocation);
        return new FfmpegVideoDecoder(process, width, height, frameRate);
    }

    public boolean hasFrame() {
        return this.ready;
    }

    public @Nullable ByteBuffer takeFrameForPlayback(double relativePositionSeconds) {
        long targetFrame = Math.max(0L, (long) Math.floor(relativePositionSeconds * this.frameRate));
        synchronized (this.frameLock) {
            VideoFrame selected = null;
            while (!this.frames.isEmpty() && this.frames.peekFirst().index() <= targetFrame) {
                if (selected != null)
                    recycleBuffer(selected.pixels());
                selected = this.frames.removeFirst();
            }
            return selected == null ? null : selected.pixels();
        }
    }

    public void recycleFrame(@Nullable ByteBuffer pixels) {
        if (pixels == null || this.closed || pixels.capacity() != this.frameBytes)
            return;
        synchronized (this.frameLock) {
            if (!this.closed)
                recycleBuffer(pixels);
        }
    }

    private void decodeLoop() {
        try (this.rawVideo) {
            while (!this.closed) {
                ByteBuffer pixels = acquireBuffer();
                if (!readFrame(pixels)) {
                    synchronized (this.frameLock) {
                        recycleBuffer(pixels);
                    }
                    break;
                }

                synchronized (this.frameLock) {
                    if (this.closed)
                        break;
                    if (this.frames.size() >= MAX_QUEUED_FRAMES)
                        recycleBuffer(this.frames.removeFirst().pixels());
                    this.frames.addLast(new VideoFrame(this.decodedFrames++, pixels));
                    this.ready = true;
                }
            }
        } catch (IOException exception) {
            if (!this.closed)
                MediaBox.LOGGER.error("Could not read FFmpeg NV12 video output", exception);
        } finally {
            this.process.destroy();
        }
    }

    private ByteBuffer acquireBuffer() {
        synchronized (this.frameLock) {
            ByteBuffer buffer = this.availableBuffers.pollFirst();
            if (buffer != null)
                return buffer.clear();
            if (this.frames.size() >= MAX_QUEUED_FRAMES)
                return this.frames.removeFirst().pixels().clear();
        }
        return ByteBuffer.allocateDirect(this.frameBytes);
    }

    private boolean readFrame(ByteBuffer destination) throws IOException {
        destination.clear();
        while (destination.hasRemaining()) {
            int read = this.rawVideo.read(this.readBuffer, 0, Math.min(this.readBuffer.length, destination.remaining()));
            if (read == -1)
                return false;
            destination.put(this.readBuffer, 0, read);
        }
        destination.flip();
        return true;
    }

    private void recycleBuffer(ByteBuffer pixels) {
        pixels.clear();
        this.availableBuffers.addLast(pixels);
    }

    @Override
    public void close() {
        if (this.closed)
            return;
        this.closed = true;
        synchronized (this.frameLock) {
            this.frames.clear();
            this.availableBuffers.clear();
        }
        try {
            this.rawVideo.close();
        } catch (IOException ignored) {
        }
        this.process.destroyForcibly();
        this.decoderThread.interrupt();
    }

    private record VideoFrame(long index, ByteBuffer pixels) {
    }
}
