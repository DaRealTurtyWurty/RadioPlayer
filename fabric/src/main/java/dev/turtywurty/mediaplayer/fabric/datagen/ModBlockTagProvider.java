package dev.turtywurty.mediaplayer.fabric.datagen;

import dev.turtywurty.mediaplayer.block.ModBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.BlockTags;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagProvider extends FabricTagsProvider.BlockTagsProvider {
    public ModBlockTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.@NonNull Provider arg) {
        builder(BlockTags.MINEABLE_WITH_AXE)
                .add(ModBlocks.radioPlayer.asResourceKey())
                .add(ModBlocks.globe.asResourceKey())
                .add(ModBlocks.speaker.asResourceKey())
                .add(ModBlocks.bassReflexSpeaker.asResourceKey())
                .add(ModBlocks.hornSpeaker.asResourceKey())
                .add(ModBlocks.bookshelfSpeaker.asResourceKey())
                .add(ModBlocks.floorStandingSpeaker.asResourceKey())
                .add(ModBlocks.subwoofer.asResourceKey());
    }
}
