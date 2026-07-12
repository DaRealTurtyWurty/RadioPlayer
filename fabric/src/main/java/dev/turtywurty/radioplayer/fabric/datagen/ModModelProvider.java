package dev.turtywurty.radioplayer.fabric.datagen;

import dev.turtywurty.radioplayer.Radioplayer;
import dev.turtywurty.radioplayer.block.ModBlocks;
import dev.turtywurty.radioplayer.block.RadioPlayerBlock;
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
                new MultiVariant(WeightedList.of(new Variant(Radioplayer.id("block/radio")))))
                .with(PropertyDispatch.modify(RadioPlayerBlock.FACING)
                        .select(Direction.NORTH, NOP)
                        .select(Direction.EAST, Y_ROT_90)
                        .select(Direction.SOUTH, Y_ROT_180)
                        .select(Direction.WEST, Y_ROT_270)));

        blockModelGenerators.blockStateOutput.accept(MultiVariantGenerator.dispatch(
                ModBlocks.globe.asBlock(),
                new MultiVariant(WeightedList.of(new Variant(Radioplayer.id("block/globe")))))
                .with(PropertyDispatch.modify(RadioPlayerBlock.FACING)
                        .select(Direction.NORTH, NOP)
                        .select(Direction.EAST, Y_ROT_90)
                        .select(Direction.SOUTH, Y_ROT_180)
                        .select(Direction.WEST, Y_ROT_270)));

        blockModelGenerators.blockStateOutput.accept(MultiVariantGenerator.dispatch(
                ModBlocks.speaker.asBlock(),
                new MultiVariant(WeightedList.of(new Variant(Radioplayer.id("block/speaker")))))
                .with(PropertyDispatch.modify(RadioPlayerBlock.FACING)
                        .select(Direction.NORTH, NOP)
                        .select(Direction.EAST, Y_ROT_90)
                        .select(Direction.SOUTH, Y_ROT_180)
                        .select(Direction.WEST, Y_ROT_270)));
    }

    @Override
    public void generateItemModels(ItemModelGenerators itemModelGenerators) {
        itemModelGenerators.itemModelOutput.accept(
                ModBlocks.radioPlayer.asBlock().asItem(),
                ItemModelUtils.plainModel(Radioplayer.id("block/radio")));

        itemModelGenerators.itemModelOutput.accept(
                ModBlocks.globe.asBlock().asItem(),
                ItemModelUtils.plainModel(Radioplayer.id("block/globe")));

        itemModelGenerators.itemModelOutput.accept(
                ModBlocks.speaker.asBlock().asItem(),
                ItemModelUtils.plainModel(Radioplayer.id("block/speaker")));
    }
}
