package dev.turtywurty.mediabox.client.ytdlp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class YtDlpOnboarding {
    public static final Component TITLE = Component.translatable("screen.mediabox.yt_dlp.title");
    public static final Component DESCRIPTION = Component.translatable("screen.mediabox.yt_dlp.description");
    public static final Component DOWNLOAD_BUTTON = Component.translatable("screen.mediabox.yt_dlp.download");
    public static final Component DECLINE_BUTTON = Component.translatable("screen.mediabox.yt_dlp.decline");

    private static boolean promptOpened;
    private static boolean acceptedInstallStarted;
    private static CompletableFuture<Optional<Path>> systemDetection;

    private YtDlpOnboarding() {
    }

    public static void tick(Minecraft minecraft) {
        Path gameDirectory = minecraft.gameDirectory.toPath();
        if (systemDetection == null) {
            systemDetection = YtDlpManager.detectSystemInstallation(gameDirectory);
            return;
        }
        if (!systemDetection.isDone())
            return;
        if (systemDetection.getNow(Optional.empty()).isPresent())
            return;

        YtDlpManager.Consent consent = YtDlpManager.consent(gameDirectory);
        if (consent == YtDlpManager.Consent.DECLINED)
            return;

        if (consent == YtDlpManager.Consent.ACCEPTED) {
            if (!acceptedInstallStarted && minecraft.gui.screen() instanceof TitleScreen) {
                acceptedInstallStarted = true;
                YtDlpManager.installIfAccepted(gameDirectory);
            }
            return;
        }

        if (promptOpened || !(minecraft.gui.screen() instanceof TitleScreen))
            return;

        promptOpened = true;
        Screen parent = minecraft.gui.screen();
        minecraft.gui.setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                acceptedInstallStarted = true;
                minecraft.gui.setScreen(new YtDlpDownloadScreen(parent, gameDirectory));
            } else {
                minecraft.gui.setScreen(parent);
                YtDlpManager.decline(gameDirectory);
            }
        }, TITLE, DESCRIPTION, DOWNLOAD_BUTTON, DECLINE_BUTTON));
    }
}
