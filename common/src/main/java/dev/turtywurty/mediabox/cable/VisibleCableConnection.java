package dev.turtywurty.mediabox.cable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Objects;
import java.util.UUID;

public record VisibleCableConnection(
        UUID id,
        PortEndpoint first,
        PortEndpoint second,
        MediaSignalType signalType,
        float slack
) {
    public static final Codec<VisibleCableConnection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(VisibleCableConnection::id),
            PortEndpoint.CODEC.fieldOf("first").forGetter(VisibleCableConnection::first),
            PortEndpoint.CODEC.fieldOf("second").forGetter(VisibleCableConnection::second),
            MediaSignalType.CODEC.fieldOf("signal_type").forGetter(VisibleCableConnection::signalType),
            Codec.FLOAT.optionalFieldOf("slack", 0.0F).forGetter(VisibleCableConnection::slack)
    ).apply(instance, VisibleCableConnection::new));

    public VisibleCableConnection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(signalType, "signalType");
    }
}
