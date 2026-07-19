package dev.turtywurty.mediabox;

import dev.turtywurty.mediabox.block.ModBlockEntities;
import dev.turtywurty.mediabox.block.ModBlocks;
import dev.turtywurty.mediabox.ffmpeg.FfmpegNatives;
import dev.turtywurty.mediabox.item.ModItems;
import dev.turtywurty.mediabox.network.UpdateRadioUrlMessage;
import dev.turtywurty.mediabox.network.CableSnapshotMessage;
import dev.turtywurty.mediabox.cable.CableSync;
import net.blay09.mods.balm.Balm;
import net.blay09.mods.balm.core.BalmRegistrars;
import net.blay09.mods.balm.platform.event.callback.ServerLifecycleCallback;
import net.blay09.mods.balm.platform.event.callback.ServerPlayerCallback;
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

    public static void initialize(BalmRegistrars registrars) {
        ServerLifecycleCallback.Starting.EVENT.register(server -> FfmpegNatives.extract(server.getServerDirectory()));

        registrars.blocks(ModBlocks::initialize);
        registrars.items(ModItems::initialize);
        registrars.creativeModeTabs(ModItems::initialize);
        registrars.blockEntityTypes(ModBlockEntities::initialize);

        Balm.networking().registerServerboundPacket(
                UpdateRadioUrlMessage.TYPE,
                UpdateRadioUrlMessage.class,
                UpdateRadioUrlMessage.CODEC,
                UpdateRadioUrlMessage::handle);

        Balm.networking().registerClientboundPacket(
                CableSnapshotMessage.TYPE,
                CableSnapshotMessage.class,
                CableSnapshotMessage.CODEC,
                CableSnapshotMessage::handle);

        ServerPlayerCallback.Join.EVENT.register(player -> CableSync.sendSnapshot(player, player.level()));
        ServerPlayerCallback.Respawn.EVENT.register((oldPlayer, newPlayer) ->
                CableSync.sendSnapshot(newPlayer, newPlayer.level()));
        ServerPlayerCallback.DimensionChange.EVENT.register((player, from, to) -> {
            var level = player.level().getServer().getLevel(to);
            if (level != null)
                CableSync.sendSnapshot(player, level);
        });
    }
}
