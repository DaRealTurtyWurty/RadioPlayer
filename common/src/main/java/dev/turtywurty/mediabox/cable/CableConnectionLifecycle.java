package dev.turtywurty.mediabox.cable;

import dev.turtywurty.mediabox.cable.concealed.ConcealedCableRun;
import dev.turtywurty.mediabox.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Pops visible cables when blocked and returns cable items when connections are removed. */
public final class CableConnectionLifecycle {
    private CableConnectionLifecycle() {
    }

    public static void onBlockChanged(ServerLevel level, BlockPos changedPos) {
        Optional<CableSavedData> existingData = CableSavedData.getIfPresent(level);
        if (existingData.isEmpty())
            return;

        CableSavedData data = existingData.get();
        int droppedItems = 0;
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

            if (data.removeVisibleCable(connection.id()).isPresent())
                droppedItems += connection.cableItems();
        }

        if (droppedItems > 0) {
            dropCableItems(level, Vec3.atCenterOf(changedPos), droppedItems);
            CableSync.broadcastSnapshot(level);
        }
    }

    public static void removePort(ServerLevel level, PortEndpoint endpoint, boolean removeConcealedRuns) {
        CableSavedData data = CableSavedData.get(level);
        int cableItems = 0;
        for (VisibleCableConnection connection : data.removeVisibleCablesAt(endpoint)) {
            cableItems += connection.cableItems();
        }

        if (removeConcealedRuns) {
            Set<ConcealedCableRun> runs = data.manager().concealedCableRunsAt(endpoint);
            for (ConcealedCableRun run : runs) {
                if (data.removeConcealedCableRun(run.id()).isPresent())
                    cableItems += run.cableItems();
            }
        }

        dropCableItems(level, Vec3.atCenterOf(endpoint.pos()), cableItems);
    }

    private static void dropCableItems(ServerLevel level, Vec3 position, int count) {
        if (count <= 0)
            return;

        Item cableItem = ModItems.audioCable.asItem();
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
