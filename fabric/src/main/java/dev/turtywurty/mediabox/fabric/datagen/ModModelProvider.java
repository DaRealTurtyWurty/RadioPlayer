package dev.turtywurty.mediabox.fabric.datagen;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.block.GlobeBlock;
import dev.turtywurty.mediabox.block.HorizontalDirection8;
import dev.turtywurty.mediabox.block.ModBlocks;
import dev.turtywurty.mediabox.block.RadioPlayerBlock;
import dev.turtywurty.mediabox.block.SpeakerBlock;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.core.Direction;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import static net.minecraft.client.data.models.BlockModelGenerators.NOP;
import static net.minecraft.client.data.models.BlockModelGenerators.Y_ROT_180;
import static net.minecraft.client.data.models.BlockModelGenerators.Y_ROT_270;
import static net.minecraft.client.data.models.BlockModelGenerators.Y_ROT_90;

public class ModModelProvider extends FabricModelProvider {
    public ModModelProvider(FabricPackOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators blockModelGenerators) {
        blockModelGenerators.blockStateOutput.accept(MultiVariantGenerator.dispatch(
                ModBlocks.radioPlayer.asBlock(),
                new MultiVariant(WeightedList.of(new Variant(MediaBox.id("block/radio")))))
                .with(PropertyDispatch.modify(RadioPlayerBlock.FACING)
                        .select(HorizontalDirection8.NORTH, NOP)
                        .select(HorizontalDirection8.NORTH_EAST, Y_ROT_90)
                        .select(HorizontalDirection8.EAST, Y_ROT_90)
                        .select(HorizontalDirection8.SOUTH_EAST, Y_ROT_180)
                        .select(HorizontalDirection8.SOUTH, Y_ROT_180)
                        .select(HorizontalDirection8.SOUTH_WEST, Y_ROT_270)
                        .select(HorizontalDirection8.WEST, Y_ROT_270)
                        .select(HorizontalDirection8.NORTH_WEST, NOP)));

        blockModelGenerators.blockStateOutput.accept(MultiVariantGenerator.dispatch(
                ModBlocks.globe.asBlock(),
                new MultiVariant(WeightedList.of(new Variant(MediaBox.id("block/globe")))))
                .with(PropertyDispatch.modify(GlobeBlock.FACING)
                        .select(Direction.NORTH, NOP)
                        .select(Direction.EAST, Y_ROT_90)
                        .select(Direction.SOUTH, Y_ROT_180)
                        .select(Direction.WEST, Y_ROT_270)));

        blockModelGenerators.blockStateOutput.accept(MultiVariantGenerator.dispatch(
                ModBlocks.speaker.asBlock(),
                new MultiVariant(WeightedList.of(new Variant(MediaBox.id("block/speaker")))))
                .with(PropertyDispatch.modify(SpeakerBlock.FACING)
                        .select(HorizontalDirection8.NORTH, NOP)
                        .select(HorizontalDirection8.NORTH_EAST, Y_ROT_90)
                        .select(HorizontalDirection8.EAST, Y_ROT_90)
                        .select(HorizontalDirection8.SOUTH_EAST, Y_ROT_180)
                        .select(HorizontalDirection8.SOUTH, Y_ROT_180)
                        .select(HorizontalDirection8.SOUTH_WEST, Y_ROT_270)
                        .select(HorizontalDirection8.WEST, Y_ROT_270)
                        .select(HorizontalDirection8.NORTH_WEST, NOP)));

        speakerBlockState(blockModelGenerators, ModBlocks.bassReflexSpeaker.asBlock(), "bass_reflex_speaker");
        speakerBlockState(blockModelGenerators, ModBlocks.hornSpeaker.asBlock(), "horn_speaker");
        speakerBlockState(blockModelGenerators, ModBlocks.bookshelfSpeaker.asBlock(), "bookshelf_speaker");
        speakerBlockState(blockModelGenerators, ModBlocks.floorStandingSpeaker.asBlock(), "floor_standing_speaker");
        speakerBlockState(blockModelGenerators, ModBlocks.subwoofer.asBlock(), "subwoofer");
    }

    private static void speakerBlockState(BlockModelGenerators blockModelGenerators, Block block,
                                          String modelName) {
        blockModelGenerators.blockStateOutput.accept(MultiVariantGenerator.dispatch(
                block,
                new MultiVariant(WeightedList.of(new Variant(MediaBox.id("block/" + modelName)))))
                .with(PropertyDispatch.modify(SpeakerBlock.FACING)
                        .select(HorizontalDirection8.NORTH, NOP)
                        .select(HorizontalDirection8.NORTH_EAST, Y_ROT_90)
                        .select(HorizontalDirection8.EAST, Y_ROT_90)
                        .select(HorizontalDirection8.SOUTH_EAST, Y_ROT_180)
                        .select(HorizontalDirection8.SOUTH, Y_ROT_180)
                        .select(HorizontalDirection8.SOUTH_WEST, Y_ROT_270)
                        .select(HorizontalDirection8.WEST, Y_ROT_270)
                        .select(HorizontalDirection8.NORTH_WEST, NOP)));
    }

    @Override
    public void generateItemModels(ItemModelGenerators itemModelGenerators) {
        itemModelGenerators.itemModelOutput.accept(
                ModBlocks.radioPlayer.asBlock().asItem(),
                ItemModelUtils.plainModel(MediaBox.id("block/radio")));

        itemModelGenerators.itemModelOutput.accept(
                ModBlocks.globe.asBlock().asItem(),
                ItemModelUtils.plainModel(MediaBox.id("block/globe")));

        itemModelGenerators.itemModelOutput.accept(
                ModBlocks.speaker.asBlock().asItem(),
                ItemModelUtils.plainModel(MediaBox.id("block/speaker")));

        speakerItemModel(itemModelGenerators, ModBlocks.bassReflexSpeaker.asBlock().asItem(), "bass_reflex_speaker");
        speakerItemModel(itemModelGenerators, ModBlocks.hornSpeaker.asBlock().asItem(), "horn_speaker");
        speakerItemModel(itemModelGenerators, ModBlocks.bookshelfSpeaker.asBlock().asItem(), "bookshelf_speaker");
        speakerItemModel(itemModelGenerators, ModBlocks.floorStandingSpeaker.asBlock().asItem(), "floor_standing_speaker");
        speakerItemModel(itemModelGenerators, ModBlocks.subwoofer.asBlock().asItem(), "subwoofer");
    }

    private static void speakerItemModel(ItemModelGenerators itemModelGenerators, Item item,
                                         String modelName) {
        itemModelGenerators.itemModelOutput.accept(
                item,
                ItemModelUtils.plainModel(MediaBox.id("block/" + modelName)));
    }
}
