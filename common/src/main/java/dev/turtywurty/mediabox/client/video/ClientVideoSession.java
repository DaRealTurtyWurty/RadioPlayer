package dev.turtywurty.mediabox.client.video;

import dev.turtywurty.mediabox.MediaBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.OptionalDouble;
import java.util.UUID;

public final class ClientVideoSession implements AutoCloseable {
    private final UUID id;
    private final Identifier textureLocation;
    private final DynamicTexture texture;
    private final Path gameDirectory;
    private final String mediaLocation;
    private final int width;
    private final int height;
    private final boolean looping;

    private FfmpegVideoDecoder decoder;
    private double initialPositionSeconds;
    private long decoderStartedNanos;
    private double playbackRate = 1.0;

    private FfmpegVideoDecoder pendingDecoder;
    private double pendingInitialPositionSeconds;
    private long pendingDecoderStartedNanos;
    private double pendingPlaybackRate;
    private boolean pendingDiscontinuity;
    private long discontinuityRevision;

    private boolean receivedFrame;
    private long firstFrameNanos = -1L;

    private final TextureManager textureManager;

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
        this.textureLocation = MediaBox.id("dynamic/video/" + id);
        this.texture = new DynamicTexture(
                () -> "MediaBox video " + id,
                width,
                height,
                true
        );

        minecraft.getTextureManager().register(
                this.textureLocation,
                this.texture
        );

        this.decoderStartedNanos = System.nanoTime();
        this.decoder = FfmpegVideoDecoder.open(
                minecraft.gameDirectory.toPath(),
                mediaLocation,
                width,
                height,
                30,
                looping,
                startPositionSeconds,
                1.0
        );

        this.textureManager = minecraft.getTextureManager();
    }

    public UUID id() {
        return this.id;
    }

    public Identifier textureLocation() {
        return this.textureLocation;
    }

    public boolean isReady() {
        return this.texture != null && this.decoder != null && this.receivedFrame;
    }

    public OptionalDouble playbackPositionSeconds() {
        OptionalDouble outputTime = this.decoder.outputTimeSeconds();
        return outputTime.isPresent()
                ? OptionalDouble.of(this.initialPositionSeconds + outputTime.getAsDouble())
                : OptionalDouble.empty();
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

    public void beginResync(
            double startPositionSeconds,
            double playbackRate,
            boolean discontinuity
    ) throws IOException {
        if (this.pendingDecoder != null) {
            this.pendingDecoder.close();
            this.pendingDecoder = null;
        }

        long startedNanos = System.nanoTime();
        FfmpegVideoDecoder replacement = FfmpegVideoDecoder.open(
                this.gameDirectory,
                this.mediaLocation,
                this.width,
                this.height,
                30,
                this.looping,
                startPositionSeconds,
                playbackRate
        );
        this.pendingInitialPositionSeconds = startPositionSeconds;
        this.pendingDecoderStartedNanos = startedNanos;
        this.pendingPlaybackRate = playbackRate;
        this.pendingDiscontinuity = discontinuity;
        this.pendingDecoder = replacement;
    }

    public void uploadLatestFrame() {
        if (this.pendingDecoder != null) {
            byte[] pendingFrame = this.pendingDecoder.takeLatestFrame();
            if (pendingFrame != null) {
                FfmpegVideoDecoder previousDecoder = this.decoder;
                this.decoder = this.pendingDecoder;
                this.pendingDecoder = null;
                this.initialPositionSeconds = this.pendingInitialPositionSeconds;
                this.decoderStartedNanos = this.pendingDecoderStartedNanos;
                this.playbackRate = this.pendingPlaybackRate;
                if (this.pendingDiscontinuity) {
                    this.discontinuityRevision++;
                }
                this.pendingDiscontinuity = false;
                this.firstFrameNanos = System.nanoTime();

                uploadFrame(pendingFrame);
                previousDecoder.close();
                return;
            }
        }

        byte[] frame = this.decoder.takeLatestFrame();
        if (frame == null)
            return;

        uploadFrame(frame);
    }

    private void uploadFrame(byte[] frame) {
        ByteBuffer pixels = this.texture.getPixels().getPixelBytes();
        pixels.clear();
        pixels.put(frame);
        pixels.flip();

        this.texture.upload();
        if (this.firstFrameNanos < 0L) {
            this.firstFrameNanos = System.nanoTime();
        }
        this.receivedFrame = true;
    }

    @Override
    public void close() {
        this.decoder.close();
        if (this.pendingDecoder != null) {
            this.pendingDecoder.close();
            this.pendingDecoder = null;
        }
        this.textureManager.release(this.textureLocation);
    }
}
