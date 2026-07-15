package dev.turtywurty.mediabox.sound.process.impl;

import dev.turtywurty.mediabox.sound.process.SpeakerProcessingContext;
import dev.turtywurty.mediabox.sound.process.SpeakerProcessor;

public final class BandPassProcessor implements SpeakerProcessor {
    private final LowPassProcessor lowPassProcessor;
    private final HighPassProcessor highPassProcessor;

    public BandPassProcessor(float lowCutoffFrequency, float highCutoffFrequency) {
        this.lowPassProcessor = new LowPassProcessor(highCutoffFrequency);
        this.highPassProcessor = new HighPassProcessor(lowCutoffFrequency);
    }

    @Override
    public float process(float sample, SpeakerProcessingContext context) {
        float lowPassedSample = this.lowPassProcessor.process(sample, context);
        return this.highPassProcessor.process(lowPassedSample, context);
    }
}
