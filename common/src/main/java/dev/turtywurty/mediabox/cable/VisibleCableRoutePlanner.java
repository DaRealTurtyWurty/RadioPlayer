package dev.turtywurty.mediabox.cable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/** Selects an understandable air corridor, then settles a weighted rope inside it. */
public final class VisibleCableRoutePlanner {
    public static final double CABLE_RADIUS = 0.04;
    public static final double INVALIDATION_MARGIN = 0.75;
    private static final double ENDPOINT_EGRESS = CABLE_RADIUS + 0.07;
    private static final double GRID_SIZE = 0.5;
    private static final double COLLISION_EPSILON = 1.0E-5;
    private static final double LENGTH_EPSILON = 1.0E-5;
    private static final double PARTICLE_SPACING = 0.18;
    private static final double GRAVITY_STEP = 0.018;
    private static final double BASE_NATURAL_SLACK = 0.25;
    private static final double SLACK_PER_BLOCK = 0.10;
    private static final double MAX_NATURAL_SLACK = 1.25;
    private static final double BEND_COST = 0.14;
    private static final double UPWARD_COST = 0.10;
    private static final double DOWNWARD_DISCOUNT = 0.025;
    private static final double CLEARANCE_COST = 0.06;
    private static final double SEARCH_LENGTH_ALLOWANCE = 5.0;
    private static final int SETTLE_ITERATIONS = 72;
    private static final int CONSTRAINT_PASSES = 4;
    private static final int MAX_ROPE_PARTICLES = 512;
    private static final int MAX_SEARCHED_STATES = 60_000;
    private static final Direction[] SEARCH_DIRECTIONS = {
            Direction.DOWN,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST,
            Direction.UP
    };

    private VisibleCableRoutePlanner() {
    }

    public static Optional<PurchasedRoute> findPurchasableRoute(
            Level level,
            ResolvedMediaPort firstPort,
            ResolvedMediaPort secondPort,
            int maximumCableItems) {
        if (maximumCableItems < 1)
            return Optional.empty();

        double directLength = portPosition(firstPort).distanceTo(portPosition(secondPort));
        int minimumCableItems = CableConstants.itemsForLength(directLength);
        Optional<VisibleCableRoute> maximumRoute = findSettledRoute(
                level,
                firstPort,
                secondPort,
                CableConstants.capacityForItems(maximumCableItems));
        if (maximumRoute.isEmpty())
            return Optional.empty();

        int upperCandidate = Math.min(
                maximumCableItems,
                Math.max(minimumCableItems, CableConstants.itemsForLength(maximumRoute.get().length())));
        for (int cableItems = minimumCableItems; cableItems <= upperCandidate; cableItems++) {
            if (cableItems == maximumCableItems)
                return Optional.of(new PurchasedRoute(maximumRoute.get(), cableItems));

            Optional<VisibleCableRoute> route = findSettledRoute(
                    level,
                    firstPort,
                    secondPort,
                    CableConstants.capacityForItems(cableItems));
            if (route.isPresent())
                return Optional.of(new PurchasedRoute(route.get(), cableItems));
        }
        return Optional.of(new PurchasedRoute(maximumRoute.get(), upperCandidate));
    }

    public static Optional<VisibleCableRoute> findSettledRoute(
            Level level,
            ResolvedMediaPort firstPort,
            ResolvedMediaPort secondPort,
            double cableCapacity) {
        if (!Double.isFinite(cableCapacity) || cableCapacity <= 0.0)
            return Optional.empty();

        Vec3 firstAnchor = portPosition(firstPort);
        Vec3 secondAnchor = portPosition(secondPort);
        Vec3 firstEgress = moveAlongFace(firstAnchor, firstPort.port().face(), ENDPOINT_EGRESS);
        Vec3 secondEgress = moveAlongFace(secondAnchor, secondPort.port().face(), ENDPOINT_EGRESS);
        double endpointLength = firstAnchor.distanceTo(firstEgress) + secondAnchor.distanceTo(secondEgress);
        double centralCapacity = cableCapacity - endpointLength;
        if (centralCapacity < firstEgress.distanceTo(secondEgress) - LENGTH_EPSILON)
            return Optional.empty();

        Optional<List<Vec3>> corridor = findCorridor(
                level,
                firstEgress,
                secondEgress,
                centralCapacity + SEARCH_LENGTH_ALLOWANCE);
        if (corridor.isEmpty())
            return Optional.empty();

        List<Vec3> settled = settleRope(level, corridor.get(), centralCapacity);
        if (!isPolylineCollisionFree(level, settled, CABLE_RADIUS)
                || polylineLength(settled) > centralCapacity + LENGTH_EPSILON) {
            if (polylineLength(corridor.get()) > centralCapacity + LENGTH_EPSILON)
                return Optional.empty();
            settled = corridor.get();
        }

        List<Vec3> routePoints = new ArrayList<>(settled.size() + 2);
        appendConsecutiveDistinct(routePoints, firstAnchor);
        settled.forEach(point -> appendConsecutiveDistinct(routePoints, point));
        appendConsecutiveDistinct(routePoints, secondAnchor);
        VisibleCableRoute route = new VisibleCableRoute(routePoints);
        return route.length() <= cableCapacity + LENGTH_EPSILON ? Optional.of(route) : Optional.empty();
    }

    public static Vec3 portPosition(ResolvedMediaPort port) {
        return port.port().worldPosition(port.endpoint().pos());
    }

    private static Optional<List<Vec3>> findCorridor(Level level, Vec3 start, Vec3 end, double searchLimit) {
        if (isSegmentCollisionFree(level, start, end, CABLE_RADIUS))
            return Optional.of(List.of(start, end));

        List<Double> yOffsets = new ArrayList<>(3);
        appendDistinctOffset(yOffsets, gridOffset(start.y));
        appendDistinctOffset(yOffsets, gridOffset(end.y));
        appendDistinctOffset(yOffsets, 0.0);

        List<Vec3> bestCorridor = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (double yOffset : yOffsets) {
            Optional<List<Vec3>> corridor = findGridCorridor(level, start, end, searchLimit, yOffset);
            if (corridor.isEmpty())
                continue;

            double cost = corridorPreferenceCost(corridor.get(), start, end);
            if (cost + LENGTH_EPSILON < bestCost) {
                bestCorridor = corridor.get();
                bestCost = cost;
            }
        }
        return Optional.ofNullable(bestCorridor);
    }

    private static Optional<List<Vec3>> findGridCorridor(
            Level level,
            Vec3 start,
            Vec3 end,
            double searchLimit,
            double yOffset) {
        List<EndpointNode> starts = endpointNodes(level, start, yOffset);
        List<EndpointNode> ends = endpointNodes(level, end, yOffset);
        if (starts.isEmpty() || ends.isEmpty())
            return Optional.empty();

        Map<GridPoint, EndpointNode> endConnectors = new HashMap<>();
        for (EndpointNode endpoint : ends) {
            endConnectors.merge(
                    endpoint.point(),
                    endpoint,
                    (first, second) -> first.connectorCost() <= second.connectorCost() ? first : second);
        }

        Map<SearchState, SearchRecord> records = new HashMap<>();
        Map<SearchState, EndpointNode> startConnectors = new HashMap<>();
        PriorityQueue<PendingState> pending = new PriorityQueue<>(Comparator
                .comparingDouble(PendingState::estimatedCost)
                .thenComparingDouble(PendingState::physicalLength)
                .thenComparingInt(entry -> entry.state().point().x())
                .thenComparingInt(entry -> entry.state().point().y())
                .thenComparingInt(entry -> entry.state().point().z())
                .thenComparingInt(entry -> directionOrder(entry.state().incoming())));

        for (EndpointNode endpoint : starts) {
            SearchState state = new SearchState(endpoint.point(), null);
            double heuristic = axisDistance(endpoint.point(), ends);
            SearchRecord record = new SearchRecord(endpoint.connectorCost(), endpoint.connectorLength(), null);
            SearchRecord previous = records.get(state);
            if (previous == null || record.weightedCost() < previous.weightedCost()) {
                records.put(state, record);
                startConnectors.put(state, endpoint);
                pending.add(new PendingState(
                        state,
                        record.weightedCost(),
                        record.weightedCost() + heuristic,
                        record.physicalLength()));
            }
        }

        SearchState goalState = null;
        EndpointNode goalConnector = null;
        int searchedStates = 0;
        while (!pending.isEmpty() && searchedStates++ < MAX_SEARCHED_STATES) {
            PendingState pendingState = pending.remove();
            SearchRecord currentRecord = records.get(pendingState.state());
            if (currentRecord == null
                    || pendingState.weightedCost() > currentRecord.weightedCost() + LENGTH_EPSILON)
                continue;

            EndpointNode endConnector = endConnectors.get(pendingState.state().point());
            if (endConnector != null
                    && currentRecord.physicalLength() + endConnector.connectorLength()
                    <= searchLimit + LENGTH_EPSILON) {
                goalState = pendingState.state();
                goalConnector = endConnector;
                break;
            }

            Vec3 currentPosition = pendingState.state().point().position(yOffset);
            for (Direction direction : SEARCH_DIRECTIONS) {
                GridPoint nextPoint = pendingState.state().point().relative(direction);
                Vec3 nextPosition = nextPoint.position(yOffset);
                if (!isSegmentCollisionFree(level, currentPosition, nextPosition, CABLE_RADIUS))
                    continue;

                double nextPhysicalLength = currentRecord.physicalLength() + GRID_SIZE;
                double physicalLowerBound = nextPhysicalLength + nextPosition.distanceTo(end);
                if (physicalLowerBound > searchLimit + LENGTH_EPSILON)
                    continue;

                double stepCost = GRID_SIZE;
                if (pendingState.state().incoming() != null && pendingState.state().incoming() != direction)
                    stepCost += BEND_COST;
                if (direction == Direction.UP)
                    stepCost += UPWARD_COST;
                else if (direction == Direction.DOWN)
                    stepCost -= DOWNWARD_DISCOUNT;
                if (!isPointCollisionFree(level, nextPosition, CABLE_RADIUS + 0.12))
                    stepCost += CLEARANCE_COST;

                SearchState nextState = new SearchState(nextPoint, direction);
                double nextWeightedCost = currentRecord.weightedCost() + stepCost;
                SearchRecord oldRecord = records.get(nextState);
                if (oldRecord != null && nextWeightedCost + LENGTH_EPSILON >= oldRecord.weightedCost())
                    continue;

                records.put(nextState, new SearchRecord(
                        nextWeightedCost,
                        nextPhysicalLength,
                        pendingState.state()));
                double heuristic = axisDistance(nextPoint, ends);
                pending.add(new PendingState(
                        nextState,
                        nextWeightedCost,
                        nextWeightedCost + heuristic,
                        nextPhysicalLength));
            }
        }

        if (goalState == null)
            return Optional.empty();

        List<SearchState> reversed = new ArrayList<>();
        SearchState current = goalState;
        while (current != null) {
            reversed.add(current);
            current = records.get(current).previous();
        }

        SearchState startState = reversed.getLast();
        EndpointNode startConnector = startConnectors.get(startState);
        if (startConnector == null || goalConnector == null)
            return Optional.empty();

        List<Vec3> gridPoints = new ArrayList<>(reversed.size());
        for (int index = reversed.size() - 1; index >= 0; index--) {
            appendConsecutiveDistinct(gridPoints, reversed.get(index).point().position(yOffset));
        }
        gridPoints = shortcutCorridor(level, compressCollinear(gridPoints));

        List<Vec3> points = new ArrayList<>(
                startConnector.path().size() + gridPoints.size() + goalConnector.path().size());
        startConnector.path().forEach(point -> appendConsecutiveDistinct(points, point));
        gridPoints.forEach(point -> appendConsecutiveDistinct(points, point));
        for (int index = goalConnector.path().size() - 2; index >= 0; index--) {
            appendConsecutiveDistinct(points, goalConnector.path().get(index));
        }
        return Optional.of(List.copyOf(points));
    }

    private static List<EndpointNode> endpointNodes(Level level, Vec3 endpoint, double yOffset) {
        int centerX = (int) Math.round(endpoint.x / GRID_SIZE);
        int centerY = (int) Math.round((endpoint.y - yOffset) / GRID_SIZE);
        int centerZ = (int) Math.round(endpoint.z / GRID_SIZE);
        List<EndpointNode> candidates = new ArrayList<>();
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    GridPoint point = new GridPoint(centerX + x, centerY + y, centerZ + z);
                    Vec3 position = point.position(yOffset);
                    if (endpoint.distanceTo(position) > 1.5
                            || !isPointCollisionFree(level, position, CABLE_RADIUS))
                        continue;

                    endpointConnector(level, endpoint, position)
                            .ifPresent(path -> candidates.add(new EndpointNode(
                                    point,
                                    path,
                                    polylineLength(path),
                                    connectorCost(path))));
                }
            }
        }
        candidates.sort(Comparator
                .comparingDouble(EndpointNode::connectorCost)
                .thenComparingDouble(EndpointNode::connectorLength)
                .thenComparingInt(candidate -> candidate.point().x())
                .thenComparingInt(candidate -> candidate.point().y())
                .thenComparingInt(candidate -> candidate.point().z()));
        return candidates.stream().limit(16).toList();
    }

    private static double gridOffset(double coordinate) {
        double offset = coordinate - Math.floor(coordinate / GRID_SIZE) * GRID_SIZE;
        return Math.abs(offset - GRID_SIZE) < LENGTH_EPSILON ? 0.0 : offset;
    }

    private static void appendDistinctOffset(List<Double> offsets, double offset) {
        if (offsets.stream().noneMatch(existing -> Math.abs(existing - offset) < LENGTH_EPSILON))
            offsets.add(offset);
    }

    private static double corridorPreferenceCost(List<Vec3> points, Vec3 start, Vec3 end) {
        double cost = polylineLength(points);
        cost += Math.max(0, points.size() - 2) * BEND_COST;

        double endpointCeiling = Math.max(start.y, end.y);
        double maximumHeight = endpointCeiling;
        for (Vec3 point : points) {
            maximumHeight = Math.max(maximumHeight, point.y);
        }
        return cost + (maximumHeight - endpointCeiling) * 0.75;
    }

    private static Optional<List<Vec3>> endpointConnector(Level level, Vec3 endpoint, Vec3 gridPoint) {
        int[][] axisOrders = {
                {0, 2, 1},
                {2, 0, 1},
                {0, 1, 2},
                {2, 1, 0},
                {1, 0, 2},
                {1, 2, 0}
        };
        List<Vec3> bestPath = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (int[] axisOrder : axisOrders) {
            List<Vec3> path = orthogonalPath(endpoint, gridPoint, axisOrder);
            if (!isPolylineCollisionFree(level, path, CABLE_RADIUS))
                continue;

            double cost = connectorCost(path);
            if (cost + LENGTH_EPSILON < bestCost) {
                bestPath = path;
                bestCost = cost;
            }
        }
        return Optional.ofNullable(bestPath);
    }

    private static List<Vec3> orthogonalPath(Vec3 from, Vec3 to, int[] axisOrder) {
        List<Vec3> points = new ArrayList<>(4);
        appendConsecutiveDistinct(points, from);
        Vec3 current = from;
        for (int axis : axisOrder) {
            current = switch (axis) {
                case 0 -> new Vec3(to.x, current.y, current.z);
                case 1 -> new Vec3(current.x, to.y, current.z);
                case 2 -> new Vec3(current.x, current.y, to.z);
                default -> throw new IllegalArgumentException("Invalid axis: " + axis);
            };
            appendConsecutiveDistinct(points, current);
        }
        return List.copyOf(points);
    }

    private static double connectorCost(List<Vec3> path) {
        double cost = polylineLength(path);
        int bends = Math.max(0, path.size() - 2);
        cost += bends * BEND_COST;

        for (int index = 1; index < path.size(); index++) {
            double verticalChange = path.get(index).y - path.get(index - 1).y;
            if (verticalChange > 0.0)
                cost += verticalChange * UPWARD_COST / GRID_SIZE;
            else if (verticalChange < 0.0)
                cost += verticalChange * DOWNWARD_DISCOUNT / GRID_SIZE;
        }
        return cost;
    }

    private static double axisDistance(GridPoint point, List<EndpointNode> ends) {
        double minimum = Double.POSITIVE_INFINITY;
        for (EndpointNode end : ends) {
            double gridDistance = (Math.abs(point.x() - end.point().x())
                    + Math.abs(point.y() - end.point().y())
                    + Math.abs(point.z() - end.point().z())) * GRID_SIZE;
            minimum = Math.min(minimum, gridDistance * 0.95 + end.connectorCost());
        }
        return minimum;
    }

    private static List<Vec3> settleRope(Level level, List<Vec3> corridor, double capacity) {
        double corridorLength = polylineLength(corridor);
        double availableSlack = capacity - corridorLength - 0.002;
        if (availableSlack <= LENGTH_EPSILON)
            return corridor;

        double preferredSlack = Math.min(
                MAX_NATURAL_SLACK,
                BASE_NATURAL_SLACK + corridorLength * SLACK_PER_BLOCK);
        double requestedSlack = Math.min(availableSlack * 0.9, preferredSlack);
        for (double slackScale : new double[]{1.0, 0.7, 0.4}) {
            double targetLength = corridorLength + requestedSlack * slackScale;
            List<Vec3> settled = simulateSettledRope(level, corridor, targetLength);
            if (polylineLength(settled) <= capacity + LENGTH_EPSILON
                    && isPolylineCollisionFree(level, settled, CABLE_RADIUS))
                return settled;
        }
        return corridor;
    }

    private static List<Vec3> simulateSettledRope(Level level, List<Vec3> corridor, double targetLength) {
        double corridorLength = polylineLength(corridor);
        int particleCount = Math.min(
                MAX_ROPE_PARTICLES,
                Math.max(3, (int) Math.ceil(Math.max(corridorLength, targetLength) / PARTICLE_SPACING) + 1));
        List<Vec3> particles = resample(corridor, particleCount);
        double restLength = targetLength / (particleCount - 1);

        for (int iteration = 0; iteration < SETTLE_ITERATIONS; iteration++) {
            for (int index = 1; index < particles.size() - 1; index++) {
                particles.set(index, projectOutOfCollisions(
                        level,
                        particles.get(index).add(0.0, -GRAVITY_STEP, 0.0),
                        CABLE_RADIUS));
            }

            for (int pass = 0; pass < CONSTRAINT_PASSES; pass++) {
                boolean forwards = ((iteration * CONSTRAINT_PASSES + pass) & 1) == 0;
                if (forwards) {
                    for (int index = 0; index < particles.size() - 1; index++)
                        satisfyDistanceConstraint(level, particles, index, restLength);
                } else {
                    for (int index = particles.size() - 2; index >= 0; index--)
                        satisfyDistanceConstraint(level, particles, index, restLength);
                }
            }
        }

        return List.copyOf(particles);
    }

    private static void satisfyDistanceConstraint(Level level, List<Vec3> particles, int firstIndex, double restLength) {
        int secondIndex = firstIndex + 1;
        Vec3 first = particles.get(firstIndex);
        Vec3 second = particles.get(secondIndex);
        Vec3 difference = second.subtract(first);
        double distance = difference.length();
        if (distance < 1.0E-9)
            return;

        double correctionScale = (distance - restLength) / distance;
        boolean firstPinned = firstIndex == 0;
        boolean secondPinned = secondIndex == particles.size() - 1;
        if (firstPinned) {
            second = second.subtract(difference.scale(correctionScale));
        } else if (secondPinned) {
            first = first.add(difference.scale(correctionScale));
        } else {
            Vec3 halfCorrection = difference.scale(correctionScale * 0.5);
            first = first.add(halfCorrection);
            second = second.subtract(halfCorrection);
        }

        if (!firstPinned)
            particles.set(firstIndex, projectOutOfCollisions(level, first, CABLE_RADIUS));
        if (!secondPinned)
            particles.set(secondIndex, projectOutOfCollisions(level, second, CABLE_RADIUS));
    }

    private static List<Vec3> resample(List<Vec3> points, int particleCount) {
        double totalLength = polylineLength(points);
        List<Vec3> sampled = new ArrayList<>(particleCount);
        sampled.add(points.getFirst());
        int segment = 1;
        double traversed = 0.0;
        for (int particle = 1; particle < particleCount - 1; particle++) {
            double target = totalLength * particle / (particleCount - 1.0);
            while (segment < points.size()) {
                Vec3 from = points.get(segment - 1);
                Vec3 to = points.get(segment);
                double segmentLength = from.distanceTo(to);
                if (traversed + segmentLength >= target) {
                    double progress = segmentLength < 1.0E-9 ? 0.0 : (target - traversed) / segmentLength;
                    sampled.add(from.lerp(to, progress));
                    break;
                }
                traversed += segmentLength;
                segment++;
            }
        }
        sampled.add(points.getLast());
        return sampled;
    }

    private static Vec3 projectOutOfCollisions(Level level, Vec3 point, double radius) {
        Vec3 projected = point;
        for (int iteration = 0; iteration < 4; iteration++) {
            AABB containing = containingCollisionBox(level, projected, radius);
            if (containing == null)
                return projected;

            double toMinX = projected.x - containing.minX;
            double toMaxX = containing.maxX - projected.x;
            double toMinY = projected.y - containing.minY;
            double toMaxY = containing.maxY - projected.y;
            double toMinZ = projected.z - containing.minZ;
            double toMaxZ = containing.maxZ - projected.z;
            double minimum = Math.min(Math.min(Math.min(toMinX, toMaxX), Math.min(toMinY, toMaxY)), Math.min(toMinZ, toMaxZ));
            if (minimum == toMinY)
                projected = new Vec3(projected.x, containing.minY - COLLISION_EPSILON, projected.z);
            else if (minimum == toMaxY)
                projected = new Vec3(projected.x, containing.maxY + COLLISION_EPSILON, projected.z);
            else if (minimum == toMinX)
                projected = new Vec3(containing.minX - COLLISION_EPSILON, projected.y, projected.z);
            else if (minimum == toMaxX)
                projected = new Vec3(containing.maxX + COLLISION_EPSILON, projected.y, projected.z);
            else if (minimum == toMinZ)
                projected = new Vec3(projected.x, projected.y, containing.minZ - COLLISION_EPSILON);
            else
                projected = new Vec3(projected.x, projected.y, containing.maxZ + COLLISION_EPSILON);
        }
        return projected;
    }

    private static AABB containingCollisionBox(Level level, Vec3 point, double radius) {
        BlockPos center = BlockPos.containing(point.x, point.y, point.z);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!level.isLoaded(pos)) {
                        AABB unloaded = new AABB(pos).inflate(radius);
                        if (containsStrictly(unloaded, point))
                            return unloaded;
                        continue;
                    }
                    for (AABB local : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs()) {
                        AABB expanded = local.move(pos.getX(), pos.getY(), pos.getZ()).inflate(radius);
                        if (containsStrictly(expanded, point))
                            return expanded;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isPointCollisionFree(Level level, Vec3 point, double radius) {
        return containingCollisionBox(level, point, radius) == null;
    }

    private static boolean isPolylineCollisionFree(Level level, List<Vec3> points, double radius) {
        for (int index = 1; index < points.size(); index++) {
            if (!isSegmentCollisionFree(level, points.get(index - 1), points.get(index), radius))
                return false;
        }
        return true;
    }

    private static boolean isSegmentCollisionFree(Level level, Vec3 from, Vec3 to, double radius) {
        double length = from.distanceTo(to);
        int samples = Math.max(1, (int) Math.ceil(length / 0.18));
        Set<BlockPos> checked = new HashSet<>();
        for (int sample = 0; sample <= samples; sample++) {
            Vec3 point = from.lerp(to, sample / (double) samples);
            BlockPos center = BlockPos.containing(point.x, point.y, point.z);
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        BlockPos pos = center.offset(x, y, z).immutable();
                        if (!checked.add(pos))
                            continue;
                        if (!level.isLoaded(pos))
                            return false;
                        for (AABB local : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs()) {
                            AABB expanded = local.move(pos.getX(), pos.getY(), pos.getZ()).inflate(radius);
                            if (segmentIntersectsInterior(expanded, from, to))
                                return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean segmentIntersectsInterior(AABB box, Vec3 from, Vec3 to) {
        double[] interval = {0.0, 1.0};
        return clipAxis(from.x, to.x - from.x, box.minX + COLLISION_EPSILON, box.maxX - COLLISION_EPSILON, interval)
                && clipAxis(from.y, to.y - from.y, box.minY + COLLISION_EPSILON, box.maxY - COLLISION_EPSILON, interval)
                && clipAxis(from.z, to.z - from.z, box.minZ + COLLISION_EPSILON, box.maxZ - COLLISION_EPSILON, interval);
    }

    private static boolean clipAxis(double origin, double delta, double min, double max, double[] interval) {
        if (min >= max)
            return false;
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

    private static boolean containsStrictly(AABB box, Vec3 point) {
        return point.x > box.minX + COLLISION_EPSILON && point.x < box.maxX - COLLISION_EPSILON
                && point.y > box.minY + COLLISION_EPSILON && point.y < box.maxY - COLLISION_EPSILON
                && point.z > box.minZ + COLLISION_EPSILON && point.z < box.maxZ - COLLISION_EPSILON;
    }

    private static List<Vec3> compressCollinear(List<Vec3> points) {
        if (points.size() <= 2)
            return List.copyOf(points);
        List<Vec3> compressed = new ArrayList<>();
        compressed.add(points.getFirst());
        for (int index = 1; index < points.size() - 1; index++) {
            Vec3 before = points.get(index).subtract(points.get(index - 1));
            Vec3 after = points.get(index + 1).subtract(points.get(index));
            if (before.cross(after).lengthSqr() > 1.0E-10)
                compressed.add(points.get(index));
        }
        compressed.add(points.getLast());
        return List.copyOf(compressed);
    }

    private static List<Vec3> shortcutCorridor(Level level, List<Vec3> points) {
        if (points.size() <= 2)
            return points;

        List<Vec3> shortened = new ArrayList<>();
        int current = 0;
        shortened.add(points.getFirst());
        while (current < points.size() - 1) {
            int next = current + 1;
            for (int candidate = points.size() - 1; candidate > current + 1; candidate--) {
                if (isSegmentCollisionFree(level, points.get(current), points.get(candidate), CABLE_RADIUS)) {
                    next = candidate;
                    break;
                }
            }
            shortened.add(points.get(next));
            current = next;
        }
        return List.copyOf(shortened);
    }

    private static double polylineLength(List<Vec3> points) {
        double length = 0.0;
        for (int index = 1; index < points.size(); index++)
            length += points.get(index - 1).distanceTo(points.get(index));
        return length;
    }

    private static Vec3 moveAlongFace(Vec3 point, Direction face, double distance) {
        return point.add(face.getStepX() * distance, face.getStepY() * distance, face.getStepZ() * distance);
    }

    private static void appendConsecutiveDistinct(List<Vec3> points, Vec3 point) {
        if (points.isEmpty() || points.getLast().distanceToSqr(point) > 1.0E-12)
            points.add(point);
    }

    private static int directionOrder(Direction direction) {
        return direction == null ? -1 : Arrays.asList(SEARCH_DIRECTIONS).indexOf(direction);
    }

    private record GridPoint(int x, int y, int z) {
        private Vec3 position(double yOffset) {
            return new Vec3(x * GRID_SIZE, y * GRID_SIZE + yOffset, z * GRID_SIZE);
        }

        private GridPoint relative(Direction direction) {
            return new GridPoint(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());
        }
    }

    private record EndpointNode(
            GridPoint point,
            List<Vec3> path,
            double connectorLength,
            double connectorCost) {
    }

    private record SearchState(GridPoint point, Direction incoming) {
    }

    private record SearchRecord(double weightedCost, double physicalLength, SearchState previous) {
    }

    private record PendingState(
            SearchState state,
            double weightedCost,
            double estimatedCost,
            double physicalLength) {
    }

    public record PurchasedRoute(VisibleCableRoute route, int cableItems) {
    }
}
