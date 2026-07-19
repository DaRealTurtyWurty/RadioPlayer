package dev.turtywurty.mediabox.client.cable;

import dev.turtywurty.mediabox.cable.VisibleCableConnection;
import dev.turtywurty.mediabox.cable.concealed.ConcealedCableRun;
import dev.turtywurty.mediabox.network.CableSnapshotMessage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

public final class ClientCableState {
    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    private ClientCableState() {
    }

    public static void apply(CableSnapshotMessage message) {
        ClientVisibleCableRouteCache.retainConnections(message.visibleConnections());
        ClientConcealedCableRouteCache.retainRuns(message.concealedRuns());
        snapshot = new Snapshot(
                message.dimension(),
                message.visibleConnections(),
                message.concealedRuns());
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    public static void clear() {
        ClientVisibleCableRouteCache.clear();
        ClientConcealedCableRouteCache.clear();
        snapshot = Snapshot.EMPTY;
    }

    public record Snapshot(
            ResourceKey<Level> dimension,
            List<VisibleCableConnection> visibleConnections,
            List<ConcealedCableRun> concealedRuns) {
        private static final Snapshot EMPTY = new Snapshot(null, List.of(), List.of());

        public Snapshot {
            visibleConnections = List.copyOf(visibleConnections);
            concealedRuns = List.copyOf(concealedRuns);
        }
    }
}
