package dev.turtywurty.radioplayer.block;

import dev.turtywurty.radioplayer.api.client.RadioplayerClientAPI;
import dev.turtywurty.radioplayer.block.entity.RadioPlayerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class RadioPlayerBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = makeShape();
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, SHAPE,
            Direction.EAST, calculateShape(Direction.EAST),
            Direction.SOUTH, calculateShape(Direction.SOUTH),
            Direction.WEST, calculateShape(Direction.WEST)
    );

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public RadioPlayerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos blockPos, @NonNull BlockState blockState) {
        return new RadioPlayerBlockEntity(blockPos, blockState);
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hitResult) {
        if (level.isClientSide() && level.getBlockEntity(pos) instanceof RadioPlayerBlockEntity radioPlayer) {
            RadioplayerClientAPI.openRadioPlayerScreen(pos, radioPlayer.getUrl(), radioPlayer.isPlaying(), radioPlayer.getSavedStations());
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected @NonNull VoxelShape getShape(BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected @NonNull BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected @NonNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    public static VoxelShape makeShape() {
        VoxelShape shape = Shapes.empty();
        shape = Shapes.join(shape, Shapes.box(0.125, 0, 0.3125, 0.875, 0.375, 0.6875), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.125, 0.3125, 0.6875, 0.25, 0.375, 0.75), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.125, 0.375, 0.6875, 0.1875, 0.75, 0.75), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.25, 0.375, 0.5, 0.75, 0.4375, 0.5625), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.25, 0.4375, 0.5, 0.3125, 0.5625, 0.5625), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.6875, 0.4375, 0.5, 0.75, 0.5625, 0.5625), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.3125, 0.5625, 0.5, 0.6875, 0.625, 0.5625), BooleanOp.OR);

        return shape;
    }

    private static VoxelShape calculateShape(Direction to) {
        final VoxelShape[] buffer = {SHAPE, Shapes.empty()};

        final int times = (to.get2DDataValue() - Direction.NORTH.get2DDataValue() + 4) % 4;
        for (int i = 0; i < times; i++) {
            buffer[0].forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                    buffer[1] = Shapes.or(buffer[1],
                            Shapes.box(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX)));
            buffer[0] = buffer[1];
            buffer[1] = Shapes.empty();
        }

        return buffer[0];
    }
}
