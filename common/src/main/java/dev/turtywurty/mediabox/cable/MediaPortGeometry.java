package dev.turtywurty.mediabox.cable;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Utilities for expressing cable attachment points in block-model coordinates. */
public final class MediaPortGeometry {
    private MediaPortGeometry() {
    }

    public static Vec3 modelPoint(double x, double y, double z) {
        return new Vec3(x / 16.0, y / 16.0, z / 16.0);
    }

    /** Rotates a point from the north-facing model into its horizontal block orientation. */
    public static Vec3 rotateFromNorth(Vec3 point, Direction facing) {
        return switch (facing) {
            case NORTH -> point;
            case EAST -> new Vec3(1.0 - point.z, point.y, point.x);
            case SOUTH -> new Vec3(1.0 - point.x, point.y, 1.0 - point.z);
            case WEST -> new Vec3(point.z, point.y, 1.0 - point.x);
            default -> throw new IllegalArgumentException("Model facing must be horizontal: " + facing);
        };
    }
}
