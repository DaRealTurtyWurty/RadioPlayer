package dev.turtywurty.mediabox.client.cable;

import dev.turtywurty.mediabox.cable.PortEndpoint;
import dev.turtywurty.mediabox.cable.MediaSignalType;
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
        ClientVisibleCablePreview.invalidate();
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

    public static int connectionCount(PortEndpoint endpoint) {
        Snapshot current = snapshot;
        int count = 0;
        for (VisibleCableConnection connection : current.visibleConnections()) {
            if (connection.first().equals(endpoint) || connection.second().equals(endpoint))
                count++;
        }
        for (ConcealedCableRun run : current.concealedRuns()) {
            if (run.terminals().contains(endpoint))
                count++;
        }
        return count;
    }

    public static boolean hasConnectionBetween(
            PortEndpoint first,
            PortEndpoint second,
            MediaSignalType signalType) {
        Snapshot current = snapshot;
        for (VisibleCableConnection connection : current.visibleConnections()) {
            if (connection.signalType() == signalType
                    && (connection.first().equals(first) && connection.second().equals(second)
                    || connection.first().equals(second) && connection.second().equals(first)))
                return true;
        }
        for (ConcealedCableRun run : current.concealedRuns()) {
            if (run.signalType() == signalType
                    && run.terminals().contains(first)
                    && run.terminals().contains(second))
                return true;
        }
        return false;
    }

    public static void clear() {
        ClientVisibleCablePreview.invalidate();
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
