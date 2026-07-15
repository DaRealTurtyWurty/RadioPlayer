package dev.turtywurty.mediabox.cable;

import java.util.Objects;

public record ResolvedMediaPort(PortEndpoint endpoint, MediaPort port) {
    public ResolvedMediaPort {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(port, "port");
    }
}
