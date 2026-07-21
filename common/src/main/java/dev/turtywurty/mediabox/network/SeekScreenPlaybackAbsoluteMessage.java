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

public record SeekScreenPlaybackAbsoluteMessage(BlockPos pos, double seconds) implements CustomPacketPayload {
    public static final Type<SeekScreenPlaybackAbsoluteMessage> TYPE =
            new Type<>(MediaBox.id("seek_screen_playback_absolute"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SeekScreenPlaybackAbsoluteMessage> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    SeekScreenPlaybackAbsoluteMessage::pos,
                    ByteBufCodecs.DOUBLE,
                    SeekScreenPlaybackAbsoluteMessage::seconds,
                    SeekScreenPlaybackAbsoluteMessage::new
            );

    public static void handle(ServerPlayer player, SeekScreenPlaybackAbsoluteMessage message) {
        ServerLevel level = player.level();
        if (!Double.isFinite(message.seconds())
                || !ScreenPlaybackControlAccess.canControl(player, message.pos())
                || !(level.getBlockEntity(message.pos()) instanceof FlatScreenBlockEntity screen)
                || screen.getScreenId() == null)
            return;

        ScreenPlaybackSync.seekAbsolute(level, screen.getScreenId(), message.seconds());
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
