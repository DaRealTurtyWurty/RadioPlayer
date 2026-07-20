package dev.turtywurty.mediabox.client.video;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.client.ytdlp.YtDlpVideoResolver;
import dev.turtywurty.mediabox.client.screen.ClientScreenState;
import dev.turtywurty.mediabox.screen.ScreenAssembly;
import dev.turtywurty.mediabox.video.PlaybackStatus;
import dev.turtywurty.mediabox.video.VideoSessionState;
import dev.turtywurty.mediabox.video.VideoSource;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientVideoManager {
    private static final long SYNC_CHECK_INTERVAL_TICKS = 20L;
    private static final long RESYNC_COOLDOWN_TICKS = 300L;
    private static final double IN_SYNC_THRESHOLD_SECONDS = 0.075;
    private static final double HARD_SEEK_THRESHOLD_SECONDS = 2.0;
    private static final double RATE_CORRECTION_WINDOW_SECONDS = 12.0;
    private static final double MIN_PLAYBACK_RATE = 0.97;
    private static final double MAX_PLAYBACK_RATE = 1.03;
    private static final double RATE_CHANGE_THRESHOLD = 0.001;

    private static final Map<UUID, ClientVideoSession> SESSIONS = new ConcurrentHashMap<>();
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
        if (session == null)
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

    public static void updateAudioPlaybackPosition(UUID id, double positionSeconds, long revision) {
        ClientVideoSession session = SESSIONS.get(id);
        if (session != null)
            session.updateAudioPlaybackPosition(positionSeconds, revision);
    }

    public static void remove(UUID id) {
        STARTING_SESSIONS.remove(id);
        LAST_SYNC_CHECK_TICKS.remove(id);
        LAST_RESYNC_TICKS.remove(id);
        ClientVideoSession session = SESSIONS.remove(id);
        if (session != null)
            session.close();
    }

    public static void uploadLatestFrames(Set<UUID> visibleSessions) {
        for (UUID sessionId : visibleSessions) {
            ClientVideoSession session = SESSIONS.get(sessionId);
            if (session != null)
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
        YtDlpVideoResolver.clear();
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
                () -> prepareMedia(minecraft, state.source(), mediaLocation),
                Util.nonCriticalIoPool()
        ).whenComplete((preparedMedia, throwable) -> minecraft.execute(() -> {
            if (!STARTING_SESSIONS.remove(state.sessionId()))
                return;

            if (throwable != null) {
                MediaBox.LOGGER.warn(
                        "Could not prepare video session {}",
                        state.sessionId(),
                        throwable
                );
                return;
            }

            if (preparedMedia == null) {
                MediaBox.LOGGER.warn("No playable media stream could be resolved for video session {}",
                        state.sessionId());
                return;
            }

            PreparedMedia availableMedia = preparedMedia;
            OptionalDouble availableDuration = availableMedia.durationSeconds();

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

            VideoResolution resolution = chooseResolution(minecraft, state.sessionId());
            try {
                add(new ClientVideoSession(
                        minecraft,
                        state.sessionId(),
                        availableMedia.mediaLocation(),
                        resolution.width(),
                        resolution.height(),
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

    private static PreparedMedia prepareMedia(
            Minecraft minecraft,
            VideoSource source,
            String mediaLocation
    ) {
        Double cachedDuration = DURATION_CACHE.get(mediaLocation);
        FfmpegVideoProbe.ProbeResult directProbe = FfmpegVideoProbe.probe(
                minecraft.gameDirectory.toPath(),
                mediaLocation
        );
        if (directProbe.playable()) {
            OptionalDouble duration = cachedDuration == null
                    ? directProbe.durationSeconds()
                    : OptionalDouble.of(cachedDuration);
            duration.ifPresent(value -> DURATION_CACHE.put(mediaLocation, value));
            return new PreparedMedia(mediaLocation, duration, directProbe.hasAudio());
        }

        if (source instanceof VideoSource.RemoteUrl) {
            Optional<YtDlpVideoResolver.ResolvedMedia> resolvedLocation = YtDlpVideoResolver.resolve(
                    minecraft.gameDirectory.toPath(),
                    mediaLocation
            );
            if (resolvedLocation.isPresent()) {
                String resolved = resolvedLocation.get().url();
                FfmpegVideoProbe.ProbeResult resolvedProbe = FfmpegVideoProbe.probe(
                        minecraft.gameDirectory.toPath(),
                        resolved
                );
                if (resolvedProbe.playable()) {
                    resolvedProbe.durationSeconds().ifPresent(value -> DURATION_CACHE.put(resolved, value));
                    MediaBox.LOGGER.info("Using a yt-dlp-resolved media stream for a video session");
                    return new PreparedMedia(resolved, resolvedProbe.durationSeconds(), resolvedProbe.hasAudio());
                }

                MediaBox.LOGGER.warn("FFmpeg could not open the media stream returned by yt-dlp");
            }
        }

        if (source instanceof VideoSource.RemoteUrl)
            return null;

        // Local/server-defined inputs may still be supported even when FFprobe
        // cannot obtain complete metadata.
        return new PreparedMedia(mediaLocation, OptionalDouble.empty(), false);
    }

    private static void synchronizeSession(
            Minecraft minecraft,
            VideoSessionState state,
            ClientVideoSession session
    ) {
        if (minecraft.level == null)
            return;
        if (!session.isPlaybackStarted())
            return;

        long currentTick = minecraft.level.getGameTime();
        Long lastCheckTick = LAST_SYNC_CHECK_TICKS.get(state.sessionId());
        if (lastCheckTick != null && currentTick - lastCheckTick < SYNC_CHECK_INTERVAL_TICKS)
            return;

        LAST_SYNC_CHECK_TICKS.put(state.sessionId(), currentTick);

        OptionalDouble playbackPosition = session.playbackPositionSeconds();
        if (playbackPosition.isEmpty() || session.isResynchronizing())
            return;

        String mediaLocation = session.mediaLocation();

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

        if (!hardSeek) {
            session.setPlaybackRate(targetRate);
            return;
        }

        Long lastResyncTick = LAST_RESYNC_TICKS.get(state.sessionId());
        if (lastResyncTick != null && currentTick - lastResyncTick < RESYNC_COOLDOWN_TICKS)
            return;

        double expectedStartupDelay = session.startupDelaySeconds().orElse(0.0);
        double correctedPosition = desiredPosition + expectedStartupDelay;
        if (state.looping()) {
            correctedPosition = wrapPosition(correctedPosition, duration);
        }

        MediaBox.LOGGER.info(
                "Hard-resynchronizing video session {} (drift {} seconds)",
                state.sessionId(),
                String.format(Locale.ROOT, "%.3f", drift)
        );
        targetRate = 1.0;

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

    private static VideoResolution chooseResolution(Minecraft minecraft, UUID sessionId) {
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        double largestProjectedDimension = 0.0;
        for (Map.Entry<UUID, VideoSessionState> entry
                : ClientScreenPlaybackState.snapshot().assignments().entrySet()) {
            if (!entry.getValue().sessionId().equals(sessionId))
                continue;
            ScreenAssembly assembly = ClientScreenState.get(entry.getKey()).orElse(null);
            if (assembly == null)
                continue;

            Vec3 center = Vec3.atCenterOf(assembly.origin())
                    .add(
                            assembly.right().getStepX() * (assembly.width() - 1) * 0.5,
                            (assembly.height() - 1) * 0.5,
                            assembly.right().getStepZ() * (assembly.width() - 1) * 0.5
                    );
            double distance = Math.max(1.0, center.distanceTo(camera));
            double focalPixels = minecraft.getWindow().getHeight()
                    / (2.0 * Math.tan(Math.toRadians(minecraft.gameRenderer.mainCamera().getFov()) * 0.5));
            double projected = Math.max(assembly.width(), assembly.height()) * focalPixels / distance;
            largestProjectedDimension = Math.max(largestProjectedDimension, projected);
        }

        if (largestProjectedDimension == 0.0)
            return new VideoResolution(854, 480);
        if (largestProjectedDimension >= 600.0)
            return new VideoResolution(1280, 720);
        if (largestProjectedDimension >= 300.0)
            return new VideoResolution(854, 480);
        return new VideoResolution(640, 360);
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

    private record PreparedMedia(String mediaLocation, OptionalDouble durationSeconds, boolean hasAudio) {
    }

    private record VideoResolution(int width, int height) {
    }
}
