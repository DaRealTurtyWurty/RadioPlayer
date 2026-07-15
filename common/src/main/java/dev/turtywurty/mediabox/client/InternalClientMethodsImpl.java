package dev.turtywurty.mediabox.client;

import dev.turtywurty.mediabox.SavedRadioStation;
import dev.turtywurty.mediabox.api.client.InternalClientMethods;
import dev.turtywurty.mediabox.client.gui.GlobeScreen;
import dev.turtywurty.mediabox.client.gui.RadioPlayerScreen;
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
