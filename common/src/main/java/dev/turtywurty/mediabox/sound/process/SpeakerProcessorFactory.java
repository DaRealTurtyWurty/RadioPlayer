package dev.turtywurty.mediabox.sound.process;

@FunctionalInterface
public interface SpeakerProcessorFactory {
    SpeakerProcessor create();
}
