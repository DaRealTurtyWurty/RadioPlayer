package dev.turtywurty.mediabox.fabric.datagen;

import dev.turtywurty.mediabox.block.ModBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.core.HolderLookup;

import java.util.concurrent.CompletableFuture;

public class ModBlockLootTableProvider extends FabricBlockLootSubProvider {
    protected ModBlockLootTableProvider(FabricPackOutput dataOutput, CompletableFuture<HolderLookup.Provider> provider) {
        super(dataOutput, provider);
    }

    @Override
    public void generate() {
        dropSelf(ModBlocks.radioPlayer.asBlock());
        dropSelf(ModBlocks.globe.asBlock());
        dropSelf(ModBlocks.speaker.asBlock());
        dropSelf(ModBlocks.bassReflexSpeaker.asBlock());
        dropSelf(ModBlocks.hornSpeaker.asBlock());
        dropSelf(ModBlocks.bookshelfSpeaker.asBlock());
        dropSelf(ModBlocks.floorStandingSpeaker.asBlock());
        dropSelf(ModBlocks.subwoofer.asBlock());
    }
}
