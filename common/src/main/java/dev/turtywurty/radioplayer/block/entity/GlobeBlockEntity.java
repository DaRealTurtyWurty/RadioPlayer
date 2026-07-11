package dev.turtywurty.radioplayer.block.entity;

import dev.turtywurty.radioplayer.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class GlobeBlockEntity extends BlockEntity {
    private BlockPos connectedRadioPlayerPos;

    public GlobeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.globe.value(), pos, state);
    }

    public void setConnectedRadioPlayerPos(BlockPos pos) {
        this.connectedRadioPlayerPos = pos;
        setChanged();

        if (level != null) {
            level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public BlockPos getConnectedRadioPlayerPos() {
        return connectedRadioPlayerPos;
    }
}
