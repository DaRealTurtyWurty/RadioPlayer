package dev.turtywurty.mediabox.client.render.screen;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.mixin.RenderPipelinesAccessor;
import net.minecraft.client.renderer.BindGroupLayouts;

public final class VideoRenderPipelines {
    public static final RenderPipeline NV12_SCREEN = RenderPipeline.builder(
                    RenderPipelinesAccessor.mediabox$getMatricesFogSnippet())
            .withLocation(MediaBox.id("pipeline/nv12_screen"))
            .withVertexShader(MediaBox.id("core/video_nv12"))
            .withFragmentShader(MediaBox.id("core/video_nv12"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    private VideoRenderPipelines() {
    }
}
