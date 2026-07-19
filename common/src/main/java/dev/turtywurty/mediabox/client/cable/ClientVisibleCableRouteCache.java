package dev.turtywurty.mediabox.client.cable;

import dev.turtywurty.mediabox.cable.MediaPortLookup;
import dev.turtywurty.mediabox.cable.VisibleCableConnection;
import dev.turtywurty.mediabox.cable.VisibleCableRoute;
import dev.turtywurty.mediabox.cable.VisibleCableRouteFactory;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Caches client-only render curves for visible cable connections. */
public final class ClientVisibleCableRouteCache {
    private static final Map<UUID, CachedRoute> ROUTES = new HashMap<>();

    private ClientVisibleCableRouteCache() {
    }

    public static Optional<VisibleCableRoute> route(ClientLevel level, VisibleCableConnection connection) {
        var first = MediaPortLookup.resolve(level, connection.first());
        var second = MediaPortLookup.resolve(level, connection.second());
        if (first.isEmpty() || second.isEmpty()) {
            ROUTES.remove(connection.id());
            return Optional.empty();
        }

        Vec3 firstPosition = VisibleCableRouteFactory.portPosition(first.get());
        Vec3 secondPosition = VisibleCableRouteFactory.portPosition(second.get());
        RouteKey key = new RouteKey(firstPosition, secondPosition, connection.cableItems());
        CachedRoute cached = ROUTES.get(connection.id());
        if (cached != null && cached.key().equals(key))
            return Optional.of(cached.route());

        VisibleCableRoute route = VisibleCableRouteFactory.create(
                first.get(),
                second.get(),
                connection.cableItems());
        ROUTES.put(connection.id(), new CachedRoute(key, route));
        return Optional.of(route);
    }

    public static void retainConnections(List<VisibleCableConnection> connections) {
        Set<UUID> retainedIds = connections.stream()
                .map(VisibleCableConnection::id)
                .collect(Collectors.toSet());
        ROUTES.keySet().retainAll(retainedIds);
    }

    public static void clear() {
        ROUTES.clear();
    }

    private record RouteKey(Vec3 first, Vec3 second, int cableItems) {
    }

    private record CachedRoute(RouteKey key, VisibleCableRoute route) {
    }
}
