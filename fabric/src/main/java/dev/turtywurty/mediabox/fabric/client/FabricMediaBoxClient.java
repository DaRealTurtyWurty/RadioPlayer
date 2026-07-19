package dev.turtywurty.mediabox.fabric.client;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.block.ModBlockEntities;
import dev.turtywurty.mediabox.client.MediaBoxClient;
import dev.turtywurty.mediabox.client.render.blockentity.RadioPlayerBlockEntityRenderer;
import dev.turtywurty.mediabox.client.render.blockentity.SpeakerBlockEntityRenderer;
import dev.turtywurty.mediabox.client.render.globe.SpherePictureRenderer;
import dev.turtywurty.mediabox.client.render.globe.SphereRenderPipelines;
import dev.turtywurty.mediabox.client.render.cable.CableRenderPipelines;
import dev.turtywurty.mediabox.client.render.cable.CableWorldRenderer;
import net.blay09.mods.balm.client.BalmClient;
import net.blay09.mods.balm.fabric.platform.runtime.FabricLoadContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public class FabricMediaBoxClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RenderPipelines.register(SphereRenderPipelines.EARTH_NORMAL_MAPPED);
        RenderPipelines.register(CableRenderPipelines.XRAY_LINES);
        LevelRenderEvents.COLLECT_SUBMITS.register(context ->
                CableWorldRenderer.submit(context.poseStack(), context.submitNodeCollector()));
        PictureInPictureRendererRegistry.register(_ -> new SpherePictureRenderer());
        BlockEntityRenderers.register(ModBlockEntities.radioPlayer.value(), RadioPlayerBlockEntityRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.speaker.value(), SpeakerBlockEntityRenderer::new);
        BalmClient.initializeMod(MediaBox.MOD_ID, FabricLoadContext.INSTANCE, MediaBoxClient::initialize);
    }
}
