package dev.turtywurty.mediabox.client.render.screen;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;

import java.nio.ByteBuffer;

/** A directly-uploaded video plane; unlike DynamicTexture this is not RGBA-backed. */
final class VideoPlaneTexture extends AbstractTexture {
    private final int width;
    private final int height;

    VideoPlaneTexture(String label, int width, int height, GpuFormat format) {
        RenderSystem.assertOnRenderThread();
        this.width = width;
        this.height = height;

        GpuDevice device = RenderSystem.getDevice();
        this.texture = device.createTexture(
                label,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                format,
                width,
                height,
                1,
                1
        );
        this.textureView = device.createTextureView(this.texture);
        this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    void upload(CommandEncoder encoder, ByteBuffer pixels) {
        encoder.writeToTexture(this.texture, pixels, 0, 0, 0, 0, this.width, this.height);
    }
}
