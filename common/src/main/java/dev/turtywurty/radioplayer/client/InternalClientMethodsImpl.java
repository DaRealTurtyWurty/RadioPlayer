package dev.turtywurty.radioplayer.client;

import dev.turtywurty.radioplayer.SavedRadioStation;
import dev.turtywurty.radioplayer.api.client.InternalClientMethods;
import dev.turtywurty.radioplayer.client.gui.RadioPlayerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;

public class InternalClientMethodsImpl implements InternalClientMethods {
    @Override
    public void openRadioPlayerScreen(BlockPos pos, String url, boolean playing, List<SavedRadioStation> savedStations) {
        Minecraft.getInstance().gui.setScreen(new RadioPlayerScreen(pos, url, playing, savedStations));
    }
}
