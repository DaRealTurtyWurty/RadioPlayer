package dev.turtywurty.radioplayer.block;

import net.blay09.mods.balm.world.level.block.BalmBlockRegistrar;
import net.blay09.mods.balm.world.level.block.DeferredBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

public class ModBlocks {
    public static DeferredBlock radioPlayer;
    public static DeferredBlock globe;
    public static DeferredBlock speaker;
    public static DeferredBlock subwoofer;

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

        globe = blocks.register("globe", GlobeBlock::new, it ->
                        it.mapColor(MapColor.COLOR_LIGHT_BLUE)
                                .instrument(NoteBlockInstrument.BASS)
                                .strength(2.0F, 6.0F)
                                .sound(SoundType.GLASS)
                                .ignitedByLava()
                                .noOcclusion())
                .withItem(BlockItem::new)
                .asDeferredBlock();

        speaker = blocks.register("speaker", SpeakerBlock::new, it ->
                        it.mapColor(MapColor.COLOR_BLACK)
                                .instrument(NoteBlockInstrument.BASS)
                                .strength(2.0F, 6.0F)
                                .sound(SoundType.WOOD)
                                .ignitedByLava()
                                .noOcclusion())
                .withItem(BlockItem::new)
                .asDeferredBlock();

        subwoofer = blocks.register("subwoofer", SubwooferBlock::new, it ->
                        it.mapColor(MapColor.COLOR_BLACK)
                                .instrument(NoteBlockInstrument.BASS)
                                .strength(2.0F, 6.0F)
                                .sound(SoundType.WOOD)
                                .ignitedByLava()
                                .noOcclusion())
                .withItem(BlockItem::new)
                .asDeferredBlock();
    }
}
