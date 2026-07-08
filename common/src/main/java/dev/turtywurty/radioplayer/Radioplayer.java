package dev.turtywurty.radioplayer;

import dev.turtywurty.radioplayer.block.ModBlockEntities;
import dev.turtywurty.radioplayer.block.ModBlocks;
import dev.turtywurty.radioplayer.item.ModItems;
import dev.turtywurty.radioplayer.network.UpdateRadioUrlMessage;
import net.blay09.mods.balm.Balm;
import net.blay09.mods.balm.core.BalmRegistrars;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Radioplayer {
    public static final Logger logger = LoggerFactory.getLogger(Radioplayer.class);

    public static final String MOD_ID = "radioplayer";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static RadioplayerConfig config() {
        return Balm.config().getActiveConfig(RadioplayerConfig.class);
    }

    public static void initialize(BalmRegistrars registrars) {
        Balm.config().registerConfig(RadioplayerConfig.class);

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
