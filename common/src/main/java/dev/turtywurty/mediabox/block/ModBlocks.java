package dev.turtywurty.mediabox.block;

import dev.turtywurty.mediabox.sound.SpeakerType;
import net.blay09.mods.balm.world.level.block.BalmBlockRegistrar;
import net.blay09.mods.balm.world.level.block.DeferredBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Function;

public class ModBlocks {
    private static final VoxelShape SPEAKER_SHAPE = Shapes.box(0.125, 0, 0.125, 0.875, 1, 0.875);

    public static DeferredBlock radioPlayer;

    public static DeferredBlock globe;

    public static DeferredBlock speaker;
    public static DeferredBlock subwoofer;
    public static DeferredBlock bassReflexSpeaker;
    public static DeferredBlock hornSpeaker;
    public static DeferredBlock bookshelfSpeaker;
    public static DeferredBlock floorStandingSpeaker;

    public static DeferredBlock cablePort;

    public static DeferredBlock flatScreen;

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
        subwoofer = registerSpeaker(blocks, "subwoofer", SpeakerType.SUBWOOFER, Shapes.block());
        bassReflexSpeaker = registerSpeaker(blocks, "bass_reflex_speaker", SpeakerType.BASS_REFLEX, Shapes.block());
        hornSpeaker = registerSpeaker(blocks, "horn_speaker", SpeakerType.HORN, Shapes.block());
        bookshelfSpeaker = registerSpeakerLike(blocks, "bookshelf_speaker", BookshelfSpeakerBlock::new);
        floorStandingSpeaker = registerSpeakerLike(blocks, "floor_standing_speaker", FloorStandingSpeakerBlock::new);

        cablePort = blocks.register("cable_port", CablePortBlock::new, it ->
                        it.mapColor(MapColor.METAL)
                                .strength(0.5F)
                                .sound(SoundType.METAL)
                                .noOcclusion()
                                .instabreak())
                .withItem(BlockItem::new)
                .asDeferredBlock();

        flatScreen = blocks.register("flat_screen", FlatScreenBlock::new, it ->
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
        return registerSpeakerLike(blocks, name, properties -> new SpeakerBlock(properties, speakerType, shape));
    }

    private static <T extends SpeakerBlock> DeferredBlock registerSpeakerLike(BalmBlockRegistrar blocks, String name,
                                                                              Function<BlockBehaviour.Properties, T> blockFactory) {
        return blocks.register(name, blockFactory::apply, it ->
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
