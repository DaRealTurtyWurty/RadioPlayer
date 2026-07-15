package dev.turtywurty.mediaplayer.api.client;

import dev.turtywurty.mediaplayer.SavedRadioStation;
import net.minecraft.core.BlockPos;

import java.util.List;

public interface InternalClientMethods {
    void openRadioPlayerScreen(BlockPos pos, String url, boolean playing, List<SavedRadioStation> savedStations);

    void openGlobeScreen();
}
