package dev.turtywurty.radioplayer.client.render.blockentity;

import dev.turtywurty.radioplayer.block.HorizontalDirection8;
import dev.turtywurty.radioplayer.block.SpeakerBlock;
import dev.turtywurty.radioplayer.block.SpeakerBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class SpeakerBlockEntityRenderer extends RotatedBlockModelRenderer<SpeakerBlockEntity> {
    public SpeakerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
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
