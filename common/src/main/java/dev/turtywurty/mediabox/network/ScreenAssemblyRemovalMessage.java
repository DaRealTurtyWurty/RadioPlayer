package dev.turtywurty.mediabox.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.client.screen.ClientScreenState;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

public record ScreenAssemblyRemovalMessage(
        ResourceKey<Level> dimension,
        UUID screenId
) implements CustomPacketPayload {
    public static final Type<ScreenAssemblyRemovalMessage> TYPE =
            new Type<>(MediaBox.id("screen_assembly_removal"));
    public static final Codec<ScreenAssemblyRemovalMessage> DATA_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ScreenAssemblyRemovalMessage::dimension),
            UUIDUtil.CODEC.fieldOf("screen_id").forGetter(ScreenAssemblyRemovalMessage::screenId)
    ).apply(instance, ScreenAssemblyRemovalMessage::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScreenAssemblyRemovalMessage> CODEC =
            ByteBufCodecs.fromCodecWithRegistries(DATA_CODEC);

    public static void handle(Player player, ScreenAssemblyRemovalMessage message) {
        ClientScreenState.apply(message);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
