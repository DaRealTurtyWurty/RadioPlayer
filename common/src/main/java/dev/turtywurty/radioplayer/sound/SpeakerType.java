package dev.turtywurty.radioplayer.sound;

import dev.turtywurty.radioplayer.sound.process.impl.LowPassProcessor;

import java.util.List;

public enum SpeakerType {
    FULL_RANGE(new SpeakerProfile(
            1.0F,
            32.0F,
            0.04F,
            0.15F,
            0.35F,
            List.of()
    )),

    SUBWOOFER(new SpeakerProfile(
            3.0F,
            40.0F,
            0.025F,
            0.75F,
            0.9F,
            List.of(() -> new LowPassProcessor(120.0F))
    ));

    private final SpeakerProfile profile;

    SpeakerType(SpeakerProfile profile) {
        this.profile = profile;
    }

    public SpeakerProfile profile() {
        return this.profile;
    }
}