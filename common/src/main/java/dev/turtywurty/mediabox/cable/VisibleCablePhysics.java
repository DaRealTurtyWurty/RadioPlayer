package dev.turtywurty.mediabox.cable;

import dev.turtywurty.mediabox.cable.concealed.ConcealedCableRun;
import dev.turtywurty.mediabox.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

/** Event-driven lifecycle for server-authored visible cable routes. */
public final class VisibleCablePhysics {
    private VisibleCablePhysics() {
    }

    public static void onBlockChanged(ServerLevel level, BlockPos changedPos) {
        Optional<CableSavedData> existingData = CableSavedData.getIfPresent(level);
        if (existingData.isEmpty())
            return;

        CableSavedData data = existingData.get();
        if (data.manager().visibleCableCount() == 0)
            return;

        boolean changed = false;
        for (VisibleCableConnection connection : data.manager().visibleConnections().values()) {
            if (!isAffectedByBlock(connection, changedPos))
                continue;

            Optional<ResolvedMediaPort> first = MediaPortLookup.resolve(level, connection.first());
            Optional<ResolvedMediaPort> second = MediaPortLookup.resolve(level, connection.second());
            if (first.isEmpty() || second.isEmpty()) {
                data.removeVisibleCable(connection.id());
                dropCableItems(level, routeMidpoint(connection.route()), connection.cableItems());
                changed = true;
                continue;
            }

            double capacity = CableConstants.capacityForItems(connection.cableItems());
            Optional<VisibleCableRoute> tautRoute = VisibleCableRoutePlanner.findTautRoute(
                    level,
                    first.get(),
                    second.get(),
                    capacity);
            if (tautRoute.isEmpty()) {
                data.removeVisibleCable(connection.id());
                dropCableItems(level, routeMidpoint(connection.route()), connection.cableItems());
                changed = true;
                continue;
            }

            VisibleCableRoute route = VisibleCableRoutePlanner.addCollisionSafeSag(level, tautRoute.get(), capacity);
            VisibleCableConnection updated = new VisibleCableConnection(
                    connection.id(),
                    connection.first(),
                    connection.second(),
                    connection.signalType(),
                    connection.cableItems(),
                    route);
            if (!updated.equals(connection)) {
                data.updateVisibleCable(updated);
                changed = true;
            }
        }

        if (changed)
            CableSync.broadcastSnapshot(level);
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

    private static boolean isAffectedByBlock(VisibleCableConnection connection, BlockPos changedPos) {
        if (connection.first().pos().equals(changedPos) || connection.second().pos().equals(changedPos))
            return true;

        AABB changedBlock = new AABB(changedPos);
        var points = connection.route().points();
        for (int index = 1; index < points.size(); index++) {
            Vec3 first = points.get(index - 1);
            Vec3 second = points.get(index);
            AABB segmentBounds = new AABB(
                    Math.min(first.x, second.x),
                    Math.min(first.y, second.y),
                    Math.min(first.z, second.z),
                    Math.max(first.x, second.x),
                    Math.max(first.y, second.y),
                    Math.max(first.z, second.z))
                    .inflate(VisibleCableRoutePlanner.CABLE_RADIUS + 0.01);
            if (segmentBounds.intersects(changedBlock))
                return true;
        }
        return false;
    }

    private static Vec3 routeMidpoint(VisibleCableRoute route) {
        return route.points().get(route.points().size() / 2);
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
