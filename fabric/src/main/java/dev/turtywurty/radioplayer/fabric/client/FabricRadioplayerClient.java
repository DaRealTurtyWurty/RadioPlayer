package dev.turtywurty.radioplayer.fabric.client;

import dev.turtywurty.radioplayer.Radioplayer;
import dev.turtywurty.radioplayer.client.RadioplayerClient;
import dev.turtywurty.radioplayer.client.render.globe.SpherePictureRenderer;
import dev.turtywurty.radioplayer.client.render.globe.SphereRenderPipelines;
import net.blay09.mods.balm.client.BalmClient;
import net.blay09.mods.balm.fabric.platform.runtime.FabricLoadContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.renderer.RenderPipelines;

public class FabricRadioplayerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RenderPipelines.register(SphereRenderPipelines.EARTH_NORMAL_MAPPED);
        PictureInPictureRendererRegistry.register(_ -> new SpherePictureRenderer());
        BalmClient.initializeMod(Radioplayer.MOD_ID, FabricLoadContext.INSTANCE, RadioplayerClient::initialize);
    }
}
