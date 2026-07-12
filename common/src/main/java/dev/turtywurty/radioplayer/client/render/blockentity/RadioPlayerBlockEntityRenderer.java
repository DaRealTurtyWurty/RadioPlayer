package dev.turtywurty.radioplayer.client.render.blockentity;

import dev.turtywurty.radioplayer.block.HorizontalDirection8;
import dev.turtywurty.radioplayer.block.RadioPlayerBlock;
import dev.turtywurty.radioplayer.block.entity.RadioPlayerBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class RadioPlayerBlockEntityRenderer extends RotatedBlockModelRenderer<RadioPlayerBlockEntity> {
    public RadioPlayerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected HorizontalDirection8 getFacing(BlockState blockState) {
        return blockState.getValue(RadioPlayerBlock.FACING);
    }

    @Override
    protected BlockState getModelState(BlockState blockState) {
        return blockState.setValue(RadioPlayerBlock.FACING, HorizontalDirection8.NORTH);
    }
}
