package dev.turtywurty.mediaplayer.client;

import dev.turtywurty.mediaplayer.SavedRadioStation;
import dev.turtywurty.mediaplayer.api.client.InternalClientMethods;
import dev.turtywurty.mediaplayer.client.gui.GlobeScreen;
import dev.turtywurty.mediaplayer.client.gui.RadioPlayerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;

public class InternalClientMethodsImpl implements InternalClientMethods {
    @Override
    public void openRadioPlayerScreen(BlockPos pos, String url, boolean playing, List<SavedRadioStation> savedStations) {
        Minecraft.getInstance().gui.setScreen(new RadioPlayerScreen(pos, url, playing, savedStations));
    }

    @Override
    public void openGlobeScreen() {
        Minecraft.getInstance().gui.setScreen(new GlobeScreen());
    }
}
