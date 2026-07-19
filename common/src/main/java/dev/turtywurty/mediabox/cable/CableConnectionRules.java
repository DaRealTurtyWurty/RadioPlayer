package dev.turtywurty.mediabox.cable;

/** Shared rules for deciding whether two media-port roles may be connected. */
public final class CableConnectionRules {
    private CableConnectionRules() {
    }

    public static boolean directionsAreCompatible(MediaPort first, MediaPort second) {
        PortDirection firstDirection = first.direction();
        PortDirection secondDirection = second.direction();
        if (firstDirection == PortDirection.INPUT && secondDirection == PortDirection.INPUT)
            return false;
        if (firstDirection == PortDirection.OUTPUT && secondDirection == PortDirection.OUTPUT)
            return false;
        return true;
    }

    public static void validateDirections(MediaPort first, MediaPort second) {
        if (first.direction() == PortDirection.INPUT && second.direction() == PortDirection.INPUT)
            throw new IllegalArgumentException("Two input ports cannot be connected");
        if (first.direction() == PortDirection.OUTPUT && second.direction() == PortDirection.OUTPUT)
            throw new IllegalArgumentException("Two output ports cannot be connected");
    }

    public static boolean hasCapacity(MediaPort port, int existingConnections) {
        return port.canAcceptConnection(existingConnections);
    }

    public static void validateCapacity(MediaPort port, int existingConnections) {
        if (!hasCapacity(port, existingConnections))
            throw new IllegalArgumentException("That port has reached its connection limit");
    }
}
