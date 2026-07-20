package dev.turtywurty.mediabox.client.ytdlp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.Locale;

public final class YtDlpDownloadScreen extends Screen {
    public static final Component TITLE = Component.translatable("screen.mediabox.yt_dlp.progress.title");
    public static final Component CHECKING = Component.translatable("screen.mediabox.yt_dlp.progress.checking");
    public static final Component VERIFYING = Component.translatable("screen.mediabox.yt_dlp.progress.verifying");
    public static final Component FINALIZING = Component.translatable("screen.mediabox.yt_dlp.progress.finalizing");
    public static final Component COMPLETE = Component.translatable("screen.mediabox.yt_dlp.progress.complete");
    public static final Component FAILED = Component.translatable("screen.mediabox.yt_dlp.progress.failed");

    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 104;
    private static final int PANEL_PADDING = 12;
    private static final int PROGRESS_BAR_HEIGHT = 12;

    private final Screen parent;
    private final Path gameDirectory;
    private volatile YtDlpManager.InstallStage stage = YtDlpManager.InstallStage.CHECKING;
    private volatile long completedBytes;
    private volatile long totalBytes;
    private boolean started;
    private boolean finished;
    private Button doneButton;

    public YtDlpDownloadScreen(Screen parent, Path gameDirectory) {
        super(TITLE);
        this.parent = parent;
        this.gameDirectory = gameDirectory;
    }

    @Override
    protected void init() {
        int panelWidth = panelWidth();
        int left = (this.width - panelWidth) / 2;
        int top = panelTop();
        this.doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), _ -> onClose())
                .bounds(left + (panelWidth - 100) / 2, top + 80, 100, 20)
                .build());
        this.doneButton.visible = this.finished;

        if (this.started)
            return;

        this.started = true;
        YtDlpManager.acceptAndInstall(this.gameDirectory, this::updateProgress)
                .thenAccept(result -> Minecraft.getInstance().execute(() -> {
                    this.finished = true;
                    if (this.doneButton != null)
                        this.doneButton.visible = true;
                }));
    }

    private void updateProgress(YtDlpManager.InstallStage stage, long completedBytes, long totalBytes) {
        this.stage = stage;
        this.completedBytes = completedBytes;
        this.totalBytes = totalBytes;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int panelWidth = panelWidth();
        int left = (this.width - panelWidth) / 2;
        int top = panelTop();

        graphics.fill(
                left - PANEL_PADDING,
                top - PANEL_PADDING,
                left + panelWidth + PANEL_PADDING,
                top + PANEL_HEIGHT + PANEL_PADDING,
                0xD0101010
        );
        graphics.outline(
                left - PANEL_PADDING,
                top - PANEL_PADDING,
                panelWidth + PANEL_PADDING * 2,
                PANEL_HEIGHT + PANEL_PADDING * 2,
                0xFF707070
        );
        graphics.centeredText(this.font, this.title, this.width / 2, top, 0xFFFFFFFF);
        graphics.centeredText(this.font, status(), this.width / 2, top + 21, 0xFFA0A0A0);

        int progressTop = top + 43;
        graphics.fill(left, progressTop, left + panelWidth, progressTop + PROGRESS_BAR_HEIGHT, 0xFF303030);
        int fillWidth = (int) Math.round(panelWidth * progress());
        if (fillWidth > 0) {
            graphics.fill(left, progressTop, left + fillWidth, progressTop + PROGRESS_BAR_HEIGHT, 0xFF3FAE5A);
        }
        graphics.outline(left, progressTop, panelWidth, PROGRESS_BAR_HEIGHT, 0xFF909090);

        if (this.stage == YtDlpManager.InstallStage.DOWNLOADING
                || this.stage == YtDlpManager.InstallStage.VERIFYING) {
            graphics.centeredText(this.font, progressText(), this.width / 2, top + 61, 0xFFFFFFFF);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private Component status() {
        return switch (this.stage) {
            case CHECKING -> CHECKING;
            case VERIFYING -> VERIFYING;
            case DOWNLOADING -> Component.translatable("screen.mediabox.yt_dlp.progress.downloading");
            case FINALIZING -> FINALIZING;
            case COMPLETE -> COMPLETE;
            case FAILED -> FAILED;
        };
    }

    private Component progressText() {
        int percentage = (int) Math.round(progress() * 100.0);
        return Component.translatable(
                "screen.mediabox.yt_dlp.progress.bytes",
                formatMiB(this.completedBytes),
                formatMiB(this.totalBytes),
                percentage
        );
    }

    private double progress() {
        if (this.stage == YtDlpManager.InstallStage.COMPLETE
                || this.stage == YtDlpManager.InstallStage.FINALIZING) {
            return 1.0;
        }
        if (this.totalBytes <= 0)
            return 0.0;

        return Math.clamp((double) this.completedBytes / this.totalBytes, 0.0, 1.0);
    }

    private static String formatMiB(long bytes) {
        return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, this.width - 32);
    }

    private int panelTop() {
        return Math.max(16, (this.height - PANEL_HEIGHT) / 2);
    }

    @Override
    public void onClose() {
        if (this.finished)
            Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
