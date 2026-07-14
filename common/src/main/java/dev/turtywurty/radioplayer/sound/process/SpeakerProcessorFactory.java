package dev.turtywurty.radioplayer.sound.process;

@FunctionalInterface
public interface SpeakerProcessorFactory {
    SpeakerProcessor create();
}
