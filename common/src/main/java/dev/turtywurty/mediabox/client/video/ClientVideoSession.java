package dev.turtywurty.mediabox.client.video;

import dev.turtywurty.mediabox.client.render.screen.PlanarVideoTexture;
import dev.turtywurty.mediabox.client.render.screen.VideoRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.OptionalDouble;
import java.util.UUID;

/** Owns one bounded video decoder, GPU surface, and audio-disciplined presentation clock. */
public final class ClientVideoSession implements AutoCloseable {
    private final UUID id;
    private final PlanarVideoTexture texture;
    private final RenderType renderType;
    private final Path gameDirectory;
    private final String mediaLocation;
    private final int width;
    private final int height;
    private final boolean looping;

    private FfmpegVideoDecoder decoder;
    private double initialPositionSeconds;
    private long decoderStartedNanos;
    private volatile ClockAnchor clockAnchor;
    private volatile double playbackRate = 1.0;

    private FfmpegVideoDecoder pendingDecoder;
    private double pendingInitialPositionSeconds;
    private long pendingDecoderStartedNanos;
    private double pendingPlaybackRate;
    private boolean pendingDiscontinuity;
    private long discontinuityRevision;

    private boolean decoderReady;
    private long firstFrameNanos = -1L;

    public ClientVideoSession(
            Minecraft minecraft,
            UUID id,
            String mediaLocation,
            int width,
            int height,
            boolean looping,
            double startPositionSeconds
    ) throws IOException {
        this.id = id;
        this.gameDirectory = minecraft.gameDirectory.toPath();
        this.mediaLocation = mediaLocation;
        this.width = width;
        this.height = height;
        this.looping = looping;
        this.initialPositionSeconds = startPositionSeconds;
        this.clockAnchor = new ClockAnchor(startPositionSeconds, System.nanoTime());
        this.texture = new PlanarVideoTexture(minecraft.getTextureManager(), id, width, height);
        this.renderType = VideoRenderTypes.nv12(this.texture.yLocation(), this.texture.uvLocation());

        this.decoderStartedNanos = System.nanoTime();
        this.decoder = openDecoder(startPositionSeconds, 1.0);
    }

    public UUID id() {
        return this.id;
    }

    public RenderType renderType() {
        return this.renderType;
    }

    public boolean isReady() {
        return this.decoderReady;
    }

    public OptionalDouble playbackPositionSeconds() {
        ClockAnchor anchor = this.clockAnchor;
        double elapsed = (System.nanoTime() - anchor.nanoTime()) / 1_000_000_000.0;
        return OptionalDouble.of(anchor.positionSeconds() + elapsed * this.playbackRate);
    }

    public OptionalDouble startupDelaySeconds() {
        if (this.firstFrameNanos < 0L)
            return OptionalDouble.empty();
        return OptionalDouble.of((this.firstFrameNanos - this.decoderStartedNanos) / 1_000_000_000.0);
    }

    public double playbackRate() {
        return this.playbackRate;
    }

    public String mediaLocation() {
        return this.mediaLocation;
    }

    public long discontinuityRevision() {
        return this.discontinuityRevision;
    }

    public boolean isResynchronizing() {
        return this.pendingDecoder != null;
    }

    public boolean isPlaybackStarted() {
        return true;
    }

    public void beginResync(
            double startPositionSeconds,
            double playbackRate,
            boolean discontinuity
    ) throws IOException {
        if (this.pendingDecoder != null)
            this.pendingDecoder.close();

        this.pendingInitialPositionSeconds = startPositionSeconds;
        this.pendingDecoderStartedNanos = System.nanoTime();
        this.pendingPlaybackRate = playbackRate;
        this.pendingDiscontinuity = discontinuity;
        this.pendingDecoder = openDecoder(startPositionSeconds, playbackRate);
    }

    /** Re-anchors video to the sample position reported by the actual OpenAL source. */
    public void updateAudioPlaybackPosition(double positionSeconds, long revision) {
        if (!Double.isFinite(positionSeconds) || positionSeconds < 0.0
                || revision != this.discontinuityRevision)
            return;
        this.clockAnchor = new ClockAnchor(positionSeconds, System.nanoTime());
    }

    public void setPlaybackRate(double playbackRate) {
        if (!Double.isFinite(playbackRate) || playbackRate <= 0.0)
            return;
        double position = playbackPositionSeconds().orElse(this.clockAnchor.positionSeconds());
        this.clockAnchor = new ClockAnchor(position, System.nanoTime());
        this.playbackRate = playbackRate;
    }

    public void uploadLatestFrame() {
        if (this.pendingDecoder != null) {
            ByteBuffer pendingFrame = this.pendingDecoder.takeFrameForPlayback(0.0);
            if (pendingFrame != null) {
                FfmpegVideoDecoder previousDecoder = this.decoder;
                this.decoder = this.pendingDecoder;
                this.pendingDecoder = null;
                finishDecoderSwap();
                try {
                    uploadFrame(pendingFrame);
                } finally {
                    this.decoder.recycleFrame(pendingFrame);
                }
                previousDecoder.close();
                return;
            }
        }

        double relativePosition = playbackPositionSeconds().orElse(this.initialPositionSeconds)
                - this.initialPositionSeconds;
        ByteBuffer frame = this.decoder.takeFrameForPlayback(relativePosition);
        if (frame == null)
            return;
        try {
            uploadFrame(frame);
        } finally {
            this.decoder.recycleFrame(frame);
        }
    }

    private FfmpegVideoDecoder openDecoder(double startPositionSeconds, double rate) throws IOException {
        return FfmpegVideoDecoder.open(
                this.gameDirectory,
                this.mediaLocation,
                this.width,
                this.height,
                30,
                this.looping,
                startPositionSeconds,
                rate
        );
    }

    private void finishDecoderSwap() {
        this.initialPositionSeconds = this.pendingInitialPositionSeconds;
        this.clockAnchor = new ClockAnchor(this.pendingInitialPositionSeconds, System.nanoTime());
        this.decoderStartedNanos = this.pendingDecoderStartedNanos;
        this.playbackRate = this.pendingPlaybackRate;
        if (this.pendingDiscontinuity)
            this.discontinuityRevision++;
        this.pendingDiscontinuity = false;
        this.firstFrameNanos = System.nanoTime();
    }

    private void uploadFrame(ByteBuffer frame) {
        if (this.firstFrameNanos < 0L)
            this.firstFrameNanos = System.nanoTime();
        this.decoderReady = true;
        this.texture.upload(frame);
    }

    @Override
    public void close() {
        this.decoder.close();
        if (this.pendingDecoder != null) {
            this.pendingDecoder.close();
            this.pendingDecoder = null;
        }
        this.texture.close();
    }

    private record ClockAnchor(double positionSeconds, long nanoTime) {
    }
}
