package dev.turtywurty.mediaplayer;

import dev.turtywurty.mediaplayer.block.ModBlockEntities;
import dev.turtywurty.mediaplayer.block.ModBlocks;
import dev.turtywurty.mediaplayer.item.ModItems;
import dev.turtywurty.mediaplayer.network.UpdateRadioUrlMessage;
import net.blay09.mods.balm.Balm;
import net.blay09.mods.balm.core.BalmRegistrars;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaPlayer {
    public static final Logger LOGGER = LoggerFactory.getLogger(MediaPlayer.class);

    public static final String MOD_ID = "mediaplayer";
    public static final String VERSION = "1.0.0";
    public static final String MINECRAFT_VERSION = "26.2";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static MediaPlayerConfig config() {
        return Balm.config().getActiveConfig(MediaPlayerConfig.class);
    }

    public static void initialize(BalmRegistrars registrars) {
        Balm.config().registerConfig(MediaPlayerConfig.class);

        registrars.blocks(ModBlocks::initialize);
        registrars.items(ModItems::initialize);
        registrars.creativeModeTabs(ModItems::initialize);
        registrars.blockEntityTypes(ModBlockEntities::initialize);

        Balm.networking().registerServerboundPacket(
                UpdateRadioUrlMessage.TYPE,
                UpdateRadioUrlMessage.class,
                UpdateRadioUrlMessage.CODEC,
                UpdateRadioUrlMessage::handle);
    }
}
