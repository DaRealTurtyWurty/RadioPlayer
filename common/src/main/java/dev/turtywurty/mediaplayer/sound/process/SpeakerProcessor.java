package dev.turtywurty.mediaplayer.sound.process;

public interface SpeakerProcessor {
    float process(float sample, SpeakerProcessingContext context);
}
