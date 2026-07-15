package dev.turtywurty.mediabox.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction8;
import net.minecraft.core.Vec3i;

public record AudioEmitter(
        BlockPos pos,
        Direction8 facing,
        SpeakerType type,
        float gain
) {
    public Vec3i getFacingNormal() {
        return switch (this.facing) {
            case NORTH -> new Vec3i(0, 0, -1);
            case SOUTH -> new Vec3i(0, 0, 1);
            case EAST -> new Vec3i(1, 0, 0);
            case WEST -> new Vec3i(-1, 0, 0);
            case NORTH_EAST -> new Vec3i(1, 0, -1);
            case NORTH_WEST -> new Vec3i(-1, 0, -1);
            case SOUTH_EAST -> new Vec3i(1, 0, 1);
            case SOUTH_WEST -> new Vec3i(-1, 0, 1);
        };
    }
}
