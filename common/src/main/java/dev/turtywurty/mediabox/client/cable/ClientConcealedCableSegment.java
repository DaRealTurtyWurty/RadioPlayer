package dev.turtywurty.mediabox.client.cable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.EnumSet;
import java.util.Objects;

public record ClientConcealedCableSegment(BlockPos pos, EnumSet<Direction> connections) {
    public ClientConcealedCableSegment {
        pos = Objects.requireNonNull(pos, "pos").immutable();
        connections = Objects.requireNonNull(connections, "connections").clone();
        if (connections.isEmpty())
            throw new IllegalArgumentException("A concealed cable segment needs at least one connection");
    }

    @Override
    public EnumSet<Direction> connections() {
        return this.connections.clone();
    }
}
