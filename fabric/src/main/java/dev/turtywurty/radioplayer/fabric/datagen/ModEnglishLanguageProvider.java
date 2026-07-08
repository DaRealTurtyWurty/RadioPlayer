package dev.turtywurty.radioplayer.fabric.datagen;

import dev.turtywurty.radioplayer.block.ModBlocks;
import dev.turtywurty.radioplayer.client.gui.RadioPlayerScreen;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public class ModEnglishLanguageProvider extends FabricLanguageProvider {
    protected ModEnglishLanguageProvider(FabricPackOutput packOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(packOutput, "en_us", registryLookup);
    }

    @Override
    public void generateTranslations(HolderLookup.@NonNull Provider registryLookup, @NonNull TranslationBuilder translationBuilder) {
        translationBuilder.add(ModBlocks.radioPlayer.asBlock(), "Radio Player");
        add(translationBuilder, RadioPlayerScreen.TITLE, "Radio Player");
        add(translationBuilder, RadioPlayerScreen.URL_LABEL, "Stream URL");
        add(translationBuilder, RadioPlayerScreen.NICKNAME_LABEL, "Nickname");
        add(translationBuilder, RadioPlayerScreen.STATIONS_LABEL, "Saved Stations");
        add(translationBuilder, RadioPlayerScreen.EMPTY_STATIONS_LABEL, "No saved stations");
        add(translationBuilder, RadioPlayerScreen.APPLY_URL_BUTTON, "Apply");
        add(translationBuilder, RadioPlayerScreen.ADD_STATION_BUTTON, "Save Station");
        add(translationBuilder, RadioPlayerScreen.EDIT_STATION_BUTTON, "Edit");
        add(translationBuilder, RadioPlayerScreen.REMOVE_STATION_BUTTON, "Delete");
        add(translationBuilder, RadioPlayerScreen.PLAY_BUTTON, "Play");
        add(translationBuilder, RadioPlayerScreen.STOP_BUTTON, "Stop");
        add(translationBuilder, RadioPlayerScreen.INVALID_URL_TITLE, "Invalid radio URL");
        add(translationBuilder, RadioPlayerScreen.INVALID_URL_DESCRIPTION, "That URL does not provide a playable audio stream.");
    }

    private static void add(TranslationBuilder translationBuilder, Component component, String translation) {
        if (component.getContents() instanceof TranslatableContents translatableContents) {
            translationBuilder.add(translatableContents.getKey(), translation);
        }
    }
}
