package dev.turtywurty.mediabox.fabric.datagen;

import dev.turtywurty.mediabox.block.ModBlocks;
import dev.turtywurty.mediabox.client.gui.GlobeScreen;
import dev.turtywurty.mediabox.client.gui.RadioPlayerScreen;
import dev.turtywurty.mediabox.client.gui.widget.StationListWidget;
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
        translationBuilder.add("mediabox.configuration.title", "Media Player Configuration");
        translationBuilder.add("mediabox.configuration", "General");
        translationBuilder.add("mediabox.configuration.ffmpegExecutablePath", "FFmpeg Executable Path");
        translationBuilder.add("mediabox.configuration.ffmpegExecutablePath.tooltip",
                "Optional absolute path to FFmpeg. Used only when Lavaplayer cannot play a stream directly.");
        translationBuilder.add(ModBlocks.globe.asBlock(), "Globe");
        add(translationBuilder, GlobeScreen.TITLE, "Globe");
        add(translationBuilder, StationListWidget.STATIONS_LABEL, "Stations");
        add(translationBuilder, StationListWidget.EMPTY_STATIONS_LABEL, "No stations");
        add(translationBuilder, StationListWidget.SEARCH_LABEL, "Search");
        translationBuilder.add(ModBlocks.speaker.asBlock(), "Speaker");
        translationBuilder.add(ModBlocks.bassReflexSpeaker.asBlock(), "Bass Reflex Speaker");
        translationBuilder.add(ModBlocks.hornSpeaker.asBlock(), "Horn Speaker");
        translationBuilder.add(ModBlocks.bookshelfSpeaker.asBlock(), "Bookshelf Speaker");
        translationBuilder.add(ModBlocks.floorStandingSpeaker.asBlock(), "Floor-Standing Speaker");
        translationBuilder.add(ModBlocks.subwoofer.asBlock(), "Subwoofer");
    }

    private static void add(TranslationBuilder translationBuilder, Component component, String translation) {
        if (component.getContents() instanceof TranslatableContents translatableContents) {
            translationBuilder.add(translatableContents.getKey(), translation);
        }
    }
}
