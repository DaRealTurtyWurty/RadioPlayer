package dev.turtywurty.mediabox.client.cable;

import dev.turtywurty.mediabox.cable.CableConstants;
import dev.turtywurty.mediabox.cable.MediaPortLookup;
import dev.turtywurty.mediabox.cable.PortEndpoint;
import dev.turtywurty.mediabox.cable.ResolvedMediaPort;
import dev.turtywurty.mediabox.cable.concealed.ConcealedCableRun;
import dev.turtywurty.mediabox.cable.concealed.ConcealedCableRouting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ClientConcealedCableRouteCache {
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
        List<PortEndpoint> endpoints = run.terminals().stream().sorted(PortEndpoint.CANONICAL_ORDER).toList();
        if (endpoints.size() != 2)
            return Optional.empty();

        Optional<ResolvedMediaPort> firstPort = MediaPortLookup.resolve(level, endpoints.get(0));
        Optional<ResolvedMediaPort> secondPort = MediaPortLookup.resolve(level, endpoints.get(1));
        if (firstPort.isEmpty() || secondPort.isEmpty())
            return Optional.empty();

        BlockPos firstInsideWall = insideWallPosition(firstPort.get());
        BlockPos secondInsideWall = insideWallPosition(secondPort.get());
        List<BlockPos> path = run.path();
        if (path.isEmpty()) {
            path = ConcealedCableRouting.findShortestPath(
                            level,
                            firstInsideWall,
                            secondInsideWall,
                            CableConstants.capacityForItems(run.cableItems()))
                    .orElse(List.of());
        }
        path = orientPath(path, firstInsideWall, secondInsideWall).orElse(List.of());
        if (path.isEmpty() || path.stream().anyMatch(pos -> !ConcealedCableRouting.canRouteThrough(level, pos)))
            return Optional.empty();

        List<ClientConcealedCableSegment> segments = buildSegments(
                firstPort.get(),
                secondPort.get(),
                path);
        return Optional.of(new CachedRoute(run, segments, Set.copyOf(path)));
    }

    private static Optional<List<BlockPos>> orientPath(
            List<BlockPos> path,
            BlockPos firstInsideWall,
            BlockPos secondInsideWall) {
        if (path.isEmpty())
            return Optional.empty();
        if (path.getFirst().equals(firstInsideWall) && path.getLast().equals(secondInsideWall))
            return Optional.of(path);
        if (!path.getFirst().equals(secondInsideWall) || !path.getLast().equals(firstInsideWall))
            return Optional.empty();

        List<BlockPos> reversed = new ArrayList<>(path.reversed());
        return Optional.of(List.copyOf(reversed));
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
