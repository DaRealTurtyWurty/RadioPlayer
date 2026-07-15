package dev.turtywurty.mediaplayer.fabric;

import dev.turtywurty.mediaplayer.MediaPlayer;
import net.blay09.mods.balm.Balm;
import net.blay09.mods.balm.fabric.platform.runtime.FabricLoadContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

public class FabricMediaPlayer implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricLoader.getInstance().getModContainer(MediaPlayer.MOD_ID).ifPresent(container ->
                ResourceLoader.registerBuiltinPack(
                        MediaPlayer.id("high_res_earth"),
                        container,
                        Component.literal("Media Player 8k Earth Textures"),
                        PackActivationType.NORMAL));

        Balm.initializeMod(MediaPlayer.MOD_ID, FabricLoadContext.INSTANCE, MediaPlayer::initialize);
    }
}
