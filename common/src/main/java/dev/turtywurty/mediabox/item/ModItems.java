package dev.turtywurty.mediabox.item;

import dev.turtywurty.mediabox.cable.MediaSignalType;
import net.blay09.mods.balm.world.item.BalmCreativeModeTabRegistrar;
import net.blay09.mods.balm.world.item.BalmItemRegistrar;
import net.blay09.mods.balm.world.item.DeferredItem;
import net.minecraft.world.item.Item;

public class ModItems {
    public static DeferredItem audioCable;
    public static DeferredItem videoCable;

    public static void initialize(BalmItemRegistrar items) {
        audioCable = items.register("audio_cable", AudioCableItem::new)
                .asDeferredItem();
        videoCable = items.register("video_cable", VideoCableItem::new)
                .asDeferredItem();
    }

    public static void initialize(BalmCreativeModeTabRegistrar creativeModeTabs) {

    }

    public static Item cableItem(MediaSignalType signalType) {
        return switch (signalType) {
            case AUDIO -> audioCable.asItem();
            case VIDEO -> videoCable.asItem();
        };
    }
}
