package dev.turtywurty.mediabox.api.client;

import dev.turtywurty.mediabox.SavedRadioStation;
import net.minecraft.core.BlockPos;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class MediaBoxClientAPI {
    private static final InternalClientMethods __internalMethods;

    static {
        try {
            __internalMethods = (InternalClientMethods) Class.forName("dev.turtywurty.mediabox.client.InternalClientMethodsImpl").getConstructor().newInstance();
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
