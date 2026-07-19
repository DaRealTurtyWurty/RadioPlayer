package dev.turtywurty.mediabox.client.video;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.video.PlaybackStatus;
import dev.turtywurty.mediabox.video.VideoSessionState;
import dev.turtywurty.mediabox.video.VideoSource;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientVideoManager {
    private static final long SYNC_CHECK_INTERVAL_TICKS = 300L;
    private static final long RESYNC_COOLDOWN_TICKS = 300L;
    private static final double IN_SYNC_THRESHOLD_SECONDS = 0.25;
    private static final double HARD_SEEK_THRESHOLD_SECONDS = 5.0;
    private static final double RATE_CORRECTION_WINDOW_SECONDS = 30.0;
    private static final double MIN_PLAYBACK_RATE = 0.95;
    private static final double MAX_PLAYBACK_RATE = 1.05;
    private static final double RATE_CHANGE_THRESHOLD = 0.002;

    private static final Map<UUID, ClientVideoSession> SESSIONS = new HashMap<>();
    private static final Set<UUID> STARTING_SESSIONS = new HashSet<>();
    private static final Map<String, Double> DURATION_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SYNC_CHECK_TICKS = new HashMap<>();
    private static final Map<UUID, Long> LAST_RESYNC_TICKS = new HashMap<>();

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

    public static Optional<AudioClock> getAudioClock(UUID id) {
        ClientVideoSession session = SESSIONS.get(id);
        if (session == null || !session.isReady())
            return Optional.empty();

        OptionalDouble position = session.playbackPositionSeconds();
        if (position.isEmpty())
            return Optional.empty();

        return Optional.of(new AudioClock(
                session.mediaLocation(),
                position.getAsDouble(),
                session.playbackRate(),
                session.discontinuityRevision()
        ));
    }

    public static void remove(UUID id) {
        STARTING_SESSIONS.remove(id);
        LAST_SYNC_CHECK_TICKS.remove(id);
        LAST_RESYNC_TICKS.remove(id);
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
        STARTING_SESSIONS.clear();
        LAST_SYNC_CHECK_TICKS.clear();
        LAST_RESYNC_TICKS.clear();
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
            ClientVideoSession session = SESSIONS.get(state.sessionId());
            if (session == null) {
                ensureSession(minecraft, state);
            } else {
                synchronizeSession(minecraft, state, session);
            }
        }

        for (UUID sessionId : Set.copyOf(SESSIONS.keySet())) {
            if (!wantedSessions.containsKey(sessionId)) {
                remove(sessionId);
            }
        }
    }

    private static void ensureSession(Minecraft minecraft, VideoSessionState state) {
        if (SESSIONS.containsKey(state.sessionId())
                || !STARTING_SESSIONS.add(state.sessionId()))
            return;

        if (minecraft.level == null) {
            STARTING_SESSIONS.remove(state.sessionId());
            return;
        }

        String mediaLocation = resolveMediaLocation(minecraft, state.source());
        if (mediaLocation == null) {
            STARTING_SESSIONS.remove(state.sessionId());
            return;
        }

        CompletableFuture.supplyAsync(
                () -> probeDuration(minecraft, mediaLocation),
                Util.nonCriticalIoPool()
        ).whenComplete((duration, throwable) -> minecraft.execute(() -> {
            if (!STARTING_SESSIONS.remove(state.sessionId()))
                return;

            if (throwable != null) {
                MediaBox.LOGGER.warn(
                        "Could not probe video session {}; starting without duration metadata",
                        state.sessionId(),
                        throwable
                );
            }

            OptionalDouble availableDuration = duration == null
                    ? OptionalDouble.empty()
                    : duration;

            if (!isStillWanted(state.sessionId()) || minecraft.level == null)
                return;

            double startPositionSeconds = authoritativePositionSeconds(minecraft, state);
            if (state.looping()) {
                if (availableDuration.isPresent()) {
                    startPositionSeconds %= availableDuration.getAsDouble();
                } else {
                    // Seeking far beyond an unknown-duration looping input can prevent
                    // FFmpeg from producing any frame at all. Starting at zero is a
                    // playable fallback until duration metadata becomes available.
                    startPositionSeconds = 0.0;
                }
            }

            try {
                add(new ClientVideoSession(
                        minecraft,
                        state.sessionId(),
                        mediaLocation,
                        1280,
                        720,
                        state.looping(),
                        startPositionSeconds
                ));
            } catch (Exception exception) {
                MediaBox.LOGGER.error(
                        "Failed to create video session {}",
                        state.sessionId(),
                        exception
                );
            }
        }));
    }

    private static OptionalDouble probeDuration(Minecraft minecraft, String mediaLocation) {
        Double cachedDuration = DURATION_CACHE.get(mediaLocation);
        if (cachedDuration != null)
            return OptionalDouble.of(cachedDuration);

        OptionalDouble duration = FfmpegVideoProbe.durationSeconds(
                minecraft.gameDirectory.toPath(),
                mediaLocation
        );
        duration.ifPresent(value -> DURATION_CACHE.put(mediaLocation, value));
        return duration;
    }

    private static void synchronizeSession(
            Minecraft minecraft,
            VideoSessionState state,
            ClientVideoSession session
    ) {
        if (minecraft.level == null)
            return;

        long currentTick = minecraft.level.getGameTime();
        Long lastCheckTick = LAST_SYNC_CHECK_TICKS.get(state.sessionId());
        if (lastCheckTick != null && currentTick - lastCheckTick < SYNC_CHECK_INTERVAL_TICKS)
            return;

        LAST_SYNC_CHECK_TICKS.put(state.sessionId(), currentTick);

        OptionalDouble playbackPosition = session.playbackPositionSeconds();
        if (playbackPosition.isEmpty() || session.isResynchronizing())
            return;

        Long lastResyncTick = LAST_RESYNC_TICKS.get(state.sessionId());
        if (lastResyncTick != null && currentTick - lastResyncTick < RESYNC_COOLDOWN_TICKS)
            return;

        String mediaLocation = resolveMediaLocation(minecraft, state.source());
        if (mediaLocation == null)
            return;

        Double duration = DURATION_CACHE.get(mediaLocation);
        if (state.looping() && duration == null)
            return;

        double desiredPosition = authoritativePositionSeconds(minecraft, state);
        double displayedPosition = playbackPosition.getAsDouble();
        double drift;
        if (state.looping()) {
            desiredPosition = wrapPosition(desiredPosition, duration);
            displayedPosition = wrapPosition(displayedPosition, duration);
            drift = circularDifference(desiredPosition, displayedPosition, duration);
        } else {
            drift = desiredPosition - displayedPosition;
        }

        boolean hardSeek = Math.abs(drift) >= HARD_SEEK_THRESHOLD_SECONDS;
        double targetRate = Math.abs(drift) <= IN_SYNC_THRESHOLD_SECONDS
                ? 1.0
                : Math.clamp(
                        1.0 + drift / RATE_CORRECTION_WINDOW_SECONDS,
                        MIN_PLAYBACK_RATE,
                        MAX_PLAYBACK_RATE
                );
        if (!hardSeek && Math.abs(targetRate - session.playbackRate()) < RATE_CHANGE_THRESHOLD)
            return;

        double expectedStartupDelay = session.startupDelaySeconds().orElse(0.0);
        double correctedPosition = hardSeek
                ? desiredPosition + expectedStartupDelay
                : displayedPosition + expectedStartupDelay * session.playbackRate();
        if (state.looping()) {
            correctedPosition = wrapPosition(correctedPosition, duration);
        }

        if (hardSeek) {
            MediaBox.LOGGER.info(
                    "Hard-resynchronizing video session {} (drift {} seconds)",
                    state.sessionId(),
                    String.format(Locale.ROOT, "%.3f", drift)
            );
            targetRate = 1.0;
        } else {
            MediaBox.LOGGER.info(
                    "Nudging video session {} to {}x (drift {} seconds)",
                    state.sessionId(),
                    String.format(Locale.ROOT, "%.4f", targetRate),
                    String.format(Locale.ROOT, "%.3f", drift)
            );
        }

        LAST_RESYNC_TICKS.put(state.sessionId(), currentTick);
        try {
            session.beginResync(correctedPosition, targetRate, hardSeek);
        } catch (Exception exception) {
            MediaBox.LOGGER.error(
                    "Failed to resynchronize video session {}",
                    state.sessionId(),
                    exception
            );
        }
    }

    private static double wrapPosition(double position, double duration) {
        double wrapped = position % duration;
        return wrapped < 0.0 ? wrapped + duration : wrapped;
    }

    private static double circularDifference(double desired, double displayed, double duration) {
        double difference = desired - displayed;
        if (difference > duration / 2.0) {
            difference -= duration;
        } else if (difference < -duration / 2.0) {
            difference += duration;
        }

        return difference;
    }

    private static boolean isStillWanted(UUID sessionId) {
        return ClientScreenPlaybackState.snapshot().assignments().values().stream()
                .anyMatch(state -> state.status() == PlaybackStatus.PLAYING
                        && state.sessionId().equals(sessionId));
    }

    private static double authoritativePositionSeconds(
            Minecraft minecraft,
            VideoSessionState state
    ) {
        long elapsedTicks = Math.max(
                0L,
                minecraft.level.getGameTime() - state.epochGameTick()
        );
        return state.positionAtEpochSeconds() + elapsedTicks / 20.0;
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

    public record AudioClock(
            String mediaLocation,
            double positionSeconds,
            double playbackRate,
            long discontinuityRevision
    ) {
    }
}
