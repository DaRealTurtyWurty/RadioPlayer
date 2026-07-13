package dev.turtywurty.radioplayer.sound.process.impl;

import dev.turtywurty.radioplayer.sound.process.SpeakerProcessingContext;
import dev.turtywurty.radioplayer.sound.process.SpeakerProcessor;

public final class SoftClipProcessor implements SpeakerProcessor {
    private final float threshold;

    public SoftClipProcessor(float threshold) {
        this.threshold = threshold;
    }

    @Override
    public float process(float sample, SpeakerProcessingContext context) {
        if (sample > this.threshold) {
            return this.threshold + (sample - this.threshold) / (1 + (sample - this.threshold) * (sample - this.threshold));
        } else if (sample < -this.threshold) {
            return -this.threshold + (sample + this.threshold) / (1 + (sample + this.threshold) * (sample + this.threshold));
        } else {
            return sample;
        }
    }
}
