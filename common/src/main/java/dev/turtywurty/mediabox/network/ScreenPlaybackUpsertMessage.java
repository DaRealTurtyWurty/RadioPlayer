package dev.turtywurty.mediabox.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.client.video.ClientScreenPlaybackState;
import dev.turtywurty.mediabox.video.ScreenPlaybackAssignment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

public record ScreenPlaybackUpsertMessage(
        ResourceKey<Level> dimension,
        ScreenPlaybackAssignment assignment
) implements CustomPacketPayload {
    public static final Type<ScreenPlaybackUpsertMessage> TYPE = new Type<>(MediaBox.id("screen_playback_upsert"));

    public static final Codec<ScreenPlaybackUpsertMessage> DATA_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Level.RESOURCE_KEY_CODEC
                            .fieldOf("dimension")
                            .forGetter(ScreenPlaybackUpsertMessage::dimension),
                    ScreenPlaybackAssignment.CODEC
                            .fieldOf("assignment")
                            .forGetter(ScreenPlaybackUpsertMessage::assignment)
            ).apply(instance, ScreenPlaybackUpsertMessage::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScreenPlaybackUpsertMessage> CODEC =
            ByteBufCodecs.fromCodecWithRegistries(DATA_CODEC);

    public static void handle(Player player, ScreenPlaybackUpsertMessage message) {
        ClientScreenPlaybackState.apply(message);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}