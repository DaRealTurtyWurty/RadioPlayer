package dev.turtywurty.radioplayer.block;

import dev.turtywurty.radioplayer.sound.SpeakerType;
import net.blay09.mods.balm.world.level.block.BalmBlockRegistrar;
import net.blay09.mods.balm.world.level.block.DeferredBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ModBlocks {
    private static final VoxelShape SPEAKER_SHAPE = Shapes.box(0.125, 0, 0.125, 0.875, 1, 0.875);
    private static final VoxelShape SUBWOOFER_SHAPE = Shapes.block();
    private static final VoxelShape BASS_REFLEX_SHAPE = Shapes.box(0.0625, 0, 0.0625, 0.9375, 1, 0.9375);
    private static final VoxelShape HORN_SHAPE = Shapes.box(0.0, 0.125, 0.1875, 1.0, 0.875, 0.8125);
    private static final VoxelShape BOOKSHELF_SHAPE = Shapes.box(0.125, 0.0625, 0.125, 0.875, 0.8125, 0.875);

    public static DeferredBlock radioPlayer;
    public static DeferredBlock globe;
    public static DeferredBlock speaker;
    public static DeferredBlock subwoofer;
    public static DeferredBlock bassReflexSpeaker;
    public static DeferredBlock hornSpeaker;
    public static DeferredBlock bookshelfSpeaker;
    public static DeferredBlock floorStandingSpeaker;

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

        speaker = registerSpeaker(blocks, "speaker", SpeakerType.FULL_RANGE, SPEAKER_SHAPE);
        subwoofer = registerSpeaker(blocks, "subwoofer", SpeakerType.SUBWOOFER, SUBWOOFER_SHAPE);
        bassReflexSpeaker = registerSpeaker(blocks, "bass_reflex_speaker", SpeakerType.BASS_REFLEX, BASS_REFLEX_SHAPE);
        hornSpeaker = registerSpeaker(blocks, "horn_speaker", SpeakerType.HORN, HORN_SHAPE);
        bookshelfSpeaker = registerSpeaker(blocks, "bookshelf_speaker", SpeakerType.BOOKSHELF, BOOKSHELF_SHAPE);
        floorStandingSpeaker = blocks.register("floor_standing_speaker", FloorStandingSpeakerBlock::new, it ->
                        it.mapColor(MapColor.COLOR_BLACK)
                                .instrument(NoteBlockInstrument.BASS)
                                .strength(2.0F, 6.0F)
                                .sound(SoundType.WOOD)
                                .ignitedByLava()
                                .noOcclusion())
                .withItem(BlockItem::new)
                .asDeferredBlock();
    }

    private static DeferredBlock registerSpeaker(BalmBlockRegistrar blocks, String name, SpeakerType speakerType,
                                                 VoxelShape shape) {
        return blocks.register(name, properties -> new SpeakerBlock(properties, speakerType, shape), it ->
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
