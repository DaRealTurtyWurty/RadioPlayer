package dev.turtywurty.radioplayer.sound.process;

public interface SpeakerProcessor {
    float process(float sample, SpeakerProcessingContext context);
}
