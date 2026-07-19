package dev.turtywurty.mediabox.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Accessor("MATRICES_FOG_SNIPPET")
    static RenderPipeline.Snippet mediabox$getMatricesFogSnippet() {
        throw new AssertionError();
    }

    @Accessor("LINES_SNIPPET")
    static RenderPipeline.Snippet mediabox$getLinesSnippet() {
        throw new AssertionError();
    }
}
