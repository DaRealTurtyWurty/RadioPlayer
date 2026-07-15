package dev.turtywurty.mediabox.cable;

import com.mojang.serialization.Codec;

import java.util.Locale;

public enum MediaSignalType {
    AUDIO,
    VIDEO;

    public static final Codec<MediaSignalType> CODEC = Codec.STRING.xmap(
            value -> valueOf(value.toUpperCase(Locale.ROOT)),
            value -> value.name().toLowerCase(Locale.ROOT));
}
