package dev.turtywurty.mediabox.block;

import dev.turtywurty.mediabox.api.client.MediaBoxClientAPI;
import dev.turtywurty.mediabox.block.entity.FlatScreenBlockEntity;
import dev.turtywurty.mediabox.cable.CableConnectionLifecycle;
import dev.turtywurty.mediabox.cable.CableSync;
import dev.turtywurty.mediabox.cable.PortEndpoint;
import dev.turtywurty.mediabox.item.CableItem;
import dev.turtywurty.mediabox.screen.ScreenAssemblyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class FlatScreenBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public FlatScreenBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos blockPos, @NonNull BlockState blockState) {
        return new FlatScreenBlockEntity(blockPos, blockState);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected @NonNull InteractionResult useItemOn(
            @NonNull ItemStack stack,
            @NonNull BlockState state,
            @NonNull Level level,
            @NonNull BlockPos pos,
            @NonNull Player player,
            @NonNull InteractionHand hand,
            @NonNull BlockHitResult hitResult
    ) {
        if (stack.getItem() instanceof CableItem)
            return InteractionResult.PASS;

        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(
            @NonNull BlockState state,
            Level level,
            @NonNull BlockPos pos,
            @NonNull Player player,
            @NonNull BlockHitResult hitResult
    ) {
        if (level.isClientSide()
                && level.getBlockEntity(pos) instanceof FlatScreenBlockEntity screen
                && screen.getScreenId() != null) {
            MediaBoxClientAPI.openFlatScreenSettingsScreen(pos, screen.getScreenId());
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(
            @NonNull BlockState state,
            @NonNull Level level,
            @NonNull BlockPos pos,
            @NonNull BlockState oldState,
            boolean movedByPiston
    ) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!(level instanceof ServerLevel serverLevel))
            return;

        if (oldState.getBlock() instanceof FlatScreenBlock
                && oldState.hasProperty(FACING)
                && oldState.getValue(FACING) != state.getValue(FACING)) {
            ScreenAssemblyManager.rebuildAfterRemoval(serverLevel, pos, oldState.getValue(FACING));
        }

        level.scheduleTick(pos, this, 1); // delays until the next tick to rebuild the screen assembly
    }

    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos, @NonNull RandomSource random) {
        ScreenAssemblyManager.rebuildFrom(level, pos);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            @NonNull BlockState state,
            @NonNull BlockEntityType<T> blockEntityType
    ) {
        if (level.isClientSide() || blockEntityType != ModBlockEntities.flatScreen.value())
            return null;

        return (level1, pos, _, screen) -> FlatScreenBlockEntity.serverTick(level1, pos, (FlatScreenBlockEntity) screen);
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
        builder.add(FACING);
    }

    @Override
    protected void affectNeighborsAfterRemoval(
            BlockState state,
            @NonNull ServerLevel level,
            @NonNull BlockPos pos,
            boolean movedByPiston
    ) {
        CableConnectionLifecycle.removePort(level, new PortEndpoint(
                level.dimension(),
                pos,
                FlatScreenBlockEntity.AUDIO_OUTPUT_PORT_ID
        ), false);
        CableSync.broadcastSnapshot(level);
        ScreenAssemblyManager.rebuildAfterRemoval(level, pos, state.getValue(FACING));
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
