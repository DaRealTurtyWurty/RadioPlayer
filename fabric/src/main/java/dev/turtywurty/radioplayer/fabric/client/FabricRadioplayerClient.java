package dev.turtywurty.radioplayer.fabric.client;

import dev.turtywurty.radioplayer.Radioplayer;
import dev.turtywurty.radioplayer.client.RadioplayerClient;
import net.blay09.mods.balm.client.BalmClient;
import net.blay09.mods.balm.fabric.platform.runtime.FabricLoadContext;
import net.fabricmc.api.ClientModInitializer;

public class FabricRadioplayerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BalmClient.initializeMod(Radioplayer.MOD_ID, FabricLoadContext.INSTANCE, RadioplayerClient::initialize);
    }
}
