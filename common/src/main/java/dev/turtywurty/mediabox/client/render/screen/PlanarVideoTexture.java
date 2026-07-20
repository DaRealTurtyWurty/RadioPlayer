package dev.turtywurty.mediabox.client.render.screen;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.turtywurty.mediabox.MediaBox;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.util.UUID;

/** Two GPU textures containing one NV12 frame: full-size Y and half-size interleaved UV. */
public final class PlanarVideoTexture implements AutoCloseable {
    private final Identifier yLocation;
    private final Identifier uvLocation;
    private final VideoPlaneTexture yTexture;
    private final VideoPlaneTexture uvTexture;
    private final TextureManager textureManager;
    private final int yBytes;
    private final int uvBytes;

    public PlanarVideoTexture(TextureManager textureManager, UUID id, int width, int height) {
        if ((width & 1) != 0 || (height & 1) != 0)
            throw new IllegalArgumentException("NV12 video dimensions must be even");

        this.textureManager = textureManager;
        this.yLocation = MediaBox.id("dynamic/video/" + id + "/y");
        this.uvLocation = MediaBox.id("dynamic/video/" + id + "/uv");
        this.yTexture = new VideoPlaneTexture("MediaBox video Y " + id, width, height, GpuFormat.R8_UNORM);
        this.uvTexture = new VideoPlaneTexture("MediaBox video UV " + id, width / 2, height / 2, GpuFormat.RG8_UNORM);
        this.yBytes = Math.multiplyExact(width, height);
        this.uvBytes = this.yBytes / 2;

        textureManager.register(this.yLocation, this.yTexture);
        textureManager.register(this.uvLocation, this.uvTexture);
    }

    public Identifier yLocation() {
        return this.yLocation;
    }

    public Identifier uvLocation() {
        return this.uvLocation;
    }

    public void upload(ByteBuffer nv12) {
        if (nv12.remaining() != this.yBytes + this.uvBytes)
            throw new IllegalArgumentException("Unexpected NV12 frame size: " + nv12.remaining());

        ByteBuffer y = nv12.slice(nv12.position(), this.yBytes);
        ByteBuffer uv = nv12.slice(nv12.position() + this.yBytes, this.uvBytes);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        this.yTexture.upload(encoder, y);
        this.uvTexture.upload(encoder, uv);
    }

    @Override
    public void close() {
        this.textureManager.release(this.yLocation);
        this.textureManager.release(this.uvLocation);
    }
}
