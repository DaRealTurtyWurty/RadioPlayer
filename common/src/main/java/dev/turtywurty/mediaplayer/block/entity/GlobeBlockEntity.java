package dev.turtywurty.mediaplayer.block.entity;

import dev.turtywurty.mediaplayer.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class GlobeBlockEntity extends BlockEntity {
    private @Nullable BlockPos connectedRadioPlayerPos;

    public GlobeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.globe.value(), pos, state);
    }

    public @Nullable BlockPos getConnectedRadioPlayerPos() {
        return connectedRadioPlayerPos;
    }

    public void setConnectedRadioPlayerPos(@Nullable BlockPos pos) {
        this.connectedRadioPlayerPos = pos;
        setChanged();

        if (level != null) {
            level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }
}
