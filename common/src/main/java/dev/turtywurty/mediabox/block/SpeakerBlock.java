package dev.turtywurty.mediabox.block;

import dev.turtywurty.mediabox.sound.SpeakerType;
import dev.turtywurty.mediabox.cable.PortEndpoint;
import dev.turtywurty.mediabox.cable.CableSync;
import dev.turtywurty.mediabox.cable.CableConnectionLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class SpeakerBlock extends Block implements EntityBlock {
    public static final EnumProperty<HorizontalDirection8> FACING = EnumProperty.create("facing", HorizontalDirection8.class);
    private static final VoxelShape SHAPE = Shapes.box(0.125, 0, 0.125, 0.875, 1, 0.875);

    private final SpeakerType speakerType;
    private final Map<Direction, VoxelShape> shapes;

    public SpeakerBlock(Properties properties) {
        this(properties, SpeakerType.FULL_RANGE);
    }

    public SpeakerBlock(Properties properties, SpeakerType speakerType) {
        this(properties, speakerType, SHAPE);
    }

    public SpeakerBlock(Properties properties, SpeakerType speakerType, VoxelShape shape) {
        super(properties);
        this.speakerType = speakerType;
        this.shapes = Shapes.rotateHorizontal(shape);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos blockPos, @NonNull BlockState blockState) {
        return new SpeakerBlockEntity(blockPos, blockState);
    }

    public SpeakerType getSpeakerType() {
        return this.speakerType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || blockEntityType != ModBlockEntities.speaker.value())
            return null;

        return (BlockEntityTicker<T>) (BlockEntityTicker<SpeakerBlockEntity>) SpeakerBlockEntity::serverTick;
    }

    @Override
    protected @NonNull VoxelShape getShape(BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return this.shapes.get(state.getValue(FACING).nearestCardinal());
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, HorizontalDirection8.fromRotation(context.getRotation()));
    }

    @Override
    protected @NonNull BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, HorizontalDirection8.fromDirection(mirror.mirror(state.getValue(FACING).nearestCardinal())));
    }

    @Override
    protected @NonNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, HorizontalDirection8.fromDirection(rotation.rotate(state.getValue(FACING).nearestCardinal())));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    protected void affectNeighborsAfterRemoval(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            boolean movedByPiston) {
        CableConnectionLifecycle.removePort(level, new PortEndpoint(
                level.dimension(),
                pos,
                SpeakerBlockEntity.AUDIO_INPUT_PORT_ID), false);
        CableSync.broadcastSnapshot(level);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
