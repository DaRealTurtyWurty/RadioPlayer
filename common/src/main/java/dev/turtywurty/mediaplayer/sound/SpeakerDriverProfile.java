package dev.turtywurty.mediaplayer.sound;

import dev.turtywurty.mediaplayer.sound.process.SpeakerProcessorFactory;

import java.util.List;

public record SpeakerDriverProfile(
        float gain,
        List<SpeakerProcessorFactory> processors
) {
}
