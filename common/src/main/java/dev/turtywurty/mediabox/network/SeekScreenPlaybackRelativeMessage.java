package dev.turtywurty.mediabox.network;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.block.entity.FlatScreenBlockEntity;
import dev.turtywurty.mediabox.video.ScreenPlaybackSync;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

public record SeekScreenPlaybackRelativeMessage(BlockPos pos, double seconds) implements CustomPacketPayload {
    public static final Type<SeekScreenPlaybackRelativeMessage> TYPE =
            new Type<>(MediaBox.id("seek_screen_playback_relative"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SeekScreenPlaybackRelativeMessage> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    SeekScreenPlaybackRelativeMessage::pos,
                    ByteBufCodecs.DOUBLE,
                    SeekScreenPlaybackRelativeMessage::seconds,
                    SeekScreenPlaybackRelativeMessage::new
            );

    public static void handle(ServerPlayer player, SeekScreenPlaybackRelativeMessage message) {
        ServerLevel level = player.level();
        if (!Double.isFinite(message.seconds())
                || !ScreenPlaybackControlAccess.canControl(player, message.pos())
                || !(level.getBlockEntity(message.pos()) instanceof FlatScreenBlockEntity screen)
                || screen.getScreenId() == null)
            return;

        ScreenPlaybackSync.seekRelative(level, screen.getScreenId(), message.seconds());
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
