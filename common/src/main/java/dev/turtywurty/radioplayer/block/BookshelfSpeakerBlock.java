package dev.turtywurty.radioplayer.block;

import dev.turtywurty.radioplayer.sound.SpeakerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

public class BookshelfSpeakerBlock extends SpeakerBlock {
    private static final VoxelShape SHAPE = makeShape();

    public BookshelfSpeakerBlock(Properties properties) {
        super(properties, SpeakerType.BOOKSHELF, SHAPE);
    }

    @Override
    protected boolean canSurvive(@NonNull BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        return belowState.isFaceSturdy(level, below, Direction.UP);
    }

    private static VoxelShape makeShape() {
        VoxelShape shape = Shapes.empty();
        shape = Shapes.join(shape, Shapes.box(0.25, 0.1875, 0.25, 0.75, 0.8125, 0.75), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.3125, 0, 0.3125, 0.6875, 0.0625, 0.6875), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.4375, 0.0625, 0.4375, 0.5625, 0.1875, 0.5625), BooleanOp.OR);

        return shape;
    }
}
