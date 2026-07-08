package dev.turtywurty.radioplayer.neoforge.client;

import dev.turtywurty.radioplayer.Radioplayer;
import dev.turtywurty.radioplayer.client.RadioplayerClient;
import net.blay09.mods.balm.client.BalmClient;
import net.blay09.mods.balm.neoforge.platform.runtime.NeoForgeLoadContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = Radioplayer.MOD_ID, dist = Dist.CLIENT)
public class NeoForgeRadioplayerClient {
    public NeoForgeRadioplayerClient(ModContainer modContainer, IEventBus modEventBus) {
        final var context = new NeoForgeLoadContext(modContainer, modEventBus);
        BalmClient.initializeMod(Radioplayer.MOD_ID, context, RadioplayerClient::initialize);
    }
}
