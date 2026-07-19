package dev.turtywurty.mediabox.cable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.Objects;

public record PortEndpoint(
        ResourceKey<Level> dimension,
        BlockPos pos,
        Identifier portId
) {
    public static final Comparator<PortEndpoint> CANONICAL_ORDER = Comparator
            .comparing((PortEndpoint endpoint) -> endpoint.dimension().identifier().toString())
            .thenComparingLong(endpoint -> endpoint.pos().asLong())
            .thenComparing(endpoint -> endpoint.portId().toString());

    public static final Codec<PortEndpoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(PortEndpoint::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(PortEndpoint::pos),
            Identifier.CODEC.fieldOf("port_id").forGetter(PortEndpoint::portId)
    ).apply(instance, PortEndpoint::new));

    public PortEndpoint {
        Objects.requireNonNull(dimension, "dimension");
        pos = Objects.requireNonNull(pos, "pos").immutable();
        Objects.requireNonNull(portId, "portId");
    }

    public static PortEndpoint of(Level level, BlockPos pos, MediaPort port) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(port, "port");
        return new PortEndpoint(level.dimension(), pos, port.id());
    }
}
