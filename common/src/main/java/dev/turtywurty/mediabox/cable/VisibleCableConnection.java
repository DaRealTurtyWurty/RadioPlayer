package dev.turtywurty.mediabox.cable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record VisibleCableConnection(
        UUID id,
        PortEndpoint first,
        PortEndpoint second,
        MediaSignalType signalType,
        Optional<PortEndpoint> sourceEndpoint,
        int cableItems
) {
    public static final Codec<VisibleCableConnection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(VisibleCableConnection::id),
            PortEndpoint.CODEC.fieldOf("first").forGetter(VisibleCableConnection::first),
            PortEndpoint.CODEC.fieldOf("second").forGetter(VisibleCableConnection::second),
            MediaSignalType.CODEC.fieldOf("signal_type").forGetter(VisibleCableConnection::signalType),
            PortEndpoint.CODEC.optionalFieldOf("source_endpoint")
                    .forGetter(VisibleCableConnection::sourceEndpoint),
            Codec.INT.fieldOf("cable_items").forGetter(VisibleCableConnection::cableItems)
    ).apply(instance, VisibleCableConnection::new));

    public VisibleCableConnection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(signalType, "signalType");
        sourceEndpoint = Objects.requireNonNull(sourceEndpoint, "sourceEndpoint");
        if (PortEndpoint.CANONICAL_ORDER.compare(first, second) > 0) {
            PortEndpoint previousFirst = first;
            first = second;
            second = previousFirst;
        }
        if (sourceEndpoint.isPresent()
                && !sourceEndpoint.get().equals(first)
                && !sourceEndpoint.get().equals(second))
            throw new IllegalArgumentException("A visible cable source must be one of its endpoints");
        if (cableItems < 1)
            throw new IllegalArgumentException("A visible cable needs at least one cable item");
    }
}
