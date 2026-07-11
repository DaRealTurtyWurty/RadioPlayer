package dev.turtywurty.radioplayer.client.render.globe;

import dev.turtywurty.radioplayer.Radioplayer;
import dev.turtywurty.radioplayer.mixin.RenderTypeAccessor;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class SphereRenderTypes {
    private static final RenderType EARTH_NORMAL_MAPPED = RenderTypeAccessor.radioplayer$create(
            "radioplayer_earth_normal_mapped",
            RenderSetup.builder(SphereRenderPipelines.EARTH_NORMAL_MAPPED)
                    .withTexture("Sampler0", Radioplayer.id("textures/globe/earth.png"))
                    .withTexture("Sampler1", Radioplayer.id("textures/globe/earth_normal.png"))
                    .withTexture("Sampler2", Radioplayer.id("textures/globe/earth_specular.png"))
                    .createRenderSetup()
    );

    public static RenderType earthNormalMapped() {
        return EARTH_NORMAL_MAPPED;
    }

    private SphereRenderTypes() {
    }
}
