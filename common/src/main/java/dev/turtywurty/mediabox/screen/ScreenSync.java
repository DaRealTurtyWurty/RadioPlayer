package dev.turtywurty.mediabox.screen;

import dev.turtywurty.mediabox.network.ScreenAssemblyRemovalMessage;
import dev.turtywurty.mediabox.network.ScreenAssemblySnapshotMessage;
import dev.turtywurty.mediabox.network.ScreenAssemblyUpsertMessage;
import net.blay09.mods.balm.Balm;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class ScreenSync {
    private ScreenSync() {
    }

    public static void sendSnapshot(ServerPlayer player, ServerLevel level) {
        ScreenSavedData data = ScreenSavedData.get(level);
        Balm.networking().sendTo(player, new ScreenAssemblySnapshotMessage(
                level.dimension(),
                data.assemblies().stream().toList()));
    }

    public static void broadcastUpsert(ServerLevel level, ScreenAssembly assembly) {
        ScreenAssemblyUpsertMessage message = new ScreenAssemblyUpsertMessage(level.dimension(), assembly);
        for (ServerPlayer player : level.players()) {
            Balm.networking().sendTo(player, message);
        }
    }

    public static void broadcastRemoval(ServerLevel level, UUID screenId) {
        ScreenAssemblyRemovalMessage message = new ScreenAssemblyRemovalMessage(level.dimension(), screenId);
        for (ServerPlayer player : level.players()) {
            Balm.networking().sendTo(player, message);
        }
    }
}
