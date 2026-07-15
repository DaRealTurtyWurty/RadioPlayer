package dev.turtywurty.mediaplayer.sound.process;

@FunctionalInterface
public interface SpeakerProcessorFactory {
    SpeakerProcessor create();
}
