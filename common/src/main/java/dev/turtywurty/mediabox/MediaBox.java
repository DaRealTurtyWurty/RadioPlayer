package dev.turtywurty.mediabox;

import dev.turtywurty.mediabox.block.ModBlockEntities;
import dev.turtywurty.mediabox.block.ModBlocks;
import dev.turtywurty.mediabox.item.ModItems;
import dev.turtywurty.mediabox.network.UpdateRadioUrlMessage;
import net.blay09.mods.balm.Balm;
import net.blay09.mods.balm.core.BalmRegistrars;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaBox {
    public static final Logger LOGGER = LoggerFactory.getLogger(MediaBox.class);

    public static final String MOD_ID = "mediabox";
    public static final String VERSION = "1.0.0";
    public static final String MINECRAFT_VERSION = "26.2";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static MediaBoxConfig config() {
        return Balm.config().getActiveConfig(MediaBoxConfig.class);
    }

    public static void initialize(BalmRegistrars registrars) {
        Balm.config().registerConfig(MediaBoxConfig.class);

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
