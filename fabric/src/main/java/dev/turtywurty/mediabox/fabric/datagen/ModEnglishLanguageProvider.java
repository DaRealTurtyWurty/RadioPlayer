package dev.turtywurty.mediabox.fabric.datagen;

import dev.turtywurty.mediabox.block.ModBlocks;
import dev.turtywurty.mediabox.client.gui.FlatScreenSettingsScreen;
import dev.turtywurty.mediabox.client.gui.GlobeScreen;
import dev.turtywurty.mediabox.client.gui.RadioPlayerScreen;
import dev.turtywurty.mediabox.client.gui.widget.StationListWidget;
import dev.turtywurty.mediabox.client.ytdlp.YtDlpOnboarding;
import dev.turtywurty.mediabox.client.ytdlp.YtDlpDownloadScreen;
import dev.turtywurty.mediabox.item.ModItems;
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
        translationBuilder.add(ModBlocks.cablePort.asBlock(), "Cable Port");
        translationBuilder.add(ModItems.audioCable.asItem(), "Audio Cable");
        translationBuilder.add(ModItems.videoCable.asItem(), "Video Cable");
        translationBuilder.add(ModBlocks.flatScreen.asBlock(), "Flat Screen");
        add(translationBuilder, FlatScreenSettingsScreen.TITLE, "Flat Screen");
        add(translationBuilder, FlatScreenSettingsScreen.URL_LABEL, "Video URL");
        add(translationBuilder, FlatScreenSettingsScreen.PLAY_BUTTON, "Play");
        add(translationBuilder, YtDlpOnboarding.TITLE, "Install optional yt-dlp?");
        add(translationBuilder, YtDlpOnboarding.DESCRIPTION,
                "MediaBox can download yt-dlp from its official GitHub release to support video page URLs. "
                        + "The download is up to 40 MB and is separately licensed under GPLv3+. "
                        + "If declined, MediaBox will not download it and will not ask again.");
        add(translationBuilder, YtDlpOnboarding.DOWNLOAD_BUTTON, "Download");
        add(translationBuilder, YtDlpOnboarding.DECLINE_BUTTON, "Don't ask again");
        add(translationBuilder, YtDlpDownloadScreen.TITLE, "Installing yt-dlp");
        add(translationBuilder, YtDlpDownloadScreen.CHECKING, "Checking existing installation...");
        add(translationBuilder, YtDlpDownloadScreen.VERIFYING, "Verifying existing installation...");
        translationBuilder.add("screen.mediabox.yt_dlp.progress.downloading", "Downloading from GitHub...");
        translationBuilder.add("screen.mediabox.yt_dlp.progress.bytes", "%s / %s (%s%%)");
        add(translationBuilder, YtDlpDownloadScreen.FINALIZING, "Finalizing installation...");
        add(translationBuilder, YtDlpDownloadScreen.COMPLETE, "yt-dlp installed successfully.");
        add(translationBuilder, YtDlpDownloadScreen.FAILED,
                "Download failed. MediaBox will retry next launch.");
    }

    private static void add(TranslationBuilder translationBuilder, Component component, String translation) {
        if (component.getContents() instanceof TranslatableContents translatableContents) {
            translationBuilder.add(translatableContents.getKey(), translation);
        }
    }
}
