package dev.turtywurty.mediabox.neoforge.client;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.block.ModBlockEntities;
import dev.turtywurty.mediabox.client.MediaBoxClient;
import dev.turtywurty.mediabox.client.render.blockentity.RadioPlayerBlockEntityRenderer;
import dev.turtywurty.mediabox.client.render.blockentity.SpeakerBlockEntityRenderer;
import dev.turtywurty.mediabox.client.render.globe.SpherePictureRenderState;
import dev.turtywurty.mediabox.client.render.globe.SpherePictureRenderer;
import dev.turtywurty.mediabox.client.render.globe.SphereRenderPipelines;
import net.blay09.mods.balm.client.BalmClient;
import net.blay09.mods.balm.neoforge.platform.runtime.NeoForgeLoadContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

@Mod(value = MediaBox.MOD_ID, dist = Dist.CLIENT)
public class NeoForgeMediaBoxClient {
    public NeoForgeMediaBoxClient(ModContainer modContainer, IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeMediaBoxClient::registerPictureInPictureRenderers);
        modEventBus.addListener(NeoForgeMediaBoxClient::registerRenderPipelines);
        modEventBus.addListener(NeoForgeMediaBoxClient::registerRenderers);

        final var context = new NeoForgeLoadContext(modContainer, modEventBus);
        BalmClient.initializeMod(MediaBox.MOD_ID, context, MediaBoxClient::initialize);
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
