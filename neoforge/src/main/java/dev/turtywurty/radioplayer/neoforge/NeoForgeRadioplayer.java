package dev.turtywurty.radioplayer.neoforge;

import dev.turtywurty.radioplayer.Radioplayer;
import net.blay09.mods.balm.Balm;
import net.blay09.mods.balm.neoforge.platform.runtime.NeoForgeLoadContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Radioplayer.MOD_ID)
public class NeoForgeRadioplayer {
    public NeoForgeRadioplayer(ModContainer modContainer, IEventBus modEventBus) {
        final var context = new NeoForgeLoadContext(modContainer, modEventBus);
        Balm.initializeMod(Radioplayer.MOD_ID, context, Radioplayer::initialize);
    }
}
