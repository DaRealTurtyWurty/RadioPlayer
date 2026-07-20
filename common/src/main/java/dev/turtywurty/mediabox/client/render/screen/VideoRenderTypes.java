package dev.turtywurty.mediabox.client.render.screen;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.mixin.RenderTypeAccessor;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class VideoRenderTypes {
    private VideoRenderTypes() {
    }

    public static RenderType nv12(Identifier yTexture, Identifier uvTexture) {
        return RenderTypeAccessor.mediabox$create(
                MediaBox.MOD_ID + "_nv12_video_" + yTexture.toDebugFileName(),
                RenderSetup.builder(VideoRenderPipelines.NV12_SCREEN)
                        .withTexture("Sampler0", yTexture)
                        .withTexture("Sampler1", uvTexture)
                        .createRenderSetup()
        );
    }
}
