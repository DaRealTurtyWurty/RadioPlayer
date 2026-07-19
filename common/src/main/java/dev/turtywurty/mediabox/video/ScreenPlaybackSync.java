package dev.turtywurty.mediabox.video;

import dev.turtywurty.mediabox.network.ScreenPlaybackRemovalMessage;
import dev.turtywurty.mediabox.network.ScreenPlaybackSnapshotMessage;
import dev.turtywurty.mediabox.network.ScreenPlaybackUpsertMessage;
import net.blay09.mods.balm.Balm;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class ScreenPlaybackSync {
    private ScreenPlaybackSync() {
    }

    public static void upsert(ServerLevel level, ScreenPlaybackAssignment assignment) {
        ScreenPlaybackSavedData data = ScreenPlaybackSavedData.get(level);
        if (!data.upsert(assignment))
            return;

        var message = new ScreenPlaybackUpsertMessage(level.dimension(), assignment);

        for (ServerPlayer player : level.players()) {
            Balm.networking().sendTo(player, message);
        }
    }

    public static void remove(ServerLevel level, UUID screenId) {
        ScreenPlaybackSavedData data = ScreenPlaybackSavedData.get(level);

        if (data.remove(screenId).isEmpty())
            return;

        var message = new ScreenPlaybackRemovalMessage(level.dimension(), screenId);

        for (ServerPlayer player : level.players()) {
            Balm.networking().sendTo(player, message);
        }
    }

    public static void sendSnapshot(ServerPlayer player, ServerLevel level) {
        ScreenPlaybackSavedData data = ScreenPlaybackSavedData.get(level);

        Balm.networking().sendTo(
                player,
                new ScreenPlaybackSnapshotMessage(
                        level.dimension(),
                        data.assignments().stream().toList()
                )
        );
    }

    public static void playRemoteUrl(ServerLevel level, UUID screenId, String url) {
        var session = new VideoSessionState(
                UUID.randomUUID(),
                new VideoSource.RemoteUrl(url),
                PlaybackStatus.PLAYING,
                level.getGameTime(),
                0.0,
                true
        );

        upsert(level, new ScreenPlaybackAssignment(screenId, session));
    }
}
