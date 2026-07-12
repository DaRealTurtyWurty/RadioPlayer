package dev.turtywurty.radioplayer.client.render.blockentity;

import dev.turtywurty.radioplayer.block.HorizontalDirection8;
import dev.turtywurty.radioplayer.block.SpeakerBlock;
import dev.turtywurty.radioplayer.block.SubwooferBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class SubwooferBlockEntityRenderer extends RotatedBlockModelRenderer<SubwooferBlockEntity> {
    public SubwooferBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected HorizontalDirection8 getFacing(BlockState blockState) {
        return blockState.getValue(SpeakerBlock.FACING);
    }

    @Override
    protected BlockState getModelState(BlockState blockState) {
        return blockState.setValue(SpeakerBlock.FACING, HorizontalDirection8.NORTH);
    }
}
