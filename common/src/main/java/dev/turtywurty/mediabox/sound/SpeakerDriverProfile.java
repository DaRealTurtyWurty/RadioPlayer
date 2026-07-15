package dev.turtywurty.mediabox.sound;

import dev.turtywurty.mediabox.sound.process.SpeakerProcessorFactory;

import java.util.List;

public record SpeakerDriverProfile(
        float gain,
        List<SpeakerProcessorFactory> processors
) {
}
