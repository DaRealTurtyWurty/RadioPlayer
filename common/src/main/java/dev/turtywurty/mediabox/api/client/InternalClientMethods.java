package dev.turtywurty.mediabox.api.client;

import dev.turtywurty.mediabox.SavedRadioStation;
import net.minecraft.core.BlockPos;

import java.util.List;

public interface InternalClientMethods {
    void openRadioPlayerScreen(BlockPos pos, String url, boolean playing, List<SavedRadioStation> savedStations);

    void openGlobeScreen();
}
