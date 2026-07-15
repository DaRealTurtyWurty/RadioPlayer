package dev.turtywurty.mediabox.fabric;

import dev.turtywurty.mediabox.MediaBox;
import net.blay09.mods.balm.Balm;
import net.blay09.mods.balm.fabric.platform.runtime.FabricLoadContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

public class FabricMediaBox implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricLoader.getInstance().getModContainer(MediaBox.MOD_ID).ifPresent(container ->
                ResourceLoader.registerBuiltinPack(
                        MediaBox.id("high_res_earth"),
                        container,
                        Component.literal("Media Box 8k Earth Textures"),
                        PackActivationType.NORMAL));

        Balm.initializeMod(MediaBox.MOD_ID, FabricLoadContext.INSTANCE, MediaBox::initialize);
    }
}
