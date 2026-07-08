package dev.turtywurty.radioplayer.fabric;

import dev.turtywurty.radioplayer.Radioplayer;
import net.blay09.mods.balm.Balm;
import net.blay09.mods.balm.fabric.platform.runtime.FabricLoadContext;
import net.fabricmc.api.ModInitializer;

public class FabricRadioplayer implements ModInitializer {
    @Override
    public void onInitialize() {
        Balm.initializeMod(Radioplayer.MOD_ID, FabricLoadContext.INSTANCE, Radioplayer::initialize);
    }
}
