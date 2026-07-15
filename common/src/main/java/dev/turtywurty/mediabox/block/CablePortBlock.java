package dev.turtywurty.mediabox.block;

import dev.turtywurty.mediabox.cable.CableSavedData;
import dev.turtywurty.mediabox.cable.CableSync;
import dev.turtywurty.mediabox.cable.PortEndpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class CablePortBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    private static final double PORT_DEPTH = 2.0 / 16.0;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.DOWN, Shapes.box(0.25, 0.875, 0.25, 0.75, 1.0, 0.75),
            Direction.UP, Shapes.box(0.25, 0.0, 0.25, 0.75, 0.125, 0.75),
            Direction.NORTH, Shapes.box(0.25, 0.25, 0.875, 0.75, 0.75, 1.0),
            Direction.SOUTH, Shapes.box(0.25, 0.25, 0.0, 0.75, 0.75, 0.125),
            Direction.WEST, Shapes.box(0.875, 0.25, 0.25, 1.0, 0.75, 0.75),
            Direction.EAST, Shapes.box(0.0, 0.25, 0.25, 0.125, 0.75, 0.75));

    public CablePortBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false));
    }

    public static Vec3 portPosition(BlockPos pos, BlockState state) {
        Direction towardsWall = state.getValue(FACING).getOpposite();
        double offset = 0.5 - PORT_DEPTH;
        return Vec3.atCenterOf(pos).add(
                towardsWall.getStepX() * offset,
                towardsWall.getStepY() * offset,
                towardsWall.getStepZ() * offset);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new CablePortBlockEntity(pos, state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        BlockState state = defaultBlockState()
                .setValue(FACING, context.getClickedFace())
                .setValue(WATERLOGGED, fluidState.is(FluidTags.WATER) && fluidState.isFull());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected @NonNull BlockState updateShape(
            BlockState state,
            @NonNull LevelReader level,
            @NonNull ScheduledTickAccess ticks,
            @NonNull BlockPos pos,
            @NonNull Direction directionToNeighbour,
            @NonNull BlockPos neighbourPos,
            @NonNull BlockState neighbourState,
            @NonNull RandomSource random) {
        if (state.getValue(WATERLOGGED))
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));

        return directionToNeighbour == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected @NonNull VoxelShape getShape(
            BlockState state,
            @NonNull BlockGetter level,
            @NonNull BlockPos pos,
            @NonNull CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected @NonNull FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected @NonNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NonNull BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    protected void affectNeighborsAfterRemoval(
            @NonNull BlockState state,
            @NonNull ServerLevel level,
            @NonNull BlockPos pos,
            boolean movedByPiston) {
        CableSavedData.get(level).removePort(new PortEndpoint(
                level.dimension(),
                pos,
                CablePortBlockEntity.AUDIO_PORT_ID), true);
        CableSync.broadcastSnapshot(level);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
