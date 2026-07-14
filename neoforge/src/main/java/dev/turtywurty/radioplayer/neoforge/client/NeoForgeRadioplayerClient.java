package dev.turtywurty.radioplayer.neoforge.client;

import dev.turtywurty.radioplayer.Radioplayer;
import dev.turtywurty.radioplayer.block.ModBlockEntities;
import dev.turtywurty.radioplayer.client.RadioplayerClient;
import dev.turtywurty.radioplayer.client.render.blockentity.RadioPlayerBlockEntityRenderer;
import dev.turtywurty.radioplayer.client.render.blockentity.SpeakerBlockEntityRenderer;
import dev.turtywurty.radioplayer.client.render.globe.SpherePictureRenderState;
import dev.turtywurty.radioplayer.client.render.globe.SpherePictureRenderer;
import dev.turtywurty.radioplayer.client.render.globe.SphereRenderPipelines;
import net.blay09.mods.balm.client.BalmClient;
import net.blay09.mods.balm.neoforge.platform.runtime.NeoForgeLoadContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

@Mod(value = Radioplayer.MOD_ID, dist = Dist.CLIENT)
public class NeoForgeRadioplayerClient {
    public NeoForgeRadioplayerClient(ModContainer modContainer, IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeRadioplayerClient::registerPictureInPictureRenderers);
        modEventBus.addListener(NeoForgeRadioplayerClient::registerRenderPipelines);
        modEventBus.addListener(NeoForgeRadioplayerClient::registerRenderers);

        final var context = new NeoForgeLoadContext(modContainer, modEventBus);
        BalmClient.initializeMod(Radioplayer.MOD_ID, context, RadioplayerClient::initialize);
    }

    public static void registerPictureInPictureRenderers(RegisterPictureInPictureRenderersEvent event) {
        event.register(SpherePictureRenderState.class, SpherePictureRenderer::new);
    }

    public static void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(SphereRenderPipelines.EARTH_NORMAL_MAPPED);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.radioPlayer.value(), RadioPlayerBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.speaker.value(), SpeakerBlockEntityRenderer::new);
    }
}
