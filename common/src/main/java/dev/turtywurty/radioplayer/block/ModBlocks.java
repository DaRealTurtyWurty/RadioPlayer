package dev.turtywurty.radioplayer.block;

import net.blay09.mods.balm.world.level.block.BalmBlockRegistrar;
import net.blay09.mods.balm.world.level.block.DeferredBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

public class ModBlocks {
    public static DeferredBlock radioPlayer;

    public static void initialize(BalmBlockRegistrar blocks) {
        radioPlayer = blocks.register("radio_player", RadioPlayerBlock::new, it ->
                        it.mapColor(MapColor.DIRT)
                                .instrument(NoteBlockInstrument.BASS)
                                .strength(2.0F, 6.0F)
                                .sound(SoundType.WOOD)
                                .ignitedByLava()
                                .noOcclusion())
                .withItem(BlockItem::new)
                .asDeferredBlock();
    }
}
