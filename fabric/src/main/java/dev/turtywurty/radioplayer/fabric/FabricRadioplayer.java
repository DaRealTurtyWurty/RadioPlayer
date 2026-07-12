package dev.turtywurty.radioplayer.fabric;

import dev.turtywurty.radioplayer.Radioplayer;
import net.blay09.mods.balm.Balm;
import net.blay09.mods.balm.fabric.platform.runtime.FabricLoadContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

public class FabricRadioplayer implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricLoader.getInstance().getModContainer(Radioplayer.MOD_ID).ifPresent(container ->
                ResourceLoader.registerBuiltinPack(
                        Radioplayer.id("high_res_earth"),
                        container,
                        Component.literal("RadioPlayer 8k Earth Textures"),
                        PackActivationType.NORMAL));

        Balm.initializeMod(Radioplayer.MOD_ID, FabricLoadContext.INSTANCE, Radioplayer::initialize);
    }
}
