package dev.turtywurty.mediabox.cable;

import dev.turtywurty.mediabox.block.CablePortBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/** Deterministic, server-side routing for visible cables around block collision shapes. */
public final class VisibleCableRoutePlanner {
    public static final double CABLE_RADIUS = 0.04;
    private static final double ENDPOINT_EGRESS = CABLE_RADIUS + 0.07;
    private static final double COLLISION_EPSILON = 1.0E-5;
    private static final double SAMPLE_STEP = 0.2;
    private static final double LENGTH_EPSILON = 1.0E-6;
    private static final int MAX_GRAPH_NODES = 514;
    private static final int MAX_DISCOVERY_PASSES = 128;
    private static final int SAG_SAMPLES = 8;
    private static final double MAX_SAG_DEPTH = 0.75;

    private VisibleCableRoutePlanner() {
    }

    public static Optional<VisibleCableRoute> findTautRoute(
            ServerLevel level,
            ResolvedMediaPort firstPort,
            ResolvedMediaPort secondPort,
            double maxLength) {
        if (!Double.isFinite(maxLength) || maxLength <= 0.0)
            return Optional.empty();

        Vec3 firstAnchor = portPosition(level, firstPort);
        Vec3 secondAnchor = portPosition(level, secondPort);
        Vec3 firstEgress = moveAlongFace(firstAnchor, firstPort.port().face(), ENDPOINT_EGRESS);
        Vec3 secondEgress = moveAlongFace(secondAnchor, secondPort.port().face(), ENDPOINT_EGRESS);
        double endpointLength = firstAnchor.distanceTo(firstEgress) + secondAnchor.distanceTo(secondEgress);
        double centralCapacity = maxLength - endpointLength;
        if (centralCapacity < firstEgress.distanceTo(secondEgress) - LENGTH_EPSILON)
            return Optional.empty();

        Optional<List<Vec3>> centralRoute = findVisibilityRoute(level, firstEgress, secondEgress, centralCapacity);
        if (centralRoute.isEmpty())
            return Optional.empty();

        List<Vec3> points = new ArrayList<>(centralRoute.get().size() + 2);
        appendDistinct(points, firstAnchor);
        centralRoute.get().forEach(point -> appendDistinct(points, point));
        appendDistinct(points, secondAnchor);
        VisibleCableRoute route = new VisibleCableRoute(points);
        return route.length() <= maxLength + LENGTH_EPSILON ? Optional.of(route) : Optional.empty();
    }

    public static VisibleCableRoute addCollisionSafeSag(
            ServerLevel level,
            VisibleCableRoute tautRoute,
            double cableCapacity) {
        double tautLength = tautRoute.length();
        double remaining = cableCapacity - tautLength;
        if (remaining <= 0.01)
            return tautRoute;

        double desiredExtraLength = Math.min(remaining * 0.8, 0.35 + tautLength * 0.05);
        List<Vec3> sagged = new ArrayList<>();
        List<Vec3> tautPoints = tautRoute.points();
        appendDistinct(sagged, tautPoints.getFirst());

        for (int index = 1; index < tautPoints.size(); index++) {
            Vec3 from = tautPoints.get(index - 1);
            Vec3 to = tautPoints.get(index);
            double spanLength = from.distanceTo(to);
            double allocatedExtra = desiredExtraLength * spanLength / tautLength;
            List<Vec3> span = sagSpan(level, from, to, allocatedExtra);
            for (int pointIndex = 1; pointIndex < span.size(); pointIndex++) {
                appendDistinct(sagged, span.get(pointIndex));
            }
        }

        VisibleCableRoute route = new VisibleCableRoute(sagged);
        return route.length() <= cableCapacity + LENGTH_EPSILON ? route : tautRoute;
    }

    public static Vec3 portPosition(ServerLevel level, ResolvedMediaPort port) {
        BlockPos pos = port.endpoint().pos();
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof CablePortBlock)
            return CablePortBlock.portPosition(pos, state);

        Direction face = port.port().face();
        return Vec3.atCenterOf(pos).add(
                face.getStepX() * 0.51,
                face.getStepY() * 0.51,
                face.getStepZ() * 0.51);
    }

    private static Optional<List<Vec3>> findVisibilityRoute(
            ServerLevel level,
            Vec3 start,
            Vec3 end,
            double maxLength) {
        List<Vec3> nodes = new ArrayList<>();
        nodes.add(start);
        nodes.add(end);
        Set<Obstacle> obstacles = new HashSet<>();

        for (int pass = 0; pass < MAX_DISCOVERY_PASSES; pass++) {
            int nodeCount = nodes.size();
            List<List<Edge>> graph = new ArrayList<>(nodeCount);
            for (int index = 0; index < nodeCount; index++) {
                graph.add(new ArrayList<>());
            }

            Set<Obstacle> discoveredThisPass = new HashSet<>();
            for (int first = 0; first < nodeCount; first++) {
                for (int second = first + 1; second < nodeCount; second++) {
                    Vec3 from = nodes.get(first);
                    Vec3 to = nodes.get(second);
                    double distance = from.distanceTo(to);
                    if (distance <= LENGTH_EPSILON || distance > maxLength + LENGTH_EPSILON)
                        continue;
                    if (start.distanceTo(from) + distance + to.distanceTo(end) > maxLength + LENGTH_EPSILON
                            && start.distanceTo(to) + distance + from.distanceTo(end) > maxLength + LENGTH_EPSILON)
                        continue;

                    Optional<Obstacle> blocker = firstBlockingObstacle(level, from, to);
                    if (blocker.isPresent()) {
                        if (!obstacles.contains(blocker.get()))
                            discoveredThisPass.add(blocker.get());
                        continue;
                    }

                    graph.get(first).add(new Edge(second, distance));
                    graph.get(second).add(new Edge(first, distance));
                }
            }

            Optional<List<Vec3>> currentRoute = shortestGraphRoute(nodes, graph, maxLength);
            if (currentRoute.isPresent())
                return currentRoute;

            if (!discoveredThisPass.isEmpty()) {
                List<Obstacle> ordered = discoveredThisPass.stream()
                        .sorted(Comparator
                                .comparingDouble((Obstacle obstacle) -> obstacle.center().distanceToSqr(start))
                                .thenComparingDouble(Obstacle::minX)
                                .thenComparingDouble(Obstacle::minY)
                                .thenComparingDouble(Obstacle::minZ)
                                .thenComparingDouble(Obstacle::maxX)
                                .thenComparingDouble(Obstacle::maxY)
                                .thenComparingDouble(Obstacle::maxZ))
                        .toList();
                for (Obstacle obstacle : ordered) {
                    obstacles.add(obstacle);
                    for (Vec3 corner : obstacle.corners()) {
                        appendDistinct(nodes, corner);
                        if (nodes.size() > MAX_GRAPH_NODES)
                            return Optional.empty();
                    }
                }
                continue;
            }
            return Optional.empty();
        }

        return Optional.empty();
    }

    private static Optional<List<Vec3>> shortestGraphRoute(
            List<Vec3> nodes,
            List<List<Edge>> graph,
            double maxLength) {
        double[] distances = new double[nodes.size()];
        Arrays.fill(distances, Double.POSITIVE_INFINITY);
        int[] previous = new int[nodes.size()];
        Arrays.fill(previous, -1);
        PriorityQueue<GraphNode> pending = new PriorityQueue<>(Comparator.comparingDouble(GraphNode::distance));
        distances[0] = 0.0;
        pending.add(new GraphNode(0, 0.0));

        while (!pending.isEmpty()) {
            GraphNode current = pending.remove();
            if (current.distance() > distances[current.index()] + LENGTH_EPSILON)
                continue;
            if (current.index() == 1)
                break;

            for (Edge edge : graph.get(current.index())) {
                double nextDistance = current.distance() + edge.length();
                if (nextDistance > maxLength + LENGTH_EPSILON
                        || nextDistance + LENGTH_EPSILON >= distances[edge.target()])
                    continue;
                distances[edge.target()] = nextDistance;
                previous[edge.target()] = current.index();
                pending.add(new GraphNode(edge.target(), nextDistance));
            }
        }

        if (!Double.isFinite(distances[1]))
            return Optional.empty();

        List<Vec3> reversed = new ArrayList<>();
        for (int current = 1; current != -1; current = previous[current]) {
            reversed.add(nodes.get(current));
        }
        List<Vec3> route = new ArrayList<>(reversed.size());
        for (int index = reversed.size() - 1; index >= 0; index--) {
            route.add(reversed.get(index));
        }
        return Optional.of(List.copyOf(route));
    }

    private static List<Vec3> sagSpan(ServerLevel level, Vec3 from, Vec3 to, double extraLength) {
        double directLength = from.distanceTo(to);
        if (directLength < 0.5 || extraLength < 0.005)
            return List.of(from, to);

        double targetLength = directLength + extraLength;
        double low = 0.0;
        double high = Math.min(MAX_SAG_DEPTH, directLength * 0.4);
        for (int iteration = 0; iteration < 18; iteration++) {
            double middle = (low + high) * 0.5;
            List<Vec3> candidate = sampleSag(from, to, middle);
            if (polylineLength(candidate) <= targetLength)
                low = middle;
            else
                high = middle;
        }

        double depth = low;
        for (int attempt = 0; attempt < 10 && depth > 0.002; attempt++) {
            List<Vec3> candidate = sampleSag(from, to, depth);
            if (isCollisionFree(level, candidate))
                return candidate;
            depth *= 0.5;
        }
        return List.of(from, to);
    }

    private static List<Vec3> sampleSag(Vec3 from, Vec3 to, double depth) {
        List<Vec3> points = new ArrayList<>(SAG_SAMPLES + 1);
        for (int sample = 0; sample <= SAG_SAMPLES; sample++) {
            double progress = sample / (double) SAG_SAMPLES;
            double sag = depth * 4.0 * progress * (1.0 - progress);
            points.add(from.lerp(to, progress).add(0.0, -sag, 0.0));
        }
        return points;
    }

    private static boolean isCollisionFree(ServerLevel level, List<Vec3> points) {
        for (int index = 1; index < points.size(); index++) {
            if (firstBlockingObstacle(level, points.get(index - 1), points.get(index)).isPresent())
                return false;
        }
        return true;
    }

    private static Optional<Obstacle> firstBlockingObstacle(ServerLevel level, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        int samples = Math.max(1, (int) Math.ceil(length / SAMPLE_STEP));
        Set<BlockPos> checkedBlocks = new HashSet<>();
        Obstacle nearest = null;
        double nearestProgress = Double.POSITIVE_INFINITY;

        for (int sample = 0; sample <= samples; sample++) {
            Vec3 point = from.lerp(to, sample / (double) samples);
            BlockPos center = BlockPos.containing(point.x, point.y, point.z);
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        BlockPos pos = center.offset(x, y, z).immutable();
                        if (!checkedBlocks.add(pos))
                            continue;

                        if (!level.isLoaded(pos)) {
                            Obstacle obstacle = Obstacle.from(new AABB(pos).inflate(CABLE_RADIUS));
                            double progress = intersectionStart(obstacle.box(), from, to);
                            if (progress < nearestProgress) {
                                nearest = obstacle;
                                nearestProgress = progress;
                            }
                            continue;
                        }

                        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                        for (AABB localBox : shape.toAabbs()) {
                            AABB expanded = localBox
                                    .move(pos.getX(), pos.getY(), pos.getZ())
                                    .inflate(CABLE_RADIUS);
                            double progress = intersectionStart(expanded, from, to);
                            if (progress < nearestProgress) {
                                nearest = Obstacle.from(expanded);
                                nearestProgress = progress;
                            }
                        }
                    }
                }
            }
        }

        return Optional.ofNullable(nearest);
    }

    private static double intersectionStart(AABB box, Vec3 from, Vec3 to) {
        double minX = box.minX + COLLISION_EPSILON;
        double minY = box.minY + COLLISION_EPSILON;
        double minZ = box.minZ + COLLISION_EPSILON;
        double maxX = box.maxX - COLLISION_EPSILON;
        double maxY = box.maxY - COLLISION_EPSILON;
        double maxZ = box.maxZ - COLLISION_EPSILON;
        if (minX >= maxX || minY >= maxY || minZ >= maxZ)
            return Double.POSITIVE_INFINITY;

        double[] interval = {0.0, 1.0};
        if (!clipAxis(from.x, to.x - from.x, minX, maxX, interval)
                || !clipAxis(from.y, to.y - from.y, minY, maxY, interval)
                || !clipAxis(from.z, to.z - from.z, minZ, maxZ, interval))
            return Double.POSITIVE_INFINITY;
        return interval[0];
    }

    private static boolean clipAxis(double origin, double delta, double min, double max, double[] interval) {
        if (Math.abs(delta) < 1.0E-12)
            return origin >= min && origin <= max;

        double first = (min - origin) / delta;
        double second = (max - origin) / delta;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        interval[0] = Math.max(interval[0], first);
        interval[1] = Math.min(interval[1], second);
        return interval[0] <= interval[1];
    }

    private static double polylineLength(List<Vec3> points) {
        double length = 0.0;
        for (int index = 1; index < points.size(); index++) {
            length += points.get(index - 1).distanceTo(points.get(index));
        }
        return length;
    }

    private static Vec3 moveAlongFace(Vec3 point, Direction face, double distance) {
        return point.add(
                face.getStepX() * distance,
                face.getStepY() * distance,
                face.getStepZ() * distance);
    }

    private static void appendDistinct(List<Vec3> points, Vec3 point) {
        if (points.stream().noneMatch(existing -> existing.distanceToSqr(point) < 1.0E-12))
            points.add(point);
    }

    private record Edge(int target, double length) {
    }

    private record GraphNode(int index, double distance) {
    }

    private record Obstacle(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        private static Obstacle from(AABB box) {
            return new Obstacle(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        }

        private AABB box() {
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        private Vec3 center() {
            return new Vec3((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
        }

        private List<Vec3> corners() {
            List<Vec3> corners = new ArrayList<>(8);
            for (double x : new double[]{minX, maxX}) {
                for (double y : new double[]{minY, maxY}) {
                    for (double z : new double[]{minZ, maxZ}) {
                        corners.add(new Vec3(x, y, z));
                    }
                }
            }
            return corners;
        }
    }
}
