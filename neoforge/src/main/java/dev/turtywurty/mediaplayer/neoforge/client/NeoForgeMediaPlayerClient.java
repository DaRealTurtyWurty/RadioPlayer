package dev.turtywurty.mediaplayer.neoforge.client;

import dev.turtywurty.mediaplayer.MediaPlayer;
import dev.turtywurty.mediaplayer.block.ModBlockEntities;
import dev.turtywurty.mediaplayer.client.MediaPlayerClient;
import dev.turtywurty.mediaplayer.client.render.blockentity.RadioPlayerBlockEntityRenderer;
import dev.turtywurty.mediaplayer.client.render.blockentity.SpeakerBlockEntityRenderer;
import dev.turtywurty.mediaplayer.client.render.globe.SpherePictureRenderState;
import dev.turtywurty.mediaplayer.client.render.globe.SpherePictureRenderer;
import dev.turtywurty.mediaplayer.client.render.globe.SphereRenderPipelines;
import net.blay09.mods.balm.client.BalmClient;
import net.blay09.mods.balm.neoforge.platform.runtime.NeoForgeLoadContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

@Mod(value = MediaPlayer.MOD_ID, dist = Dist.CLIENT)
public class NeoForgeMediaPlayerClient {
    public NeoForgeMediaPlayerClient(ModContainer modContainer, IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeMediaPlayerClient::registerPictureInPictureRenderers);
        modEventBus.addListener(NeoForgeMediaPlayerClient::registerRenderPipelines);
        modEventBus.addListener(NeoForgeMediaPlayerClient::registerRenderers);

        final var context = new NeoForgeLoadContext(modContainer, modEventBus);
        BalmClient.initializeMod(MediaPlayer.MOD_ID, context, MediaPlayerClient::initialize);
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
