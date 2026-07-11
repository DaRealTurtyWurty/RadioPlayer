package dev.turtywurty.radioplayer.client.render.globe;

import dev.turtywurty.radioplayer.Radioplayer;
import dev.turtywurty.radioplayer.mixin.RenderTypeAccessor;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.BiFunction;

public final class SphereRenderTypes {
    private static final BiFunction<Identifier, Identifier, RenderType> EARTH_NORMAL_MAPPED = Util.memoize(
            (colorTexture, normalTexture) -> RenderTypeAccessor.radioplayer$create(
                    "radioplayer_earth_normal_mapped",
                    RenderSetup.builder(SphereRenderPipelines.EARTH_NORMAL_MAPPED)
                            .withTexture("Sampler0", colorTexture)
                            .withTexture("Sampler1", normalTexture)
                            .createRenderSetup()
            )
    );

    public static RenderType earthNormalMapped() {
        return EARTH_NORMAL_MAPPED.apply(
                Radioplayer.id("textures/globe/earth.png"),
                Radioplayer.id("textures/globe/earth_normal.png")
        );
    }

    private SphereRenderTypes() {
    }
}
