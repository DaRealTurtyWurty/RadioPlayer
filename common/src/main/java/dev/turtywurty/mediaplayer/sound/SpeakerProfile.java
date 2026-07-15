package dev.turtywurty.mediaplayer.sound;

import java.util.List;

public record SpeakerProfile(
        float gain,
        float maxRange,
        float distanceFalloff,
        float coneMinGain,
        float coneWidth,
        List<SpeakerDriverProfile> drivers
) {
}
