package dev.turtywurty.mediabox.client.render.globe;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.mixin.RenderPipelinesAccessor;
import net.minecraft.client.renderer.BindGroupLayouts;

public final class SphereRenderPipelines {
    public static final RenderPipeline EARTH_NORMAL_MAPPED = RenderPipeline.builder(RenderPipelinesAccessor.mediabox$getMatricesFogSnippet())
            .withLocation(MediaBox.id("pipeline/earth_normal_mapped"))
            .withVertexShader(MediaBox.id("core/sphere_earth"))
            .withFragmentShader(MediaBox.id("core/sphere_earth"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    private SphereRenderPipelines() {
    }
}
