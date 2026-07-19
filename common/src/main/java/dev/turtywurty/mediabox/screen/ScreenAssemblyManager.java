package dev.turtywurty.mediabox.screen;

import dev.turtywurty.mediabox.block.FlatScreenBlock;
import dev.turtywurty.mediabox.block.entity.FlatScreenBlockEntity;
import dev.turtywurty.mediabox.video.ScreenPlaybackSync;
import dev.turtywurty.mediabox.video.VideoSessionState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.*;

public final class ScreenAssemblyManager {
    private ScreenAssemblyManager() {
    }

    public static void rebuildFrom(ServerLevel level, BlockPos seed) {
        BlockState state = level.getBlockState(seed);
        if (!(state.getBlock() instanceof FlatScreenBlock))
            return;

        rebuildComponents(level, List.of(seed.immutable()), state.getValue(FlatScreenBlock.FACING), null);
    }

    public static void rebuildAfterRemoval(ServerLevel level, BlockPos removedPos, Direction oldFacing) {
        if (!oldFacing.getAxis().isHorizontal())
            return;

        Direction right = screenRight(oldFacing);
        rebuildComponents(level, List.of(
                removedPos.above(),
                removedPos.below(),
                removedPos.relative(right),
                removedPos.relative(right.getOpposite())
        ), oldFacing, removedPos);
    }

    public static void ensureRegistered(ServerLevel level, ScreenAssembly assembly) {
        ScreenSavedData data = ScreenSavedData.get(level);
        if (data.upsert(assembly)) {
            ScreenSync.broadcastUpsert(level, assembly);
        }
    }

    private static void rebuildComponents(
            ServerLevel level,
            List<BlockPos> seeds,
            Direction facing,
            @Nullable BlockPos removedPos) {
        Set<BlockPos> discovered = new HashSet<>();
        List<Component> components = new ArrayList<>();
        for (BlockPos seed : seeds) {
            if (discovered.contains(seed) || !isCompatiblePanel(level, seed, facing))
                continue;

            Component component = collectComponent(level, seed, facing, discovered);
            if (!component.members().isEmpty()) {
                components.add(component);
            }
        }

        // a component that still contains its previous controller gets first claim
        // on its old ID. if the controller was removed, the largest surviving piece
        // wins, with screen-local controller position as a deterministic tie-breaker.
        components.sort((first, second) -> {
            int controllerComparison = Boolean.compare(
                    second.containsPreviousController(),
                    first.containsPreviousController());
            if (controllerComparison != 0)
                return controllerComparison;

            int sizeComparison = Integer.compare(second.members().size(), first.members().size());
            if (sizeComparison != 0)
                return sizeComparison;

            return comparePanelPositions(first.controllerPos(), second.controllerPos(), facing);
        });

        Set<UUID> previousIds = new HashSet<>();
        for (Component component : components) {
            previousIds.addAll(component.previousAssemblies().keySet());
        }

        ScreenSavedData data = ScreenSavedData.get(level);
        if (components.isEmpty() && removedPos != null) {
            data.findRemovedSingleton(removedPos, facing)
                    .map(ScreenAssembly::id)
                    .ifPresent(previousIds::add);
        }

        Set<UUID> claimedIds = new HashSet<>();
        List<ScreenAssembly> rebuiltAssemblies = new ArrayList<>();
        for (Component component : components) {
            PreviousAssembly previous = choosePreviousAssembly(component, claimedIds);
            UUID id = previous == null ? UUID.randomUUID() : previous.id();
            claimedIds.add(id);

            var assembly = new ScreenAssembly(
                    id,
                    component.facing(),
                    component.origin(),
                    component.controllerPos(),
                    component.width(),
                    component.height(),
                    component.members().size(),
                    component.rectangular()
            );
            VideoSessionState input = previous == null || previous.state() == null
                    ? null
                    : previous.state().input();
            applyAssembly(level, component, assembly, input);
            rebuiltAssemblies.add(assembly);
        }

        Set<UUID> rebuiltIds = new HashSet<>();
        for (ScreenAssembly assembly : rebuiltAssemblies) {
            rebuiltIds.add(assembly.id());
        }

        previousIds.removeAll(rebuiltIds);
        for (UUID removedId : previousIds) {
            if (data.remove(removedId)) {
                ScreenSync.broadcastRemoval(level, removedId);
            }

            ScreenPlaybackSync.remove(level, removedId);
        }

        for (ScreenAssembly assembly : rebuiltAssemblies) {
            if (data.upsert(assembly)) {
                ScreenSync.broadcastUpsert(level, assembly);
            }
        }
    }

    private static Component collectComponent(ServerLevel level, BlockPos seed, Direction facing, Set<BlockPos> discovered) {
        Direction right = screenRight(facing);
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> members = new LinkedHashSet<>();
        pending.add(seed.immutable());

        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            if (discovered.contains(pos) || !isCompatiblePanel(level, pos, facing))
                continue;

            BlockPos immutablePos = pos.immutable();
            discovered.add(immutablePos);
            members.add(immutablePos);
            pending.add(immutablePos.above());
            pending.add(immutablePos.below());
            pending.add(immutablePos.relative(right));
            pending.add(immutablePos.relative(right.getOpposite()));
        }

        return describeComponent(level, facing, members);
    }

    private static Component describeComponent(ServerLevel level, Direction facing, Set<BlockPos> members) {
        Direction right = screenRight(facing);
        int minU = Integer.MAX_VALUE;
        int maxU = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        BlockPos controllerPos = null;
        Map<UUID, PreviousAssembly> previousAssemblies = new HashMap<>();

        for (BlockPos pos : members) {
            int u = localHorizontal(pos, right);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            if (controllerPos == null || comparePanelPositions(pos, controllerPos, facing) < 0) {
                controllerPos = pos;
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof FlatScreenBlockEntity screen) || screen.getScreenId() == null)
                continue;

            UUID previousId = screen.getScreenId();
            var candidate = new PreviousAssembly(
                    previousId,
                    screen.getControllerPos(),
                    screen.getScreenState()
            );
            previousAssemblies.merge(previousId, candidate, ScreenAssemblyManager::preferCompletePreviousState);
        }

        if (controllerPos == null)
            throw new IllegalArgumentException("Cannot describe an empty screen component");

        BlockPos reference = members.iterator().next();
        int referenceU = localHorizontal(reference, right);
        int uOffset = minU - referenceU;
        BlockPos origin = reference.offset(
                right.getStepX() * uOffset,
                minY - reference.getY(),
                right.getStepZ() * uOffset
        );
        int width = maxU - minU + 1;
        int height = maxY - minY + 1;
        boolean rectangular = members.size() == (long) width * height;
        return new Component(
                Set.copyOf(members),
                facing,
                origin,
                controllerPos,
                width,
                height,
                rectangular,
                Map.copyOf(previousAssemblies)
        );
    }

    private static PreviousAssembly preferCompletePreviousState(PreviousAssembly first, PreviousAssembly second) {
        if (first.state() == null && second.state() != null)
            return second;

        if (first.controllerPos() == null && second.controllerPos() != null)
            return second;

        return first;
    }

    private static @Nullable PreviousAssembly choosePreviousAssembly(Component component, Set<UUID> claimedIds) {
        return component.previousAssemblies().values().stream()
                .filter(previous -> !claimedIds.contains(previous.id()))
                .min((first, second) -> {
                    boolean firstHasController = first.controllerPos() != null
                            && component.members().contains(first.controllerPos());
                    boolean secondHasController = second.controllerPos() != null
                            && component.members().contains(second.controllerPos());
                    int retainedControllerComparison = Boolean.compare(secondHasController, firstHasController);
                    if (retainedControllerComparison != 0)
                        return retainedControllerComparison;

                    int stateComparison = Boolean.compare(second.state() != null, first.state() != null);
                    if (stateComparison != 0)
                        return stateComparison;

                    if (first.controllerPos() != null && second.controllerPos() != null) {
                        int positionComparison = comparePanelPositions(
                                first.controllerPos(), second.controllerPos(), component.facing());
                        if (positionComparison != 0)
                            return positionComparison;
                    } else if (first.controllerPos() != null) {
                        return -1;
                    } else if (second.controllerPos() != null) {
                        return 1;
                    }

                    return first.id().compareTo(second.id());
                })
                .orElse(null);
    }

    private static void applyAssembly(
            ServerLevel level,
            Component component,
            ScreenAssembly assembly,
            @Nullable VideoSessionState input) {
        for (BlockPos member : component.members()) {
            BlockEntity blockEntity = level.getBlockEntity(member);
            if (blockEntity instanceof FlatScreenBlockEntity screen) {
                screen.setAssembly(assembly, input);
            }
        }
    }

    private static boolean isCompatiblePanel(ServerLevel level, BlockPos pos, Direction facing) {
        if (!level.hasChunkAt(pos))
            return false;

        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof FlatScreenBlock
                && state.getValue(FlatScreenBlock.FACING) == facing;
    }

    public static Direction screenRight(Direction facing) {
        if (!facing.getAxis().isHorizontal())
            throw new IllegalArgumentException("A flat screen must face horizontally");

        return facing.getCounterClockWise();
    }

    private static int localHorizontal(BlockPos pos, Direction right) {
        return pos.getX() * right.getStepX() + pos.getZ() * right.getStepZ();
    }

    private static int comparePanelPositions(BlockPos first, BlockPos second, Direction facing) {
        int vertical = Integer.compare(first.getY(), second.getY());
        if (vertical != 0)
            return vertical;

        Direction right = screenRight(facing);
        int horizontal = Integer.compare(localHorizontal(first, right), localHorizontal(second, right));
        if (horizontal != 0)
            return horizontal;

        int xComparison = Integer.compare(first.getX(), second.getX());
        return xComparison != 0 ? xComparison : Integer.compare(first.getZ(), second.getZ());
    }

    private record PreviousAssembly(
            UUID id,
            @Nullable BlockPos controllerPos,
            FlatScreenBlockEntity.@Nullable ScreenState state
    ) {
    }

    private record Component(
            Set<BlockPos> members,
            Direction facing,
            BlockPos origin,
            BlockPos controllerPos,
            int width,
            int height,
            boolean rectangular,
            Map<UUID, PreviousAssembly> previousAssemblies
    ) {
        private boolean containsPreviousController() {
            return this.previousAssemblies.values().stream()
                    .map(PreviousAssembly::controllerPos)
                    .anyMatch(this.members::contains);
        }
    }
}
