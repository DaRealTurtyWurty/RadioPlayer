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

public record ScreenAssemblyUpsertMessage(
        ResourceKey<Level> dimension,
        ScreenAssembly assembly
) implements CustomPacketPayload {
    public static final Type<ScreenAssemblyUpsertMessage> TYPE =
            new Type<>(MediaBox.id("screen_assembly_upsert"));
    public static final Codec<ScreenAssemblyUpsertMessage> DATA_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ScreenAssemblyUpsertMessage::dimension),
            ScreenAssembly.CODEC.fieldOf("assembly").forGetter(ScreenAssemblyUpsertMessage::assembly)
    ).apply(instance, ScreenAssemblyUpsertMessage::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScreenAssemblyUpsertMessage> CODEC =
            ByteBufCodecs.fromCodecWithRegistries(DATA_CODEC);

    public static void handle(Player player, ScreenAssemblyUpsertMessage message) {
        ClientScreenState.apply(message);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
