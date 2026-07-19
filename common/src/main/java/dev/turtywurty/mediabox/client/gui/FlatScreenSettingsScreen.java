package dev.turtywurty.mediabox.client.gui;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.network.SetScreenVideoUrlMessage;
import net.blay09.mods.balm.Balm;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.net.URI;

public final class FlatScreenSettingsScreen extends Screen {
    public static final Component TITLE = Component.translatable("screen." + MediaBox.MOD_ID + ".flat_screen");
    public static final Component URL_LABEL = Component.translatable("screen." + MediaBox.MOD_ID + ".flat_screen.url");
    public static final Component PLAY_BUTTON = Component.translatable("screen." + MediaBox.MOD_ID + ".flat_screen.play");
    public static final Component CANCEL_BUTTON = Component.translatable("gui.cancel");

    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 92;
    private static final int PANEL_PADDING = 12;

    private final BlockPos pos;
    private final String initialUrl;
    private EditBox urlField;
    private Button playButton;

    public FlatScreenSettingsScreen(BlockPos pos, String initialUrl) {
        super(TITLE);
        this.pos = pos.immutable();
        this.initialUrl = initialUrl;
    }

    @Override
    protected void init() {
        int panelWidth = panelWidth();
        int left = (this.width - panelWidth) / 2;
        int top = panelTop();

        this.urlField = new EditBox(
                this.font,
                left,
                top + 28,
                panelWidth,
                20,
                URL_LABEL
        );
        this.urlField.setMaxLength(2048);
        this.urlField.setValue(this.initialUrl);
        this.urlField.setResponder(_ -> updatePlayButton());
        addRenderableWidget(this.urlField);
        setInitialFocus(this.urlField);

        int buttonWidth = (panelWidth - 8) / 2;
        this.playButton = addRenderableWidget(Button.builder(PLAY_BUTTON, _ -> submit())
                .bounds(left, top + 60, buttonWidth, 20)
                .build());

        addRenderableWidget(Button.builder(CANCEL_BUTTON, _ -> onClose())
                .bounds(left + buttonWidth + 8, top + 60, buttonWidth, 20)
                .build());

        updatePlayButton();
    }

    private void updatePlayButton() {
        if (this.playButton != null) {
            this.playButton.active = isAllowedUrl(this.urlField.getValue().trim());
        }
    }

    private void submit() {
        String url = this.urlField.getValue().trim();
        if (!isAllowedUrl(url))
            return;

        Balm.networking().sendToServer(new SetScreenVideoUrlMessage(this.pos, url));
        onClose();
    }

    private static boolean isAllowedUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (IllegalArgumentException exception) {
            return false;
        }
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
        graphics.text(this.font, URL_LABEL, left, top + 17, 0xFFA0A0A0);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, this.width - 32);
    }

    private int panelTop() {
        return Math.max(16, (this.height - PANEL_HEIGHT) / 2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
