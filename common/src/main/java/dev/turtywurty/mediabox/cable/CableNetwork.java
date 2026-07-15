package dev.turtywurty.mediabox.cable;

import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * An immutable connected component of the cable graph.
 *
 * <p>Networks are created and maintained by {@link CableManager}. The identifier is stable while a
 * network grows and is retained by the largest remaining component when a cable removal splits the
 * network.</p>
 */
public record CableNetwork(UUID id, Set<PortEndpoint> ports, MediaSignalType signalType) {
    public CableNetwork(UUID id, Set<PortEndpoint> ports, MediaSignalType signalType) {
        this.id = Objects.requireNonNull(id, "id");
        this.signalType = Objects.requireNonNull(signalType, "signalType");
        this.ports = Set.copyOf(Objects.requireNonNull(ports, "ports"));

        if (this.ports.isEmpty())
            throw new IllegalArgumentException("A cable network must contain at least one port");
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

        if (!(object instanceof CableNetwork(UUID id1, Set<PortEndpoint> ports1, MediaSignalType type)))
            return false;

        return this.id.equals(id1)
                && this.ports.equals(ports1)
                && this.signalType == type;
    }

    @Override
    public @NonNull String toString() {
        return "CableNetwork[id=" + this.id
                + ", ports=" + this.ports
                + ", signalType=" + this.signalType + ']';
    }
}
