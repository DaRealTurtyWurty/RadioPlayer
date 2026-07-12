package dev.turtywurty.radioplayer.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class SubwooferBlockEntity extends SpeakerBlockEntity {
    public SubwooferBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.subwoofer.value(), pos, state);
    }
}
