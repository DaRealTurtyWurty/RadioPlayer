package dev.turtywurty.mediabox.video;

import com.mojang.serialization.Codec;

import java.util.Locale;

public enum PlaybackStatus {
    PLAYING,
    PAUSED,
    STOPPED;

    public static final Codec<PlaybackStatus> CODEC = Codec.STRING.xmap(
            value -> valueOf(value.toUpperCase(Locale.ROOT)),
            value -> value.name().toLowerCase(Locale.ROOT)
    );
}
