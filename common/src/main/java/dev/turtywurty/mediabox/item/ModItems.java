package dev.turtywurty.mediabox.item;

import net.blay09.mods.balm.world.item.BalmCreativeModeTabRegistrar;
import net.blay09.mods.balm.world.item.BalmItemRegistrar;
import net.blay09.mods.balm.world.item.DeferredItem;
import net.minecraft.world.item.Item;

public class ModItems {
    public static DeferredItem audioCable;

    public static void initialize(BalmItemRegistrar items) {
        audioCable = items.register("audio_cable", AudioCableItem::new)
                .asDeferredItem();
    }

    public static void initialize(BalmCreativeModeTabRegistrar creativeModeTabs) {

    }
}
