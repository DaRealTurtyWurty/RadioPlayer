package dev.turtywurty.mediabox.cable;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Set;

public record MediaPort(
        Identifier id,
        Direction face,
        PortDirection direction,
        Set<MediaSignalType> supportedSignals
) {
    public MediaPort {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(direction, "direction");
        supportedSignals = Set.copyOf(Objects.requireNonNull(supportedSignals, "supportedSignals"));

        if (supportedSignals.isEmpty())
            throw new IllegalArgumentException("A media port must support at least one signal type");
    }

    public boolean supports(MediaSignalType signalType) {
        return this.supportedSignals.contains(signalType);
    }
}
