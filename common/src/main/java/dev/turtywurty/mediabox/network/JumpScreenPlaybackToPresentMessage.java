package dev.turtywurty.mediabox.network;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.block.entity.FlatScreenBlockEntity;
import dev.turtywurty.mediabox.video.ScreenPlaybackSync;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

public record JumpScreenPlaybackToPresentMessage(BlockPos pos) implements CustomPacketPayload {
    public static final Type<JumpScreenPlaybackToPresentMessage> TYPE =
            new Type<>(MediaBox.id("jump_screen_playback_to_present"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JumpScreenPlaybackToPresentMessage> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    JumpScreenPlaybackToPresentMessage::pos,
                    JumpScreenPlaybackToPresentMessage::new
            );

    public static void handle(ServerPlayer player, JumpScreenPlaybackToPresentMessage message) {
        ServerLevel level = player.level();
        if (!ScreenPlaybackControlAccess.canControl(player, message.pos())
                || !(level.getBlockEntity(message.pos()) instanceof FlatScreenBlockEntity screen)
                || screen.getScreenId() == null)
            return;

        ScreenPlaybackSync.jumpToPresent(level, screen.getScreenId());
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
