package dev.turtywurty.mediabox.client.render.cable;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.mixin.RenderPipelinesAccessor;

public final class CableRenderPipelines {
    public static final RenderPipeline XRAY_LINES = RenderPipeline.builder(
                    RenderPipelinesAccessor.mediabox$getLinesSnippet())
            .withLocation(MediaBox.id("pipeline/cable_xray_lines"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    private CableRenderPipelines() {
    }
}
