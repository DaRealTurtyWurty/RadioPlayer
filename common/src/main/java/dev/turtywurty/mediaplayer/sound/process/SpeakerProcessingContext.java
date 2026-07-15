package dev.turtywurty.mediaplayer.sound.process;

import dev.turtywurty.mediaplayer.sound.RadioAudioEmitter;

public record SpeakerProcessingContext(
        float sampleRate,
        RadioAudioEmitter emitter
) {
}