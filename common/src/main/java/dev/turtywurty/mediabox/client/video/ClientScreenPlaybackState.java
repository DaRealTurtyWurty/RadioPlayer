package dev.turtywurty.mediabox.client.video;

import dev.turtywurty.mediabox.network.ScreenPlaybackSnapshotMessage;
import dev.turtywurty.mediabox.video.ScreenPlaybackAssignment;
import dev.turtywurty.mediabox.video.VideoSessionState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ClientScreenPlaybackState {
    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    private ClientScreenPlaybackState() {
    }

    public static void apply(ScreenPlaybackSnapshotMessage message) {
        Map<UUID, VideoSessionState> assignments = new HashMap<>();

        for (ScreenPlaybackAssignment assignment : message.assignments()) {
            assignments.put(assignment.screenId(), assignment.session());
        }

        snapshot = new Snapshot(message.dimension(), assignments);
    }

    public static Optional<VideoSessionState> get(UUID screenId) {
        return Optional.ofNullable(snapshot.assignments().get(screenId));
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    public static void clear() {
        snapshot = Snapshot.EMPTY;
    }

    public record Snapshot(ResourceKey<Level> dimension, Map<UUID, VideoSessionState> assignments) {
        private static final Snapshot EMPTY = new Snapshot(null, Map.of());

        public Snapshot {
            assignments = Map.copyOf(assignments);
        }
    }
}
