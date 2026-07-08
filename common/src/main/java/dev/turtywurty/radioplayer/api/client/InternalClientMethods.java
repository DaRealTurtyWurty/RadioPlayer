package dev.turtywurty.radioplayer.api.client;

import dev.turtywurty.radioplayer.SavedRadioStation;
import net.minecraft.core.BlockPos;

import java.util.List;

public interface InternalClientMethods {
    void openRadioPlayerScreen(BlockPos pos, String url, boolean playing, List<SavedRadioStation> savedStations);
}
