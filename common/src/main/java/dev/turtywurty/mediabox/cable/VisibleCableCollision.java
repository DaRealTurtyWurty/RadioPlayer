package dev.turtywurty.mediabox.cable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A connection-time collision test for a visible cable's fixed render curve. */
public final class VisibleCableCollision {
    private static final double CABLE_RADIUS = 0.04;
    private static final double INTERSECTION_EPSILON = 1.0E-6;

    private VisibleCableCollision() {
    }

    public static boolean isClear(
            Level level,
            VisibleCableRoute route,
            BlockPos firstEndpointBlock,
            BlockPos secondEndpointBlock) {
        var points = route.points();
        for (int index = 1; index < points.size(); index++) {
            if (!isSegmentClear(
                    level,
                    points.get(index - 1),
                    points.get(index),
                    firstEndpointBlock,
                    secondEndpointBlock))
                return false;
        }
        return true;
    }

    public static boolean intersectsBlock(
            Level level,
            VisibleCableRoute route,
            BlockPos blockPos,
            BlockPos firstEndpointBlock,
            BlockPos secondEndpointBlock) {
        if (blockPos.equals(firstEndpointBlock) || blockPos.equals(secondEndpointBlock))
            return false;

        var collisionBoxes = level.getBlockState(blockPos).getCollisionShape(level, blockPos).toAabbs();
        if (collisionBoxes.isEmpty())
            return false;

        var points = route.points();
        for (int index = 1; index < points.size(); index++) {
            Vec3 from = points.get(index - 1);
            Vec3 to = points.get(index);
            for (AABB localBox : collisionBoxes) {
                AABB cableExpandedBox = localBox
                        .move(blockPos.getX(), blockPos.getY(), blockPos.getZ())
                        .inflate(CABLE_RADIUS);
                if (segmentIntersects(cableExpandedBox, from, to))
                    return true;
            }
        }
        return false;
    }

    private static boolean isSegmentClear(
            Level level,
            Vec3 from,
            Vec3 to,
            BlockPos firstEndpointBlock,
            BlockPos secondEndpointBlock) {
        AABB bounds = new AABB(
                Math.min(from.x, to.x),
                Math.min(from.y, to.y),
                Math.min(from.z, to.z),
                Math.max(from.x, to.x),
                Math.max(from.y, to.y),
                Math.max(from.z, to.z))
                .inflate(CABLE_RADIUS);
        BlockPos minimum = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos maximum = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        for (int x = minimum.getX(); x <= maximum.getX(); x++) {
            for (int y = minimum.getY(); y <= maximum.getY(); y++) {
                for (int z = minimum.getZ(); z <= maximum.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (pos.equals(firstEndpointBlock) || pos.equals(secondEndpointBlock))
                        continue;
                    if (!level.isLoaded(pos))
                        return false;

                    for (AABB localBox : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs()) {
                        AABB cableExpandedBox = localBox
                                .move(pos.getX(), pos.getY(), pos.getZ())
                                .inflate(CABLE_RADIUS);
                        if (segmentIntersects(cableExpandedBox, from, to))
                            return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean segmentIntersects(AABB box, Vec3 from, Vec3 to) {
        double[] interval = {0.0, 1.0};
        return clipAxis(from.x, to.x - from.x, box.minX, box.maxX, interval)
                && clipAxis(from.y, to.y - from.y, box.minY, box.maxY, interval)
                && clipAxis(from.z, to.z - from.z, box.minZ, box.maxZ, interval);
    }

    private static boolean clipAxis(double origin, double delta, double minimum, double maximum, double[] interval) {
        if (Math.abs(delta) < INTERSECTION_EPSILON)
            return origin >= minimum && origin <= maximum;

        double first = (minimum - origin) / delta;
        double second = (maximum - origin) / delta;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        interval[0] = Math.max(interval[0], first);
        interval[1] = Math.min(interval[1], second);
        return interval[0] <= interval[1];
    }
}
