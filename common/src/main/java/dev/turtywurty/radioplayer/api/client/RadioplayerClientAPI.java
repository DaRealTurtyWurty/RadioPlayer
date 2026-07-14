package dev.turtywurty.radioplayer.api.client;

import dev.turtywurty.radioplayer.SavedRadioStation;
import net.minecraft.core.BlockPos;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class RadioplayerClientAPI {
    private static final InternalClientMethods __internalMethods;

    static {
        try {
            __internalMethods = (InternalClientMethods) Class.forName("dev.turtywurty.radioplayer.client.InternalClientMethodsImpl").getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static void openRadioPlayerScreen(BlockPos pos, String url, boolean playing, List<SavedRadioStation> savedStations) {
        __internalMethods.openRadioPlayerScreen(pos, url, playing, savedStations);
    }

    public static void openGlobeScreen() {
        __internalMethods.openGlobeScreen();
    }
}
