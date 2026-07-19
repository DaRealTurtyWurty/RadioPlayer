package dev.turtywurty.mediabox.item;

import dev.turtywurty.mediabox.cable.MediaSignalType;

public class VideoCableItem extends CableItem {
    public VideoCableItem(Properties properties) {
        super(properties, MediaSignalType.VIDEO);
    }
}
