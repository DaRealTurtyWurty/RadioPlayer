package dev.turtywurty.mediabox.cable.concealed;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.turtywurty.mediabox.cable.CableConstants;
import dev.turtywurty.mediabox.cable.MediaSignalType;
import dev.turtywurty.mediabox.cable.PortEndpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Server-owned concealed cable topology. Clients derive render geometry from these terminals.
 */
public record ConcealedCableRun(
        UUID id,
        MediaSignalType signalType,
        Set<PortEndpoint> terminals,
        List<BlockPos> path,
        int cableItems
) {
    public static final Codec<ConcealedCableRun> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(ConcealedCableRun::id),
            MediaSignalType.CODEC.fieldOf("signal_type").forGetter(ConcealedCableRun::signalType),
            PortEndpoint.CODEC.listOf().fieldOf("terminals")
                    .xmap(Set::copyOf, ArrayList::new)
                    .forGetter(ConcealedCableRun::terminals),
            BlockPos.CODEC.listOf().optionalFieldOf("path", List.of()).forGetter(ConcealedCableRun::path),
            Codec.INT.fieldOf("cable_items").forGetter(ConcealedCableRun::cableItems)
    ).apply(instance, ConcealedCableRun::new));

    public ConcealedCableRun {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(signalType, "signalType");
        terminals = Set.copyOf(Objects.requireNonNull(terminals, "terminals"));
        path = Objects.requireNonNull(path, "path").stream()
                .map(position -> Objects.requireNonNull(position, "path position").immutable())
                .toList();

        if (terminals.size() != 2)
            throw new IllegalArgumentException("A concealed cable run needs exactly two terminals");
        if (cableItems < 1)
            throw new IllegalArgumentException("A concealed cable run needs at least one cable item");
        if (path.size() - 1 > CableConstants.capacityForItems(cableItems))
            throw new IllegalArgumentException("A concealed cable path exceeds its purchased cable length");
        for (int index = 1; index < path.size(); index++) {
            if (ConcealedCableRouting.manhattanDistance(path.get(index - 1), path.get(index)) != 1)
                throw new IllegalArgumentException("Concealed cable path positions must be face-adjacent");
        }
    }
}
