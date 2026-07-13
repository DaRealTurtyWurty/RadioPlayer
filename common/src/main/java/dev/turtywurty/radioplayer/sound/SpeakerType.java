package dev.turtywurty.radioplayer.sound;

import dev.turtywurty.radioplayer.sound.process.SpeakerProcessorFactory;
import dev.turtywurty.radioplayer.sound.process.impl.BandPassProcessor;
import dev.turtywurty.radioplayer.sound.process.impl.HighPassProcessor;
import dev.turtywurty.radioplayer.sound.process.impl.LowPassProcessor;
import dev.turtywurty.radioplayer.sound.process.impl.SoftClipProcessor;

import java.util.List;

public enum SpeakerType {
    FULL_RANGE(new SpeakerProfile(
            1.0F,
            32.0F,
            0.04F,
            0.15F,
            0.35F,
            List.of(driver(1.0F))
    )),

    SUBWOOFER(new SpeakerProfile(
            3.0F,
            40.0F,
            0.025F,
            0.75F,
            0.9F,
            List.of(
                    driver(
                            3.5F,
                            () -> new LowPassProcessor(120.0F),
                            () -> new SoftClipProcessor(0.85F))
            )
    )),

    WOOFER(new SpeakerProfile(
            2.0F,
            34.0F,
            0.03F,
            0.65F,
            0.85F,
            List.of(
                    driver(1.8F, () -> new BandPassProcessor(70.0F, 700.0F))
            )
    )),

    MID_RANGE(new SpeakerProfile(
            1.4F,
            30.0F,
            0.045F,
            0.35F,
            0.45F,
            List.of(
                    driver(1.0F, () -> new BandPassProcessor(300.0F, 4500.0F))
            )
    )),

    TWEETER(new SpeakerProfile(
            1.15F,
            24.0F,
            0.06F,
            0.08F,
            0.18F,
            List.of(
                    driver(1.2F, () -> new HighPassProcessor(3500.0F))
            )
    )),

    BASS_REFLEX(new SpeakerProfile(
            1.8F,
            36.0F,
            0.024F,
            0.7F,
            0.95F,
            List.of(
                    driver(
                            1.8F,
                            () -> new BandPassProcessor(45.0F, 420.0F),
                            () -> new SoftClipProcessor(0.9F)),
                    driver(0.45F, () -> new HighPassProcessor(2500.0F))
            )
    )),

    HORN(new SpeakerProfile(
            1.8F,
            44.0F,
            0.025F,
            0.03F,
            0.12F,
            List.of(
                    driver(
                            1.5F,
                            () -> new BandPassProcessor(700.0F, 6500.0F),
                            () -> new SoftClipProcessor(0.7F))
            )
    )),

    BOOKSHELF(new SpeakerProfile(
            1.25F,
            32.0F,
            0.04F,
            0.25F,
            0.4F,
            List.of(
                    driver(1.1F, () -> new BandPassProcessor(70.0F, 2500.0F)),
                    driver(0.75F, () -> new HighPassProcessor(2500.0F))
            )
    )),

    FLOOR_STANDING(new SpeakerProfile(
            1.5F,
            38.0F,
            0.032F,
            0.35F,
            0.55F,
            List.of(
                    driver(1.3F, () -> new BandPassProcessor(50.0F, 500.0F)),
                    driver(1.0F, () -> new BandPassProcessor(500.0F, 4000.0F)),
                    driver(0.8F, () -> new HighPassProcessor(4000.0F))
            )
    ));

    private final SpeakerProfile profile;

    SpeakerType(SpeakerProfile profile) {
        this.profile = profile;
    }

    private static SpeakerDriverProfile driver(float gain, SpeakerProcessorFactory... processors) {
        return new SpeakerDriverProfile(gain, List.of(processors));
    }

    public SpeakerProfile profile() {
        return this.profile;
    }
}
