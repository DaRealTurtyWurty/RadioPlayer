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

import java.net.URI;

public record SetScreenVideoUrlMessage(
        BlockPos pos,
        String url
) implements CustomPacketPayload {
    public static final Type<SetScreenVideoUrlMessage> TYPE = new Type<>(MediaBox.id("set_screen_video_url"));

    private static final int MAX_URL_LENGTH = 2048;

    public static final StreamCodec<RegistryFriendlyByteBuf, SetScreenVideoUrlMessage> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            SetScreenVideoUrlMessage::pos,
            ByteBufCodecs.stringUtf8(MAX_URL_LENGTH),
            SetScreenVideoUrlMessage::url,
            SetScreenVideoUrlMessage::new
    );

    public static void handle(ServerPlayer player, SetScreenVideoUrlMessage message) {
        ServerLevel level = player.level();

        if (!player.blockPosition().closerThan(message.pos(), 8.0)
                || !(level.getBlockEntity(message.pos()) instanceof FlatScreenBlockEntity screen)
                || screen.getScreenId() == null)
            return;

        String url = message.url().trim();
        if (!isAllowedUrl(url)) {
            MediaBox.LOGGER.warn("{} tried to set an invalid screen URL", player.getName().getString());
            return;
        }

        ScreenPlaybackSync.playRemoteUrl(level, screen.getScreenId(), url);
    }

    private static boolean isAllowedUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();

            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}