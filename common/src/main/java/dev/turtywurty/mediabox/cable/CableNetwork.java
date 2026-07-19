package dev.turtywurty.mediabox.cable;

import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * An immutable connected component of the cable graph.
 *
 * <p>Networks are created and maintained by {@link CableManager}. The identifier is stable while a
 * network grows and is retained by the largest remaining component when a cable removal splits the
 * network.</p>
 */
public record CableNetwork(
        UUID id,
        Set<PortEndpoint> ports,
        MediaSignalType signalType,
        Optional<PortEndpoint> sourceEndpoint
) {
    public CableNetwork(
            UUID id,
            Set<PortEndpoint> ports,
            MediaSignalType signalType,
            Optional<PortEndpoint> sourceEndpoint) {
        this.id = Objects.requireNonNull(id, "id");
        this.signalType = Objects.requireNonNull(signalType, "signalType");
        this.ports = Set.copyOf(Objects.requireNonNull(ports, "ports"));
        this.sourceEndpoint = Objects.requireNonNull(sourceEndpoint, "sourceEndpoint");

        if (this.ports.isEmpty())
            throw new IllegalArgumentException("A cable network must contain at least one port");
        if (this.sourceEndpoint.isPresent() && !this.ports.contains(this.sourceEndpoint.get()))
            throw new IllegalArgumentException("A cable network source must be one of its ports");
    }

    public boolean contains(PortEndpoint port) {
        return this.ports.contains(port);
    }

    public int size() {
        return this.ports.size();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;

        if (!(object instanceof CableNetwork(
                UUID id1,
                Set<PortEndpoint> ports1,
                MediaSignalType type,
                Optional<PortEndpoint> source1)))
            return false;

        return this.id.equals(id1)
                && this.ports.equals(ports1)
                && this.signalType == type
                && this.sourceEndpoint.equals(source1);
    }

    @Override
    public @NonNull String toString() {
        return "CableNetwork[id=" + this.id
                + ", ports=" + this.ports
                + ", signalType=" + this.signalType
                + ", sourceEndpoint=" + this.sourceEndpoint + ']';
    }
}
