package dev.turtywurty.mediabox.client.video;

import dev.turtywurty.mediabox.MediaBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

public final class ClientVideoSession implements AutoCloseable {
    private final UUID id;
    private final Identifier textureLocation;
    private final DynamicTexture texture;
    private final FfmpegVideoDecoder decoder;

    private boolean receivedFrame;

    private final TextureManager textureManager;

    public ClientVideoSession(
            Minecraft minecraft,
            UUID id,
            String mediaLocation,
            int width,
            int height
    ) throws IOException {
        this.id = id;
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

        this.decoder = FfmpegVideoDecoder.open(
                minecraft.gameDirectory.toPath(),
                mediaLocation,
                width,
                height,
                30,
                true
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

    public void uploadLatestFrame() {
        byte[] frame = this.decoder.takeLatestFrame();
        if (frame == null)
            return;

        ByteBuffer pixels = this.texture.getPixels().getPixelBytes();
        pixels.clear();
        pixels.put(frame);
        pixels.flip();

        this.texture.upload();
        this.receivedFrame = true;
    }

    @Override
    public void close() {
        this.decoder.close();
        this.textureManager.release(this.textureLocation);
    }
}
