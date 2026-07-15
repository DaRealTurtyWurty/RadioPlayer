package dev.turtywurty.mediabox.client.render.blockentity;

import dev.turtywurty.mediabox.block.HorizontalDirection8;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public class RotatedBlockModelRenderState extends BlockEntityRenderState {
    public final BlockModelRenderState blockModel = new BlockModelRenderState();
    public HorizontalDirection8 facing = HorizontalDirection8.NORTH;
}
