package dev.turtywurty.mediaplayer.api;

import java.lang.reflect.InvocationTargetException;

public class MediaPlayerAPI {
    public static final String MOD_ID = "mediaplayer";

    private static final InternalMethods __internalMethods;

    static {
        try {
            __internalMethods = (InternalMethods) Class.forName("dev.turtywurty.mediaplayer.InternalMethodsImpl").getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
