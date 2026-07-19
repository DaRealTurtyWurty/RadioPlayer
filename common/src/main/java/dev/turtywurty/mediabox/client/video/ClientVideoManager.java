package dev.turtywurty.mediabox.client.video;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.video.PlaybackStatus;
import dev.turtywurty.mediabox.video.VideoSessionState;
import dev.turtywurty.mediabox.video.VideoSource;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.*;

public final class ClientVideoManager {
    private static final Map<UUID, ClientVideoSession> SESSIONS = new HashMap<>();

    private ClientVideoManager() {
    }

    public static void add(ClientVideoSession session) {
        ClientVideoSession previous = SESSIONS.put(session.id(), session);
        if (previous != null) {
            previous.close();
        }
    }

    public static Optional<ClientVideoSession> get(UUID id) {
        return Optional.ofNullable(SESSIONS.get(id));
    }

    public static void remove(UUID id) {
        ClientVideoSession session = SESSIONS.remove(id);
        if (session != null)
            session.close();
    }

    public static void uploadLatestFrames() {
        for (ClientVideoSession session : SESSIONS.values()) {
            session.uploadLatestFrame();
        }
    }

    public static void clear() {
        for (ClientVideoSession session : SESSIONS.values()) {
            session.close();
        }

        SESSIONS.clear();
    }

    public static void reconcile(Minecraft minecraft) {
        if (minecraft.level == null)
            return;

        ClientScreenPlaybackState.Snapshot snapshot = ClientScreenPlaybackState.snapshot();

        if (snapshot.dimension() == null || !snapshot.dimension().equals(minecraft.level.dimension()))
            return;

        Map<UUID, VideoSessionState> wantedSessions = new HashMap<>();

        for (VideoSessionState state : snapshot.assignments().values()) {
            if (state.status() == PlaybackStatus.PLAYING) {
                wantedSessions.putIfAbsent(state.sessionId(), state);
            }
        }

        for (VideoSessionState state : wantedSessions.values()) {
            ensureSession(minecraft, state);
        }

        for (UUID sessionId : Set.copyOf(SESSIONS.keySet())) {
            if (!wantedSessions.containsKey(sessionId)) {
                remove(sessionId);
            }
        }
    }

    private static void ensureSession(Minecraft minecraft, VideoSessionState state) {
        if (SESSIONS.containsKey(state.sessionId()))
            return;

        String mediaLocation = resolveMediaLocation(minecraft, state.source());
        if (mediaLocation == null)
            return;

        try {
            add(new ClientVideoSession(
                    minecraft,
                    state.sessionId(),
                    mediaLocation,
                    1280,
                    720
            ));
        } catch (Exception exception) {
            MediaBox.LOGGER.error(
                    "Failed to create video session {}",
                    state.sessionId(),
                    exception
            );
        }
    }

    private static String resolveMediaLocation(
            Minecraft minecraft,
            VideoSource source
    ) {
        return switch (source) {
            case VideoSource.RemoteUrl remote -> remote.url();

            case VideoSource.ServerAsset asset -> {
                Path gameDirectory =
                        minecraft.gameDirectory.toPath()
                                .toAbsolutePath()
                                .normalize();

                Path mediaPath = gameDirectory.resolve(asset.assetId()).normalize();

                if (!mediaPath.startsWith(gameDirectory)) {
                    MediaBox.LOGGER.error(
                            "Rejected unsafe media asset path: {}",
                            asset.assetId()
                    );
                    yield null;
                }

                yield mediaPath.toString();
            }

            case VideoSource.LiveStream stream -> stream.streamId();

            case VideoSource.Builtin builtin -> {
                MediaBox.LOGGER.warn(
                        "Builtin video sources aren't supported yet: {}",
                        builtin
                );
                yield null;
            }
        };
    }
}
