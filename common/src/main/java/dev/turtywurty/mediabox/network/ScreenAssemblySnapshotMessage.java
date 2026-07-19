package dev.turtywurty.mediabox.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.client.screen.ClientScreenState;
import dev.turtywurty.mediabox.screen.ScreenAssembly;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

public record ScreenAssemblySnapshotMessage(
        ResourceKey<Level> dimension,
        List<ScreenAssembly> assemblies
) implements CustomPacketPayload {
    public static final Type<ScreenAssemblySnapshotMessage> TYPE =
            new Type<>(MediaBox.id("screen_assembly_snapshot"));
    public static final Codec<ScreenAssemblySnapshotMessage> DATA_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ScreenAssemblySnapshotMessage::dimension),
            ScreenAssembly.CODEC.listOf().fieldOf("assemblies").forGetter(ScreenAssemblySnapshotMessage::assemblies)
    ).apply(instance, ScreenAssemblySnapshotMessage::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScreenAssemblySnapshotMessage> CODEC =
            ByteBufCodecs.fromCodecWithRegistries(DATA_CODEC);

    public ScreenAssemblySnapshotMessage {
        assemblies = List.copyOf(assemblies);
    }

    public static void handle(Player player, ScreenAssemblySnapshotMessage message) {
        ClientScreenState.apply(message);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
