package dev.turtywurty.mediabox.cable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

public final class MediaPortLookup {
    private MediaPortLookup() {
    }

    public static Optional<ResolvedMediaPort> resolve(
            Level level,
            BlockPos pos,
            Direction clickedFace,
            Vec3 hitLocation) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(clickedFace, "clickedFace");
        Objects.requireNonNull(hitLocation, "hitLocation");

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof MediaPortProvider provider))
            return Optional.empty();

        Vec3 localHit = hitLocation.subtract(pos.getX(), pos.getY(), pos.getZ());
        return provider.getMediaPortAt(clickedFace, localHit)
                .map(port -> new ResolvedMediaPort(PortEndpoint.of(level, pos, port), port));
    }

    public static Optional<ResolvedMediaPort> resolve(Level level, PortEndpoint endpoint) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(endpoint, "endpoint");
        if (!level.dimension().equals(endpoint.dimension()))
            return Optional.empty();

        BlockEntity blockEntity = level.getBlockEntity(endpoint.pos());
        if (!(blockEntity instanceof MediaPortProvider provider))
            return Optional.empty();

        return provider.getMediaPort(endpoint.portId())
                .map(port -> new ResolvedMediaPort(endpoint, port));
    }
}
