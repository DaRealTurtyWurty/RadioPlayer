package dev.turtywurty.mediabox.sound;

import net.minecraft.client.sounds.AudioStream;

/** An audio stream that receives the OpenAL presentation position of its queued PCM. */
public interface PlaybackStartedAudioStream extends AudioStream {
    void onPlaybackStarted();

    default void onPlaybackCursor(long playedFrames) {
    }
}
