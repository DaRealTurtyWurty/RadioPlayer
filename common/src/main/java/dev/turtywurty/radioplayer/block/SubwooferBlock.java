package dev.turtywurty.radioplayer.block;

import dev.turtywurty.radioplayer.sound.SpeakerType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class SubwooferBlock extends SpeakerBlock {
    private static final VoxelShape SHAPE = Shapes.block();

    public SubwooferBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos blockPos, @NonNull BlockState blockState) {
        return new SubwooferBlockEntity(blockPos, blockState);
    }

    @Override
    public SpeakerType getSpeakerType() {
        return SpeakerType.SUBWOOFER;
    }

    @Override
    protected @NonNull VoxelShape getShape(BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos,
                                           @NonNull CollisionContext context) {
        return SHAPE;
    }
}
