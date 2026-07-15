package dev.turtywurty.mediabox.block;

import dev.turtywurty.mediabox.api.client.MediaBoxClientAPI;
import dev.turtywurty.mediabox.block.entity.RadioPlayerBlockEntity;
import dev.turtywurty.mediabox.cable.CableSavedData;
import dev.turtywurty.mediabox.cable.PortEndpoint;
import dev.turtywurty.mediabox.cable.CableSync;
import dev.turtywurty.mediabox.item.AudioCableItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
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
    public static final EnumProperty<HorizontalDirection8> FACING = EnumProperty.create("facing", HorizontalDirection8.class);
    private static final VoxelShape SHAPE = makeShape();
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(SHAPE);

    public RadioPlayerBlock(Properties properties) {
        super(properties);
    }

    private static VoxelShape makeShape() {
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

    @Override
    public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos blockPos, @NonNull BlockState blockState) {
        return new RadioPlayerBlockEntity(blockPos, blockState);
    }

    @Override
    protected @NonNull InteractionResult useItemOn(
            @NonNull ItemStack stack,
            @NonNull BlockState state,
            @NonNull Level level,
            @NonNull BlockPos pos,
            @NonNull Player player,
            @NonNull InteractionHand hand,
            @NonNull BlockHitResult hitResult) {
        if (stack.getItem() instanceof AudioCableItem)
            return InteractionResult.PASS;

        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hitResult) {
        if (level.isClientSide() && level.getBlockEntity(pos) instanceof RadioPlayerBlockEntity radioPlayer) {
            MediaBoxClientAPI.openRadioPlayerScreen(pos, radioPlayer.getUrl(), radioPlayer.isPlaying(), radioPlayer.getSavedStations());
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected @NonNull VoxelShape getShape(BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPES.get(state.getValue(FACING).nearestCardinal());
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
        CableSavedData.get(level).removePort(new PortEndpoint(
                level.dimension(),
                pos,
                RadioPlayerBlockEntity.AUDIO_OUTPUT_PORT_ID), false);
        CableSync.broadcastSnapshot(level);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
