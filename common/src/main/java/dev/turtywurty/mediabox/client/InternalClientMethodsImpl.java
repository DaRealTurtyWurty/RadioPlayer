package dev.turtywurty.mediabox.client;

import dev.turtywurty.mediabox.SavedRadioStation;
import dev.turtywurty.mediabox.api.client.InternalClientMethods;
import dev.turtywurty.mediabox.client.gui.FlatScreenSettingsScreen;
import dev.turtywurty.mediabox.client.gui.GlobeScreen;
import dev.turtywurty.mediabox.client.gui.RadioPlayerScreen;
import dev.turtywurty.mediabox.client.video.ClientScreenPlaybackState;
import dev.turtywurty.mediabox.video.VideoSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

public class InternalClientMethodsImpl implements InternalClientMethods {
    @Override
    public void openRadioPlayerScreen(BlockPos pos, String url, boolean playing, List<SavedRadioStation> savedStations) {
        Minecraft.getInstance().gui.setScreen(new RadioPlayerScreen(pos, url, playing, savedStations));
    }

    @Override
    public void openGlobeScreen() {
        Minecraft.getInstance().gui.setScreen(new GlobeScreen());
    }

    @Override
    public void openFlatScreenSettingsScreen(BlockPos pos, UUID screenId) {
        String currentUrl = ClientScreenPlaybackState.get(screenId)
                .map(state -> state.source() instanceof VideoSource.RemoteUrl remote ? remote.url() : "")
                .orElse("");

        Minecraft.getInstance().gui.setScreen(new FlatScreenSettingsScreen(pos, currentUrl));
    }
}
