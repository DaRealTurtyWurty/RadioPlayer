package dev.turtywurty.mediabox.client.cable;

import dev.turtywurty.mediabox.cable.CableConstants;
import dev.turtywurty.mediabox.cable.MediaPortLookup;
import dev.turtywurty.mediabox.cable.PortEndpoint;
import dev.turtywurty.mediabox.cable.ResolvedMediaPort;
import dev.turtywurty.mediabox.cable.concealed.ConcealedCableRun;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ClientConcealedCableRouteCache {
    private static final Comparator<PortEndpoint> ENDPOINT_ORDER = Comparator
            .comparing((PortEndpoint endpoint) -> endpoint.dimension().identifier().toString())
            .thenComparingLong(endpoint -> endpoint.pos().asLong())
            .thenComparing(endpoint -> endpoint.portId().toString());
    private static final Map<UUID, CachedRoute> ROUTES = new HashMap<>();
    private static final Map<BlockPos, Set<UUID>> ROUTES_BY_BLOCK = new HashMap<>();

    private ClientConcealedCableRouteCache() {
    }

    public static List<ClientConcealedCableSegment> route(ClientLevel level, ConcealedCableRun run) {
        CachedRoute cached = ROUTES.get(run.id());
        if (cached != null && cached.run().equals(run))
            return cached.segments();

        removeRoute(run.id());
        Optional<CachedRoute> computed = computeRoute(level, run);
        if (computed.isEmpty())
            return List.of();

        CachedRoute route = computed.get();
        ROUTES.put(run.id(), route);
        for (BlockPos pos : route.occupiedBlocks()) {
            ROUTES_BY_BLOCK.computeIfAbsent(pos, ignored -> new HashSet<>()).add(run.id());
        }
        return route.segments();
    }

    public static void retainRuns(List<ConcealedCableRun> runs) {
        Map<UUID, ConcealedCableRun> currentRuns = new HashMap<>();
        runs.forEach(run -> currentRuns.put(run.id(), run));
        List<UUID> staleRoutes = ROUTES.entrySet().stream()
                .filter(entry -> !entry.getValue().run().equals(currentRuns.get(entry.getKey())))
                .map(Map.Entry::getKey)
                .toList();
        staleRoutes.forEach(ClientConcealedCableRouteCache::removeRoute);
    }

    public static void invalidateBlock(BlockPos pos) {
        Set<UUID> affectedRuns = Set.copyOf(ROUTES_BY_BLOCK.getOrDefault(pos, Set.of()));
        affectedRuns.forEach(ClientConcealedCableRouteCache::removeRoute);
    }

    public static void clear() {
        ROUTES.clear();
        ROUTES_BY_BLOCK.clear();
    }

    private static Optional<CachedRoute> computeRoute(ClientLevel level, ConcealedCableRun run) {
        List<PortEndpoint> endpoints = run.terminals().stream().sorted(ENDPOINT_ORDER).toList();
        if (endpoints.size() != 2)
            return Optional.empty();

        Optional<ResolvedMediaPort> firstPort = MediaPortLookup.resolve(level, endpoints.get(0));
        Optional<ResolvedMediaPort> secondPort = MediaPortLookup.resolve(level, endpoints.get(1));
        if (firstPort.isEmpty() || secondPort.isEmpty())
            return Optional.empty();

        BlockPos firstInsideWall = insideWallPosition(firstPort.get());
        BlockPos secondInsideWall = insideWallPosition(secondPort.get());
        Optional<List<BlockPos>> path = findShortestPath(
                level,
                firstInsideWall,
                secondInsideWall,
                CableConstants.MAX_CABLE_LENGTH);
        if (path.isEmpty())
            return Optional.empty();

        List<ClientConcealedCableSegment> segments = buildSegments(
                firstPort.get(),
                secondPort.get(),
                path.get());
        return Optional.of(new CachedRoute(run, segments, Set.copyOf(path.get())));
    }

    private static Optional<List<BlockPos>> findShortestPath(
            ClientLevel level,
            BlockPos start,
            BlockPos end,
            int maxLength) {
        int minimumLength = Math.abs(start.getX() - end.getX())
                + Math.abs(start.getY() - end.getY())
                + Math.abs(start.getZ() - end.getZ());
        if (minimumLength > maxLength || !canRouteThrough(level, start) || !canRouteThrough(level, end))
            return Optional.empty();
        if (start.equals(end))
            return Optional.of(List.of(start.immutable()));

        BlockPos immutableStart = start.immutable();
        BlockPos immutableEnd = end.immutable();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        Map<BlockPos, Integer> distances = new HashMap<>();
        pending.add(immutableStart);
        distances.put(immutableStart, 0);

        while (!pending.isEmpty()) {
            BlockPos current = pending.removeFirst();
            int distance = distances.get(current);
            if (distance >= maxLength)
                continue;

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction).immutable();
                if (distances.containsKey(next) || !canRouteThrough(level, next))
                    continue;

                previous.put(next, current);
                distances.put(next, distance + 1);
                if (next.equals(immutableEnd))
                    return Optional.of(reconstructPath(previous, immutableStart, immutableEnd));
                pending.addLast(next);
            }
        }

        return Optional.empty();
    }

    private static boolean canRouteThrough(ClientLevel level, BlockPos pos) {
        return level.isLoaded(pos) && !level.getBlockState(pos).isAir();
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

    private static List<ClientConcealedCableSegment> buildSegments(
            ResolvedMediaPort firstPort,
            ResolvedMediaPort secondPort,
            List<BlockPos> positions) {
        if (positions.size() == 1) {
            EnumSet<Direction> terminalFaces = EnumSet.of(firstPort.port().face());
            terminalFaces.add(secondPort.port().face());
            return List.of(new ClientConcealedCableSegment(positions.getFirst(), terminalFaces));
        }

        List<ClientConcealedCableSegment> segments = new ArrayList<>(positions.size());
        for (int index = 0; index < positions.size(); index++) {
            BlockPos current = positions.get(index);
            EnumSet<Direction> connections = EnumSet.noneOf(Direction.class);
            if (index == 0)
                connections.add(firstPort.port().face());
            else
                connections.add(directionBetween(current, positions.get(index - 1)));

            if (index == positions.size() - 1)
                connections.add(secondPort.port().face());
            else
                connections.add(directionBetween(current, positions.get(index + 1)));
            segments.add(new ClientConcealedCableSegment(current, connections));
        }
        return List.copyOf(segments);
    }

    private static BlockPos insideWallPosition(ResolvedMediaPort port) {
        return port.endpoint().pos().relative(port.port().face().getOpposite()).immutable();
    }

    private static Direction directionBetween(BlockPos from, BlockPos to) {
        for (Direction direction : Direction.values()) {
            if (from.relative(direction).equals(to))
                return direction;
        }
        throw new IllegalArgumentException("Cable route positions must be face-adjacent");
    }

    private record CachedRoute(
            ConcealedCableRun run,
            List<ClientConcealedCableSegment> segments,
            Set<BlockPos> occupiedBlocks) {
        private CachedRoute {
            segments = List.copyOf(segments);
            occupiedBlocks = Set.copyOf(occupiedBlocks);
        }
    }

    private static void removeRoute(UUID runId) {
        CachedRoute removed = ROUTES.remove(runId);
        if (removed == null)
            return;

        for (BlockPos pos : removed.occupiedBlocks()) {
            Set<UUID> runIds = ROUTES_BY_BLOCK.get(pos);
            if (runIds == null)
                continue;
            runIds.remove(runId);
            if (runIds.isEmpty())
                ROUTES_BY_BLOCK.remove(pos);
        }
    }
}
