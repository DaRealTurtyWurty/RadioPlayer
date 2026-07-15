package dev.turtywurty.mediabox.sound.process;

import dev.turtywurty.mediabox.sound.AudioEmitter;

public record SpeakerProcessingContext(
        float sampleRate,
        AudioEmitter emitter
) {
}
