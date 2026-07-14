package dev.turtywurty.radioplayer.sound;

import dev.turtywurty.radioplayer.sound.process.SpeakerProcessorFactory;

import java.util.List;

public record SpeakerDriverProfile(
        float gain,
        List<SpeakerProcessorFactory> processors
) {
}
