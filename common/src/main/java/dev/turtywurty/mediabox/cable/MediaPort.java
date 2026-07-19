package dev.turtywurty.mediabox.cable;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Set;

public record MediaPort(
        Identifier id,
        Direction face,
        Vec3 attachmentPoint,
        PortDirection direction,
        int connectionLimit,
        Set<MediaSignalType> supportedSignals
) {
    public static final int UNLIMITED_CONNECTIONS = -1;

    public MediaPort {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(attachmentPoint, "attachmentPoint");
        Objects.requireNonNull(direction, "direction");
        supportedSignals = Set.copyOf(Objects.requireNonNull(supportedSignals, "supportedSignals"));

        if (connectionLimit != UNLIMITED_CONNECTIONS && connectionLimit < 1)
            throw new IllegalArgumentException("A media port connection limit must be positive or unlimited");

        if (!Double.isFinite(attachmentPoint.x)
                || !Double.isFinite(attachmentPoint.y)
                || !Double.isFinite(attachmentPoint.z))
            throw new IllegalArgumentException("A media port attachment point must be finite");

        if (supportedSignals.isEmpty())
            throw new IllegalArgumentException("A media port must support at least one signal type");
    }

    public Vec3 worldPosition(BlockPos blockPos) {
        return Vec3.atLowerCornerOf(blockPos).add(this.attachmentPoint);
    }

    public boolean supports(MediaSignalType signalType) {
        return this.supportedSignals.contains(signalType);
    }

    public boolean canAcceptConnection(int existingConnections) {
        if (existingConnections < 0)
            throw new IllegalArgumentException("Existing connection count cannot be negative");
        return this.connectionLimit == UNLIMITED_CONNECTIONS || existingConnections < this.connectionLimit;
    }
}
