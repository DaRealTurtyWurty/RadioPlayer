package dev.turtywurty.mediabox.client.render.globe;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.mixin.RenderTypeAccessor;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class SphereRenderTypes {
    private static final RenderType EARTH_NORMAL_MAPPED = RenderTypeAccessor.mediabox$create(
            MediaBox.MOD_ID + "_earth_normal_mapped",
            RenderSetup.builder(SphereRenderPipelines.EARTH_NORMAL_MAPPED)
                    .withTexture("Sampler0", MediaBox.id("textures/globe/earth.png"))
                    .withTexture("Sampler1", MediaBox.id("textures/globe/earth_normal.png"))
                    .withTexture("Sampler2", MediaBox.id("textures/globe/earth_specular.png"))
                    .createRenderSetup()
    );

    private SphereRenderTypes() {
    }

    public static RenderType earthNormalMapped() {
        return EARTH_NORMAL_MAPPED;
    }
}
