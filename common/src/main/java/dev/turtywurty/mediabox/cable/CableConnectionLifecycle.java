package dev.turtywurty.mediabox.cable;

import dev.turtywurty.mediabox.cable.concealed.ConcealedCableRun;
import dev.turtywurty.mediabox.cable.concealed.ConcealedCableRouting;
import dev.turtywurty.mediabox.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Pops obstructed or exposed cables and returns cable items when connections are removed. */
public final class CableConnectionLifecycle {
    private CableConnectionLifecycle() {
    }

    public static void onBlockChanged(ServerLevel level, BlockPos changedPos) {
        Optional<CableSavedData> existingData = CableSavedData.getIfPresent(level);
        if (existingData.isEmpty())
            return;

        CableSavedData data = existingData.get();
        Map<MediaSignalType, Integer> droppedItems = new EnumMap<>(MediaSignalType.class);
        for (VisibleCableConnection connection : List.copyOf(data.manager().visibleConnections().values())) {
            Optional<ResolvedMediaPort> first = MediaPortLookup.resolve(level, connection.first());
            Optional<ResolvedMediaPort> second = MediaPortLookup.resolve(level, connection.second());
            if (first.isEmpty() || second.isEmpty())
                continue;

            VisibleCableRoute route = VisibleCableRouteFactory.create(
                    first.get(),
                    second.get(),
                    connection.cableItems());
            if (!VisibleCableCollision.intersectsBlock(
                    level,
                    route,
                    changedPos,
                    connection.first().pos(),
                    connection.second().pos()))
                continue;

            data.removeVisibleCable(connection.id()).ifPresent(removed ->
                    addCableItems(droppedItems, removed.signalType(), removed.cableItems()));
        }

        if (!ConcealedCableRouting.canRouteThrough(level, changedPos)) {
            for (ConcealedCableRun run : List.copyOf(data.manager().concealedCableRuns().values())) {
                if (!run.path().contains(changedPos))
                    continue;

                data.removeConcealedCableRun(run.id()).ifPresent(removed ->
                        addCableItems(droppedItems, removed.signalType(), removed.cableItems()));
            }
        }

        if (!droppedItems.isEmpty()) {
            dropCableItems(level, Vec3.atCenterOf(changedPos), droppedItems);
            CableSync.broadcastSnapshot(level);
        }
    }

    public static void removePort(ServerLevel level, PortEndpoint endpoint, boolean removeConcealedRuns) {
        CableSavedData data = CableSavedData.get(level);
        Map<MediaSignalType, Integer> cableItems = new EnumMap<>(MediaSignalType.class);
        for (VisibleCableConnection connection : data.removeVisibleCablesAt(endpoint)) {
            addCableItems(cableItems, connection.signalType(), connection.cableItems());
        }

        if (removeConcealedRuns) {
            Set<ConcealedCableRun> runs = data.manager().concealedCableRunsAt(endpoint);
            for (ConcealedCableRun run : runs) {
                data.removeConcealedCableRun(run.id()).ifPresent(removed ->
                        addCableItems(cableItems, removed.signalType(), removed.cableItems()));
            }
        }

        dropCableItems(level, Vec3.atCenterOf(endpoint.pos()), cableItems);
    }

    private static void addCableItems(
            Map<MediaSignalType, Integer> cableItems,
            MediaSignalType signalType,
            int count) {
        cableItems.merge(signalType, count, Math::addExact);
    }

    private static void dropCableItems(
            ServerLevel level,
            Vec3 position,
            Map<MediaSignalType, Integer> cableItems) {
        for (var entry : cableItems.entrySet()) {
            dropCableItems(level, position, entry.getKey(), entry.getValue());
        }
    }

    private static void dropCableItems(
            ServerLevel level,
            Vec3 position,
            MediaSignalType signalType,
            int count) {
        if (count <= 0)
            return;

        Item cableItem = ModItems.cableItem(signalType);
        int maxStackSize = new ItemStack(cableItem).getMaxStackSize();
        int remaining = count;
        BlockPos dropPos = BlockPos.containing(position.x, position.y, position.z);
        while (remaining > 0) {
            int batch = Math.min(remaining, maxStackSize);
            Block.popResource(level, dropPos, new ItemStack(cableItem, batch));
            remaining -= batch;
        }
    }
}
