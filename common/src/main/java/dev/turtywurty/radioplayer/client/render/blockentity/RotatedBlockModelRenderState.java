package dev.turtywurty.radioplayer.client.render.blockentity;

import dev.turtywurty.radioplayer.block.HorizontalDirection8;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.state.BlockState;

public class RotatedBlockModelRenderState extends BlockEntityRenderState {
    public final MovingBlockRenderState movingBlockRenderState = new MovingBlockRenderState();
    public BlockState renderState;
    public HorizontalDirection8 facing = HorizontalDirection8.NORTH;
}
