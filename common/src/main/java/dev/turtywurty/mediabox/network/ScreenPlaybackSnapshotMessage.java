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

import java.util.List;

public record ScreenPlaybackSnapshotMessage(
        ResourceKey<Level> dimension,
        List<ScreenPlaybackAssignment> assignments
) implements CustomPacketPayload {
    public static final Type<ScreenPlaybackSnapshotMessage> TYPE = new Type<>(MediaBox.id("screen_playback_snapshot"));

    public static final Codec<ScreenPlaybackSnapshotMessage> DATA_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Level.RESOURCE_KEY_CODEC
                            .fieldOf("dimension")
                            .forGetter(ScreenPlaybackSnapshotMessage::dimension),
                    ScreenPlaybackAssignment.CODEC.listOf()
                            .fieldOf("assignments")
                            .forGetter(ScreenPlaybackSnapshotMessage::assignments)
            ).apply(instance, ScreenPlaybackSnapshotMessage::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScreenPlaybackSnapshotMessage> CODEC =
            ByteBufCodecs.fromCodecWithRegistries(DATA_CODEC);

    public ScreenPlaybackSnapshotMessage {
        assignments = List.copyOf(assignments);
    }

    public static void handle(Player player, ScreenPlaybackSnapshotMessage message) {
        ClientScreenPlaybackState.apply(message);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}