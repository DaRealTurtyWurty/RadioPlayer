package dev.turtywurty.radioplayer.sound.process;

import dev.turtywurty.radioplayer.sound.RadioAudioEmitter;

public record SpeakerProcessingContext(
        float sampleRate,
        RadioAudioEmitter emitter
) {
}