package dev.turtywurty.radioplayer.api;

import java.lang.reflect.InvocationTargetException;

public class RadioplayerAPI {
    public static final String MOD_ID = "radioplayer";

    private static final InternalMethods __internalMethods;

    static {
        try {
            __internalMethods = (InternalMethods) Class.forName("dev.turtywurty.radioplayer.InternalMethodsImpl").getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
