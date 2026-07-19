package dev.turtywurty.mediabox.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.cable.VisibleCableConnection;
import dev.turtywurty.mediabox.cable.concealed.ConcealedCableRun;
import dev.turtywurty.mediabox.client.cable.ClientCableState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

public record CableSnapshotMessage(
        ResourceKey<Level> dimension,
        List<VisibleCableConnection> visibleConnections,
        List<ConcealedCableRun> concealedRuns
) implements CustomPacketPayload {
    public static final Type<CableSnapshotMessage> TYPE = new Type<>(MediaBox.id("cable_snapshot"));
    public static final Codec<CableSnapshotMessage> DATA_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(CableSnapshotMessage::dimension),
            VisibleCableConnection.CODEC.listOf().fieldOf("visible_connections")
                    .forGetter(CableSnapshotMessage::visibleConnections),
            ConcealedCableRun.CODEC.listOf().fieldOf("concealed_runs")
                    .forGetter(CableSnapshotMessage::concealedRuns)
    ).apply(instance, CableSnapshotMessage::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, CableSnapshotMessage> CODEC =
            ByteBufCodecs.fromCodecWithRegistries(DATA_CODEC);

    public CableSnapshotMessage {
        visibleConnections = List.copyOf(visibleConnections);
        concealedRuns = List.copyOf(concealedRuns);
    }

    public static void handle(Player player, CableSnapshotMessage message) {
        ClientCableState.apply(message);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
