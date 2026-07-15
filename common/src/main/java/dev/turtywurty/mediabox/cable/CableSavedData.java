package dev.turtywurty.mediabox.cable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.cable.concealed.ConcealedCableRun;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

/** Persistent, dimension-scoped ownership for cable topology. */
public final class CableSavedData extends SavedData {
    public static final Codec<CableSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            VisibleCableConnection.CODEC.listOf().optionalFieldOf("visible_connections", List.of())
                    .forGetter(data -> List.copyOf(data.manager.visibleConnections().values())),
            ConcealedCableRun.CODEC.listOf().optionalFieldOf("concealed_runs", List.of())
                    .forGetter(data -> List.copyOf(data.manager.concealedCableRuns().values()))
    ).apply(instance, CableSavedData::new));

    public static final SavedDataType<CableSavedData> TYPE = new SavedDataType<>(
            MediaBox.id("cable_networks"),
            CableSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final CableManager manager = new CableManager();

    public CableSavedData() {
    }

    private CableSavedData(
            List<VisibleCableConnection> visibleConnections,
            List<ConcealedCableRun> concealedRuns) {
        concealedRuns.forEach(this.manager::addConcealedCableRun);
        visibleConnections.forEach(this.manager::addVisibleCable);
    }

    public static CableSavedData get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public CableManager manager() {
        return this.manager;
    }

    public void addVisibleCable(VisibleCableConnection connection) {
        this.manager.addVisibleCable(connection);
        setDirty();
    }

    public Optional<VisibleCableConnection> removeVisibleCable(UUID connectionId) {
        Optional<VisibleCableConnection> removed = this.manager.removeVisibleCable(connectionId);
        if (removed.isPresent())
            setDirty();
        return removed;
    }

    public Set<VisibleCableConnection> removeVisibleCablesAt(PortEndpoint endpoint) {
        Set<VisibleCableConnection> connections = this.manager.visibleCablesAt(endpoint);
        for (VisibleCableConnection connection : connections) {
            this.manager.removeVisibleCable(connection.id());
        }

        if (!connections.isEmpty())
            setDirty();
        return connections;
    }

    public void removePort(PortEndpoint endpoint, boolean removeConcealedRun) {
        boolean changed = !removeVisibleCablesAt(endpoint).isEmpty();
        if (removeConcealedRun) {
            Set<ConcealedCableRun> runs = this.manager.concealedCableRunsAt(endpoint);
            for (ConcealedCableRun run : runs) {
                removeConcealedCableRun(run.id());
                changed = true;
            }
        }

        if (changed)
            setDirty();
    }

    public void addConcealedCableRun(ConcealedCableRun run) {
        this.manager.addConcealedCableRun(run);
        setDirty();
    }

    public void updateConcealedCableRun(ConcealedCableRun run) {
        this.manager.updateConcealedCableRun(run);
        setDirty();
    }

    public Optional<ConcealedCableRun> removeConcealedCableRun(UUID runId) {
        Optional<ConcealedCableRun> removed = this.manager.removeConcealedCableRun(runId);
        if (removed.isPresent())
            setDirty();
        return removed;
    }
}
