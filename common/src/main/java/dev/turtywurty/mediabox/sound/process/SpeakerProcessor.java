package dev.turtywurty.mediabox.sound.process;

public interface SpeakerProcessor {
    float process(float sample, SpeakerProcessingContext context);
}
