package dev.turtywurty.radioplayer.sound;

import dev.turtywurty.radioplayer.sound.process.SpeakerProcessorFactory;

import java.util.List;

public record SpeakerProfile(
        float gain,
        float maxRange,
        float distanceFalloff,
        float coneMinGain,
        float coneWidth,
        List<SpeakerProcessorFactory> processors
) {
}