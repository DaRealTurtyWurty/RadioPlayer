package dev.turtywurty.radioplayer.fabric.datagen;

import dev.turtywurty.radioplayer.Radioplayer;
import dev.turtywurty.radioplayer.block.ModBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends FabricRecipeProvider {
    public ModRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> provider) {
        super(output, provider);
    }

    @Override
    protected RecipeProvider createRecipeProvider(HolderLookup.Provider registryLookup, RecipeOutput exporter) {
        return new RecipeProvider(registryLookup, exporter) {
            @Override
            public void buildRecipes() {
                shaped(RecipeCategory.REDSTONE, ModBlocks.radioPlayer.asBlock())
                        .define('I', Items.IRON_INGOT)
                        .define('N', Blocks.NOTE_BLOCK)
                        .define('P', ItemTags.PLANKS)
                        .define('R', Items.REDSTONE)
                        .pattern("IPI")
                        .pattern("RNR")
                        .pattern("IPI")
                        .unlockedBy("has_note_block", has(Blocks.NOTE_BLOCK))
                        .save(output);
            }
        };
    }

    @Override
    public String getName() {
        return Radioplayer.MOD_ID;
    }
}
