package dev.turtywurty.mediabox.video;

import dev.turtywurty.mediabox.network.ScreenPlaybackUpsertMessage;
import net.blay09.mods.balm.Balm;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class ScreenPlaybackSync {
    private ScreenPlaybackSync() {
    }

    public static final UUID TEST_SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public static void ensureTestAssignment(ServerLevel level, UUID screenId) {
        ScreenPlaybackSavedData data = ScreenPlaybackSavedData.get(level);

        if (data.get(screenId).isPresent())
            return;

        var session = new VideoSessionState(
                TEST_SESSION_ID,
                new VideoSource.ServerAsset(
                        "2021-06-17-183704972.mp4",
                        ""
                ),
                PlaybackStatus.PLAYING,
                0L,
                0.0,
                true
        );

        upsert(level, new ScreenPlaybackAssignment(screenId, session));
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
}
