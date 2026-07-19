package dev.turtywurty.mediabox.sound;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * The client-visible state required to play media from an audio source.
 *
 * @param mediaLocation a decoder-specific location, such as an HTTP URL or a future media-item URI
 * @param playing whether playback should currently be active
 * @param looping whether the sound engine should reopen the media after it reaches the end
 * @param revision a source-controlled value that can force playback to restart without changing media
 * @param startPositionSeconds the media position at which a newly-created decoder should start
 * @param playbackRate the rate applied by the Minecraft sound instance
 * @param synchronizedVideoSessionId the video session whose client clock controls this audio, if any
 */
public record AudioPlaybackState(
        @NonNull String mediaLocation,
        boolean playing,
        boolean looping,
        long revision,
        double startPositionSeconds,
        double playbackRate,
        @Nullable UUID synchronizedVideoSessionId
) {
    public AudioPlaybackState {
        mediaLocation = mediaLocation.trim();
        if (!Double.isFinite(startPositionSeconds) || startPositionSeconds < 0.0)
            throw new IllegalArgumentException("Audio start position must be finite and non-negative");
        if (!Double.isFinite(playbackRate) || playbackRate <= 0.0)
            throw new IllegalArgumentException("Audio playback rate must be finite and positive");
    }

    public static AudioPlaybackState streaming(@NonNull String mediaLocation, boolean playing) {
        return new AudioPlaybackState(mediaLocation, playing, false, 0L, 0.0, 1.0, null);
    }

    public static AudioPlaybackState synchronizedVideo(
            @NonNull String mediaLocation,
            boolean playing,
            boolean looping,
            @NonNull UUID sessionId
    ) {
        return new AudioPlaybackState(mediaLocation, playing, looping, 0L, 0.0, 1.0, sessionId);
    }

    public AudioPlaybackState atRuntimePosition(
            @NonNull String resolvedMediaLocation,
            double positionSeconds,
            double rate,
            long synchronizationRevision
    ) {
        return new AudioPlaybackState(
                resolvedMediaLocation,
                this.playing,
                this.looping,
                synchronizationRevision,
                Math.max(0.0, positionSeconds),
                rate,
                this.synchronizedVideoSessionId
        );
    }

    public boolean isPlayable() {
        return this.playing && !this.mediaLocation.isBlank();
    }

    /**
     * Start position and rate are live clock values. They can change without requiring a new stream.
     */
    public boolean isSamePlayback(AudioPlaybackState other) {
        return other != null
                && this.playing == other.playing
                && this.looping == other.looping
                && this.revision == other.revision
                && this.mediaLocation.equals(other.mediaLocation)
                && Objects.equals(this.synchronizedVideoSessionId, other.synchronizedVideoSessionId);
    }
}
