package dev.turtywurty.mediaplayer.sound.process.impl;

import dev.turtywurty.mediaplayer.sound.process.SpeakerProcessingContext;
import dev.turtywurty.mediaplayer.sound.process.SpeakerProcessor;

public final class GainProcessor implements SpeakerProcessor {
    private final float gain;

    public GainProcessor(float gain) {
        this.gain = gain;
    }

    @Override
    public float process(float sample, SpeakerProcessingContext context) {
        return sample * this.gain;
    }
}
