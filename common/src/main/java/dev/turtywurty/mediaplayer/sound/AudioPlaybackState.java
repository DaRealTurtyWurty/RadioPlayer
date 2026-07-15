package dev.turtywurty.mediaplayer.sound;

import org.jspecify.annotations.NonNull;

/**
 * The client-visible state required to play media from an audio source.
 *
 * @param mediaLocation a decoder-specific location, such as an HTTP URL or a future media-item URI
 * @param playing whether playback should currently be active
 * @param looping whether the sound engine should reopen the media after it reaches the end
 * @param revision a source-controlled value that can force playback to restart without changing media
 */
public record AudioPlaybackState(
        @NonNull String mediaLocation,
        boolean playing,
        boolean looping,
        long revision
) {
    public AudioPlaybackState {
        mediaLocation = mediaLocation.trim();
    }

    public static AudioPlaybackState streaming(@NonNull String mediaLocation, boolean playing) {
        return new AudioPlaybackState(mediaLocation, playing, false, 0L);
    }

    public boolean isPlayable() {
        return this.playing && !this.mediaLocation.isBlank();
    }
}
