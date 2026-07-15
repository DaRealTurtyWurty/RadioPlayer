package dev.turtywurty.mediabox.cable.concealed;

import dev.turtywurty.mediabox.cable.CableSavedData;
import dev.turtywurty.mediabox.cable.CableSync;
import dev.turtywurty.mediabox.cable.CableConstants;
import dev.turtywurty.mediabox.cable.MediaPortLookup;
import dev.turtywurty.mediabox.cable.MediaSignalType;
import dev.turtywurty.mediabox.cable.PortDirection;
import dev.turtywurty.mediabox.cable.PortEndpoint;
import dev.turtywurty.mediabox.cable.ResolvedMediaPort;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Validates and installs only the server-relevant logical connection between two wall ports. */
public final class ConcealedCableInstaller {
    private ConcealedCableInstaller() {
    }

    public static ConcealedCableRun install(
            ServerLevel level,
            PortEndpoint firstTerminal,
            PortEndpoint secondTerminal,
            MediaSignalType signalType,
            int cableItems) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(firstTerminal, "firstTerminal");
        Objects.requireNonNull(secondTerminal, "secondTerminal");
        Objects.requireNonNull(signalType, "signalType");
        double cableCapacity = CableConstants.capacityForItems(cableItems);
        if (firstTerminal.equals(secondTerminal))
            throw new IllegalArgumentException("A concealed run needs two different wall terminals");
        if (!level.dimension().equals(firstTerminal.dimension())
                || !level.dimension().equals(secondTerminal.dimension()))
            throw new IllegalArgumentException("Concealed cable terminals must be in this dimension");

        ResolvedMediaPort firstPort = validateTerminal(level, firstTerminal, signalType);
        ResolvedMediaPort secondPort = validateTerminal(level, secondTerminal, signalType);
        BlockPos firstInsideWall = insideWallPosition(firstPort);
        BlockPos secondInsideWall = insideWallPosition(secondPort);
        int minimumLength = Math.abs(firstInsideWall.getX() - secondInsideWall.getX())
                + Math.abs(firstInsideWall.getY() - secondInsideWall.getY())
                + Math.abs(firstInsideWall.getZ() - secondInsideWall.getZ());
        if (minimumLength > cableCapacity)
            throw new IllegalArgumentException("The concealed cable requires more cable items");

        ConcealedCableRun run = new ConcealedCableRun(
                UUID.randomUUID(),
                signalType,
                Set.of(firstTerminal, secondTerminal),
                cableItems);
        CableSavedData.get(level).addConcealedCableRun(run);
        CableSync.broadcastSnapshot(level);
        return run;
    }

    private static ResolvedMediaPort validateTerminal(
            ServerLevel level,
            PortEndpoint endpoint,
            MediaSignalType signalType) {
        BlockEntity blockEntity = level.getBlockEntity(endpoint.pos());
        if (!(blockEntity instanceof ConcealedCablePortProvider))
            throw new IllegalArgumentException("Concealed cables must terminate at wall cable ports");

        ResolvedMediaPort resolved = MediaPortLookup.resolve(level, endpoint)
                .orElseThrow(() -> new IllegalArgumentException("A wall cable port is no longer available"));
        if (!resolved.port().supports(signalType))
            throw new IllegalArgumentException("A wall cable port does not support " + signalType);
        if (resolved.port().direction() != PortDirection.BIDIRECTIONAL)
            throw new IllegalArgumentException("A concealed cable terminal must be bidirectional");
        return resolved;
    }

    private static BlockPos insideWallPosition(ResolvedMediaPort port) {
        return port.endpoint().pos().relative(port.port().face().getOpposite()).immutable();
    }
}
