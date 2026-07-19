package dev.turtywurty.mediabox.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.client.video.ClientScreenPlaybackState;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record ScreenPlaybackRemovalMessage(
        ResourceKey<Level> dimension,
        UUID screenId
) implements CustomPacketPayload {
    public static final Type<ScreenPlaybackRemovalMessage> TYPE =
            new Type<>(MediaBox.id("screen_playback_removal"));

    public static final Codec<ScreenPlaybackRemovalMessage> DATA_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Level.RESOURCE_KEY_CODEC
                            .fieldOf("dimension")
                            .forGetter(ScreenPlaybackRemovalMessage::dimension),
                    UUIDUtil.CODEC
                            .fieldOf("screen_id")
                            .forGetter(ScreenPlaybackRemovalMessage::screenId)
            ).apply(instance, ScreenPlaybackRemovalMessage::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScreenPlaybackRemovalMessage> CODEC =
            ByteBufCodecs.fromCodecWithRegistries(DATA_CODEC);

    public static void handle(Player player, ScreenPlaybackRemovalMessage message) {
        ClientScreenPlaybackState.apply(message);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
