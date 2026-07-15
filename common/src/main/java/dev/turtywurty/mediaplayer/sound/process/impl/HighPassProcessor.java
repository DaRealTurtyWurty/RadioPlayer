package dev.turtywurty.mediaplayer.sound.process.impl;

import dev.turtywurty.mediaplayer.sound.process.SpeakerProcessingContext;
import dev.turtywurty.mediaplayer.sound.process.SpeakerProcessor;

public final class HighPassProcessor implements SpeakerProcessor {
    private final float cutoffFrequency;
    private float previousInput;
    private float previousOutput;

    public HighPassProcessor(float cutoffFrequency) {
        this.cutoffFrequency = cutoffFrequency;
    }

    @Override
    public float process(float sample, SpeakerProcessingContext context) {
        float dt = 1.0f / context.sampleRate();
        float rc = 1.0f / (2 * (float) Math.PI * this.cutoffFrequency);
        float alpha = rc / (rc + dt);

        float output = alpha * (this.previousOutput + sample - this.previousInput);

        this.previousInput = sample;
        this.previousOutput = output;

        return output;
    }
}
