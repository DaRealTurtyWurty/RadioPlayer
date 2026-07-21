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
    private static final double MAX_SEEK_SECONDS = 365.0 * 24.0 * 60.0 * 60.0;

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

    public static void togglePaused(ServerLevel level, UUID screenId) {
        VideoSessionState session = session(level, screenId);
        if (session == null || session.status() == PlaybackStatus.STOPPED)
            return;

        long now = level.getGameTime();
        PlaybackStatus status = session.status() == PlaybackStatus.PLAYING
                ? PlaybackStatus.PAUSED
                : PlaybackStatus.PLAYING;
        updateTransport(level, screenId, session, status, session.positionAt(now), now);
    }

    public static void seekRelative(ServerLevel level, UUID screenId, double seconds) {
        VideoSessionState session = session(level, screenId);
        if (session == null || !Double.isFinite(seconds))
            return;

        long now = level.getGameTime();
        updateTransport(
                level,
                screenId,
                session,
                session.status(),
                Math.max(0.0, session.positionAt(now) + seconds),
                now
        );
    }

    public static void seekAbsolute(ServerLevel level, UUID screenId, double seconds) {
        VideoSessionState session = session(level, screenId);
        if (session == null || !Double.isFinite(seconds))
            return;

        long now = level.getGameTime();
        updateTransport(level, screenId, session, session.status(), Math.max(0.0, seconds), now);
    }

    public static void jumpToPresent(ServerLevel level, UUID screenId) {
        VideoSessionState session = session(level, screenId);
        if (session == null)
            return;

        long now = level.getGameTime();
        updateTransport(level, screenId, session, PlaybackStatus.PLAYING, 0.0, now);
    }

    private static @Nullable VideoSessionState session(ServerLevel level, UUID screenId) {
        return ScreenPlaybackSavedData.get(level)
                .get(screenId)
                .map(ScreenPlaybackAssignment::session)
                .orElse(null);
    }

    private static void updateTransport(
            ServerLevel level,
            UUID screenId,
            VideoSessionState previous,
            PlaybackStatus status,
            double positionSeconds,
            long epochGameTick
    ) {
        var updated = new VideoSessionState(
                previous.sessionId(),
                previous.source(),
                status,
                epochGameTick,
                Math.clamp(positionSeconds, 0.0, MAX_SEEK_SECONDS),
                previous.looping()
        );
        upsert(level, new ScreenPlaybackAssignment(screenId, updated));
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
