package dev.turtywurty.mediabox.client.screen;

import dev.turtywurty.mediabox.network.ScreenAssemblyRemovalMessage;
import dev.turtywurty.mediabox.network.ScreenAssemblySnapshotMessage;
import dev.turtywurty.mediabox.network.ScreenAssemblyUpsertMessage;
import dev.turtywurty.mediabox.screen.ScreenAssembly;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ClientScreenState {
    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    private ClientScreenState() {
    }

    public static void apply(ScreenAssemblySnapshotMessage message) {
        Map<UUID, ScreenAssembly> assemblies = new HashMap<>();
        for (ScreenAssembly assembly : message.assemblies()) {
            assemblies.put(assembly.id(), assembly);
        }
        snapshot = new Snapshot(message.dimension(), assemblies);
    }

    public static void apply(ScreenAssemblyUpsertMessage message) {
        Snapshot current = snapshot;
        if (current.dimension() != null && !current.dimension().equals(message.dimension()))
            return;

        Map<UUID, ScreenAssembly> assemblies = new HashMap<>(current.assemblies());
        assemblies.put(message.assembly().id(), message.assembly());
        snapshot = new Snapshot(message.dimension(), assemblies);
    }

    public static void apply(ScreenAssemblyRemovalMessage message) {
        Snapshot current = snapshot;
        if (current.dimension() == null || !current.dimension().equals(message.dimension()))
            return;

        Map<UUID, ScreenAssembly> assemblies = new HashMap<>(current.assemblies());
        assemblies.remove(message.screenId());
        snapshot = new Snapshot(current.dimension(), assemblies);
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    public static Optional<ScreenAssembly> get(UUID id) {
        return Optional.ofNullable(snapshot.assemblies().get(id));
    }

    public static void clear() {
        snapshot = Snapshot.EMPTY;
    }

    public record Snapshot(ResourceKey<Level> dimension, Map<UUID, ScreenAssembly> assemblies) {
        private static final Snapshot EMPTY = new Snapshot(null, Map.of());

        public Snapshot {
            assemblies = Map.copyOf(assemblies);
        }
    }
}
