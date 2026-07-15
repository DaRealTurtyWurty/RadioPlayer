package dev.turtywurty.mediaplayer.sound.process.impl;

import dev.turtywurty.mediaplayer.sound.process.SpeakerProcessingContext;
import dev.turtywurty.mediaplayer.sound.process.SpeakerProcessor;
import net.minecraft.util.Mth;

public final class LowPassProcessor implements SpeakerProcessor {
    private final float cutoffFrequency;
    private float previousSample;

    public LowPassProcessor(float cutoffFrequency) {
        this.cutoffFrequency = cutoffFrequency;
    }

    @Override
    public float process(float sample, SpeakerProcessingContext context) {
        float dt = 1.0f / context.sampleRate();
        float rc = 1.0f / (Mth.TWO_PI * this.cutoffFrequency);
        float alpha = dt / (rc + dt);

        this.previousSample += alpha * (sample - this.previousSample);
        return this.previousSample;
    }
}
