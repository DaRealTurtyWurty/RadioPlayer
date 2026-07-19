package dev.turtywurty.mediabox.video;

import dev.turtywurty.mediabox.block.entity.FlatScreenBlockEntity;
import dev.turtywurty.mediabox.network.ScreenPlaybackRemovalMessage;
import dev.turtywurty.mediabox.network.ScreenPlaybackSnapshotMessage;
import dev.turtywurty.mediabox.network.ScreenPlaybackUpsertMessage;
import dev.turtywurty.mediabox.screen.ScreenAssembly;
import dev.turtywurty.mediabox.screen.ScreenSavedData;
import net.blay09.mods.balm.Balm;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class ScreenPlaybackSync {
    private ScreenPlaybackSync() {
    }

    public static void upsert(ServerLevel level, ScreenPlaybackAssignment assignment) {
        ScreenPlaybackSavedData data = ScreenPlaybackSavedData.get(level);
        boolean changed = data.upsert(assignment);
        updateScreenPanels(level, assignment.screenId(), assignment.session());
        if (!changed)
            return;

        var message = new ScreenPlaybackUpsertMessage(level.dimension(), assignment);

        for (ServerPlayer player : level.players()) {
            Balm.networking().sendTo(player, message);
        }
    }

    public static void remove(ServerLevel level, UUID screenId) {
        ScreenPlaybackSavedData data = ScreenPlaybackSavedData.get(level);
        updateScreenPanels(level, screenId, null);

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

    private static void updateScreenPanels(
            ServerLevel level,
            UUID screenId,
            @Nullable VideoSessionState session
    ) {
        ScreenAssembly assembly = ScreenSavedData.get(level).get(screenId).orElse(null);
        if (assembly == null)
            return;

        BlockPos origin = assembly.origin();
        for (int y = 0; y < assembly.height(); y++) {
            for (int x = 0; x < assembly.width(); x++) {
                BlockPos pos = origin.relative(assembly.right(), x).above(y);
                if (level.getBlockEntity(pos) instanceof FlatScreenBlockEntity screen
                        && screenId.equals(screen.getScreenId())) {
                    screen.setInput(session);
                }
            }
        }
    }
}
