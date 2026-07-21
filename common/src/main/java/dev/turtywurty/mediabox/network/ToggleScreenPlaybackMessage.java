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

public record ToggleScreenPlaybackMessage(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ToggleScreenPlaybackMessage> TYPE =
            new Type<>(MediaBox.id("toggle_screen_playback"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleScreenPlaybackMessage> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    ToggleScreenPlaybackMessage::pos,
                    ToggleScreenPlaybackMessage::new
            );

    public static void handle(ServerPlayer player, ToggleScreenPlaybackMessage message) {
        ServerLevel level = player.level();
        if (!ScreenPlaybackControlAccess.canControl(player, message.pos())
                || !(level.getBlockEntity(message.pos()) instanceof FlatScreenBlockEntity screen)
                || screen.getScreenId() == null)
            return;

        ScreenPlaybackSync.togglePaused(level, screen.getScreenId());
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
