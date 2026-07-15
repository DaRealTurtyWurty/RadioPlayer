package dev.turtywurty.mediaplayer.sound.process;

import dev.turtywurty.mediaplayer.sound.AudioEmitter;

public record SpeakerProcessingContext(
        float sampleRate,
        AudioEmitter emitter
) {
}
