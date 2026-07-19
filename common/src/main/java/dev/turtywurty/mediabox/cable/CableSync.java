package dev.turtywurty.mediabox.cable;

import dev.turtywurty.mediabox.network.CableSnapshotMessage;
import net.blay09.mods.balm.Balm;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class CableSync {
    private CableSync() {
    }

    public static void sendSnapshot(ServerPlayer player, ServerLevel level) {
        CableSavedData data = CableSavedData.get(level);
        Balm.networking().sendTo(player, createSnapshot(level, data));
    }

    public static void broadcastSnapshot(ServerLevel level) {
        CableSavedData data = CableSavedData.get(level);
        CableSnapshotMessage snapshot = createSnapshot(level, data);
        for (ServerPlayer player : level.players()) {
            Balm.networking().sendTo(player, snapshot);
        }
    }

    private static CableSnapshotMessage createSnapshot(ServerLevel level, CableSavedData data) {
        return new CableSnapshotMessage(
                level.dimension(),
                data.manager().visibleConnections().values().stream().toList(),
                data.manager().concealedCableRuns().values().stream().toList());
    }
}
