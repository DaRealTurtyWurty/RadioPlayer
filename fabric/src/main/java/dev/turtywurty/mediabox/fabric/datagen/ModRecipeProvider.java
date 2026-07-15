package dev.turtywurty.mediabox.fabric.datagen;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.block.ModBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends FabricRecipeProvider {
    public ModRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> provider) {
        super(output, provider);
    }

    @Override
    protected @NonNull RecipeProvider createRecipeProvider(HolderLookup.@NonNull Provider registryLookup, @NonNull RecipeOutput exporter) {
        return new RecipeProvider(registryLookup, exporter) {
            @Override
            public void buildRecipes() {
                shaped(RecipeCategory.REDSTONE, ModBlocks.radioPlayer.asBlock())
                        .define('I', Items.IRON_INGOT)
                        .define('N', Blocks.NOTE_BLOCK)
                        .define('P', ItemTags.PLANKS)
                        .define('R', Items.REDSTONE)
                        .pattern("RIR")
                        .pattern("PNP")
                        .pattern("PPP")
                        .unlockedBy("has_note_block", has(Blocks.NOTE_BLOCK))
                        .save(output);

                shaped(RecipeCategory.REDSTONE, ModBlocks.globe.asBlock())
                        .define('G', Blocks.GLASS)
                        .define('I', Items.IRON_INGOT)
                        .define('M', Items.MAP)
                        .define('P', ItemTags.PLANKS)
                        .pattern("GGG")
                        .pattern("GMG")
                        .pattern("PIP")
                        .unlockedBy("has_map", has(Items.MAP))
                        .save(output);

                shaped(RecipeCategory.REDSTONE, ModBlocks.speaker.asBlock())
                        .define('I', Items.IRON_INGOT)
                        .define('N', Blocks.NOTE_BLOCK)
                        .define('W', ItemTags.WOOL)
                        .pattern("WIW")
                        .pattern("INI")
                        .pattern("WIW")
                        .unlockedBy("has_note_block", has(Blocks.NOTE_BLOCK))
                        .save(output);

                shaped(RecipeCategory.REDSTONE, ModBlocks.subwoofer.asBlock())
                        .define('I', Items.IRON_INGOT)
                        .define('N', Blocks.NOTE_BLOCK)
                        .define('W', ItemTags.WOOL)
                        .define('R', Items.REDSTONE)
                        .pattern("WWW")
                        .pattern("INI")
                        .pattern("RIR")
                        .unlockedBy("has_note_block", has(Blocks.NOTE_BLOCK))
                        .save(output);
            }
        };
    }

    @Override
    public @NonNull String getName() {
        return MediaBox.MOD_ID;
    }
}
