package dev.turtywurty.mediaplayer.sound;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Implemented by block entities that can provide media to the client audio pipeline.
 */
public interface AudioSourceProvider {
    BlockPos getAudioSourcePos();

    AudioPlaybackState getAudioPlaybackState();

    List<AudioEmitter> getBuiltInAudioEmitters();

    /**
     * Radio streams use static while connecting. Other media sources normally do not.
     */
    default boolean playsLoadingStatic() {
        return false;
    }
}
