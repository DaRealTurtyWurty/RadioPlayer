package dev.turtywurty.mediabox.api.client;

import dev.turtywurty.mediabox.SavedRadioStation;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

public interface InternalClientMethods {
    void openRadioPlayerScreen(BlockPos pos, String url, boolean playing, List<SavedRadioStation> savedStations);

    void openGlobeScreen();

    void openFlatScreenSettingsScreen(BlockPos pos, UUID screenId);
}
