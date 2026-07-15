package dev.turtywurty.mediaplayer.client.render.globe;

import dev.turtywurty.mediaplayer.MediaPlayer;
import dev.turtywurty.mediaplayer.mixin.RenderTypeAccessor;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class SphereRenderTypes {
    private static final RenderType EARTH_NORMAL_MAPPED = RenderTypeAccessor.mediaplayer$create(
            "mediaplayer_earth_normal_mapped",
            RenderSetup.builder(SphereRenderPipelines.EARTH_NORMAL_MAPPED)
                    .withTexture("Sampler0", MediaPlayer.id("textures/globe/earth.png"))
                    .withTexture("Sampler1", MediaPlayer.id("textures/globe/earth_normal.png"))
                    .withTexture("Sampler2", MediaPlayer.id("textures/globe/earth_specular.png"))
                    .createRenderSetup()
    );

    private SphereRenderTypes() {
    }

    public static RenderType earthNormalMapped() {
        return EARTH_NORMAL_MAPPED;
    }
}
