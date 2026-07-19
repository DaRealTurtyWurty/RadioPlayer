package dev.turtywurty.mediabox.cable.concealed;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/** Shared shortest-path routing for cables embedded inside full collision blocks. */
public final class ConcealedCableRouting {
    private ConcealedCableRouting() {
    }

    public static Optional<List<BlockPos>> findShortestPath(
            Level level,
            BlockPos start,
            BlockPos end,
            int maxLength) {
        int minimumLength = manhattanDistance(start, end);
        if (minimumLength > maxLength || !canRouteThrough(level, start) || !canRouteThrough(level, end))
            return Optional.empty();
        if (start.equals(end))
            return Optional.of(List.of(start.immutable()));

        BlockPos immutableStart = start.immutable();
        BlockPos immutableEnd = end.immutable();
        PriorityQueue<RouteNode> pending = new PriorityQueue<>(Comparator
                .comparingInt(RouteNode::estimatedTotalDistance)
                .thenComparingInt(RouteNode::distance));
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        Map<BlockPos, Integer> distances = new HashMap<>();
        pending.add(new RouteNode(immutableStart, 0, minimumLength));
        distances.put(immutableStart, 0);

        while (!pending.isEmpty()) {
            RouteNode node = pending.remove();
            BlockPos current = node.pos();
            int distance = node.distance();
            if (distance != distances.getOrDefault(current, Integer.MAX_VALUE))
                continue;
            if (distance >= maxLength)
                continue;

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction).immutable();
                int nextDistance = distance + 1;
                int estimatedTotalDistance = nextDistance + manhattanDistance(next, immutableEnd);
                if (estimatedTotalDistance > maxLength
                        || nextDistance >= distances.getOrDefault(next, Integer.MAX_VALUE)
                        || !canRouteThrough(level, next))
                    continue;

                previous.put(next, current);
                distances.put(next, nextDistance);
                if (next.equals(immutableEnd))
                    return Optional.of(reconstructPath(previous, immutableStart, immutableEnd));
                pending.add(new RouteNode(next, nextDistance, estimatedTotalDistance));
            }
        }

        return Optional.empty();
    }

    public static int manhattanDistance(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX())
                + Math.abs(first.getY() - second.getY())
                + Math.abs(first.getZ() - second.getZ());
    }

    public static boolean canRouteThrough(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
    }

    private static List<BlockPos> reconstructPath(
            Map<BlockPos, BlockPos> previous,
            BlockPos start,
            BlockPos end) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos current = end;
        while (true) {
            path.add(current);
            if (current.equals(start))
                break;
            current = previous.get(current);
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }

    private record RouteNode(BlockPos pos, int distance, int estimatedTotalDistance) {
    }
}
