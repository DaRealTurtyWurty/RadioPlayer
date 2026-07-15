package dev.turtywurty.mediabox.cable.concealed;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.turtywurty.mediabox.cable.MediaSignalType;
import dev.turtywurty.mediabox.cable.PortEndpoint;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Server-owned concealed cable topology. Clients derive render geometry from these terminals.
 */
public record ConcealedCableRun(
        UUID id,
        MediaSignalType signalType,
        Set<PortEndpoint> terminals
) {
    public static final Codec<ConcealedCableRun> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(ConcealedCableRun::id),
            MediaSignalType.CODEC.fieldOf("signal_type").forGetter(ConcealedCableRun::signalType),
            PortEndpoint.CODEC.listOf().fieldOf("terminals")
                    .xmap(Set::copyOf, ArrayList::new)
                    .forGetter(ConcealedCableRun::terminals)
    ).apply(instance, ConcealedCableRun::new));

    public ConcealedCableRun {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(signalType, "signalType");
        terminals = Set.copyOf(Objects.requireNonNull(terminals, "terminals"));

        if (terminals.size() != 2)
            throw new IllegalArgumentException("A concealed cable run needs exactly two terminals");
    }
}
