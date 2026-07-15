package dev.turtywurty.mediabox.cable;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Optional;

/** Implemented by block entities that expose clickable cable ports. */
public interface MediaPortProvider {
    Collection<MediaPort> getMediaPorts();

    default Optional<MediaPort> getMediaPort(Identifier portId) {
        return getMediaPorts().stream()
                .filter(port -> port.id().equals(portId))
                .findFirst();
    }

    /**
     * Resolves a port from a block-face click. Providers with several ports on one face should
     * override this method and use {@code localHitLocation} to select the appropriate socket.
     */
    default Optional<MediaPort> getMediaPortAt(Direction face, Vec3 localHitLocation) {
        return getMediaPorts().stream()
                .filter(port -> port.face() == face)
                .findFirst();
    }
}
