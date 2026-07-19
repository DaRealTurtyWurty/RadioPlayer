package dev.turtywurty.mediabox.item;

import dev.turtywurty.mediabox.cable.MediaSignalType;

public class AudioCableItem extends CableItem {
    public AudioCableItem(Properties properties) {
        super(properties, MediaSignalType.AUDIO);
    }
}
