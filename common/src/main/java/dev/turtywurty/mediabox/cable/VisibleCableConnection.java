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
        int cableItems,
        VisibleCableRoute route
) {
    public static final Codec<VisibleCableConnection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(VisibleCableConnection::id),
            PortEndpoint.CODEC.fieldOf("first").forGetter(VisibleCableConnection::first),
            PortEndpoint.CODEC.fieldOf("second").forGetter(VisibleCableConnection::second),
            MediaSignalType.CODEC.fieldOf("signal_type").forGetter(VisibleCableConnection::signalType),
            Codec.INT.fieldOf("cable_items").forGetter(VisibleCableConnection::cableItems),
            VisibleCableRoute.CODEC.fieldOf("route").forGetter(VisibleCableConnection::route)
    ).apply(instance, VisibleCableConnection::new));

    public VisibleCableConnection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(signalType, "signalType");
        Objects.requireNonNull(route, "route");
        if (cableItems < 1)
            throw new IllegalArgumentException("A visible cable needs at least one cable item");
        if (route.length() > CableConstants.capacityForItems(cableItems) + 1.0E-6)
            throw new IllegalArgumentException("A visible cable route exceeds its purchased capacity");
    }
}
