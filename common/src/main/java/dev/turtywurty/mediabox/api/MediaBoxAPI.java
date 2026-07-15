package dev.turtywurty.mediabox.api;

import java.lang.reflect.InvocationTargetException;

public class MediaBoxAPI {
    public static final String MOD_ID = "mediabox";

    private static final InternalMethods __internalMethods;

    static {
        try {
            __internalMethods = (InternalMethods) Class.forName("dev.turtywurty.mediabox.InternalMethodsImpl").getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 ClassNotFoundException exception) {
            throw new RuntimeException(exception);
        }
    }
}
