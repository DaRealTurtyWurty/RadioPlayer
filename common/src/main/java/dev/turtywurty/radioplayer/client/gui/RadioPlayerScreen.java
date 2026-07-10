package dev.turtywurty.radioplayer.client.gui;

import dev.turtywurty.radioplayer.Radioplayer;
import dev.turtywurty.radioplayer.SavedRadioStation;
import dev.turtywurty.radioplayer.network.UpdateRadioUrlMessage;
import dev.turtywurty.radioplayer.sound.LavaPlayerAudioStream;
import net.blay09.mods.balm.Balm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RadioPlayerScreen extends Screen {
    private static final Identifier CHECKMARK_SPRITE = Identifier.withDefaultNamespace("icon/checkmark");
    private static final Identifier EDIT_SPRITE = Radioplayer.id("radio/pencil");
    private static final Identifier DELETE_SPRITE = Radioplayer.id("radio/delete");
    private static final Identifier RESUME_SPRITE = Radioplayer.id("radio/resume");
    private static final Identifier STOP_SPRITE = Radioplayer.id("radio/stop");
    private static final int MAX_SAVED_STATIONS = 8;
    private static final int VISIBLE_STATION_ROWS = 4;
    private static final int STATION_ROW_HEIGHT = 20;
    private static final int STATION_ROW_GAP = 3;
    private static final int PANEL_PADDING = 12;
    private static final int CLOSE_BUTTON_SIZE = 16;
    private static final int PLAY_BUTTON_SIZE = 20;
    private static final int APPLY_BUTTON_SIZE = 20;
    private static final int STATION_ACTION_BUTTON_SIZE = 20;
    private static final int STATION_ACTION_BUTTON_GAP = 4;
    private static final int STATION_SCROLLBAR_WIDTH = 2;
    private static final int STATION_SCROLLBAR_GAP = 4;
    private static final SystemToast.SystemToastId INVALID_URL_TOAST_ID = new SystemToast.SystemToastId();

    public static final Component TITLE = Component.translatable("screen." + Radioplayer.MOD_ID + ".radio_player");
    public static final Component URL_LABEL = Component.translatable("screen." + Radioplayer.MOD_ID + ".radio_player.url");
    public static final Component NICKNAME_LABEL = Component.translatable("screen." + Radioplayer.MOD_ID + ".radio_player.nickname");
    public static final Component STATIONS_LABEL = Component.translatable("screen." + Radioplayer.MOD_ID + ".radio_player.stations");
    public static final Component EMPTY_STATIONS_LABEL = Component.translatable("screen." + Radioplayer.MOD_ID + ".radio_player.stations.empty");
    public static final Component APPLY_URL_BUTTON = Component.translatable("screen." + Radioplayer.MOD_ID + ".radio_player.url.apply");
    public static final Component ADD_STATION_BUTTON = Component.translatable("screen." + Radioplayer.MOD_ID + ".radio_player.station.add");
    public static final Component EDIT_STATION_BUTTON = Component.translatable("screen." + Radioplayer.MOD_ID + ".radio_player.station.edit");
    public static final Component REMOVE_STATION_BUTTON = Component.translatable("screen." + Radioplayer.MOD_ID + ".radio_player.station.remove");
    public static final Component PLAY_BUTTON = Component.translatable("screen." + Radioplayer.MOD_ID + ".radio_player.play");
    public static final Component STOP_BUTTON = Component.translatable("screen." + Radioplayer.MOD_ID + ".radio_player.stop");
    public static final Component INVALID_URL_TITLE = Component.translatable("screen." + Radioplayer.MOD_ID + ".radio_player.invalid_url");
    public static final Component INVALID_URL_DESCRIPTION = Component.translatable("screen." + Radioplayer.MOD_ID + ".radio_player.invalid_url.description");

    private final BlockPos pos;
    private String currentUrl;
    private boolean playing;
    private final List<SavedRadioStation> savedStations;
    private EditBox urlField;
    private EditBox nicknameField;
    private Button playPauseButton;
    private Button applyUrlButton;
    private Button addStationButton;
    private boolean validatingUrl;
    private int editingStationIndex = -1;
    private int firstVisibleStationIndex;

    public RadioPlayerScreen(BlockPos pos, String initialUrl, boolean playing, List<SavedRadioStation> savedStations) {
        super(TITLE);
        this.pos = pos.immutable();
        this.currentUrl = initialUrl;
        this.playing = playing;
        this.savedStations = new ArrayList<>(sanitizeSavedStations(savedStations));
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(420, this.width - 32);
        int left = (this.width - panelWidth) / 2;
        int top = panelTop();

        this.urlField = new EditBox(this.font, left, top + 28, panelWidth - APPLY_BUTTON_SIZE - PLAY_BUTTON_SIZE - 16, 20, URL_LABEL);
        this.urlField.setMaxLength(2048);
        this.urlField.setValue(this.currentUrl);
        this.urlField.setResponder(_ -> updateActionButtons());
        addRenderableWidget(this.urlField);
        setInitialFocus(this.urlField);

        this.nicknameField = new EditBox(this.font, left, top + 68, panelWidth, 20, NICKNAME_LABEL);
        this.nicknameField.setMaxLength(64);
        this.nicknameField.setValue(nicknameFor(this.currentUrl));
        this.nicknameField.setResponder(_ -> updateActionButtons());
        addRenderableWidget(this.nicknameField);

        addRenderableWidget(Button.builder(Component.literal("X"), _ -> onClose())
                .bounds(left + panelWidth - CLOSE_BUTTON_SIZE, top - 2, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE)
                .build());

        this.applyUrlButton = addRenderableWidget(createApplyUrlButton(left + panelWidth - APPLY_BUTTON_SIZE - PLAY_BUTTON_SIZE - 8, top + 28));
        this.applyUrlButton.active = canApplyCurrentUrl();

        this.playPauseButton = addRenderableWidget(createPlayPauseButton(left + panelWidth - PLAY_BUTTON_SIZE, top + 28));
        this.playPauseButton.active = canTogglePlaying();

        clampFirstVisibleStationIndex();
        int stationY = stationListTop(top);
        int lastVisibleStationIndex = Math.min(this.savedStations.size(), this.firstVisibleStationIndex + VISIBLE_STATION_ROWS);
        for (int index = this.firstVisibleStationIndex; index < lastVisibleStationIndex; index++) {
            SavedRadioStation station = this.savedStations.get(index);
            int visibleIndex = index - this.firstVisibleStationIndex;
            addStationRow(left, stationY + visibleIndex * (STATION_ROW_HEIGHT + STATION_ROW_GAP), panelWidth, index, station);
        }

        int actionsY = stationY + VISIBLE_STATION_ROWS * (STATION_ROW_HEIGHT + STATION_ROW_GAP) + 6;
        this.addStationButton = addRenderableWidget(Button.builder(ADD_STATION_BUTTON, _ -> addCurrentStation())
                .bounds(left, actionsY, panelWidth, 20)
                .build());
        this.addStationButton.active = canAddCurrentStation();
    }

    private void addStationRow(int left, int y, int panelWidth, int stationIndex, SavedRadioStation station) {
        int actionRight = left + panelWidth - STATION_SCROLLBAR_WIDTH - STATION_SCROLLBAR_GAP;
        int actionX = actionRight - STATION_ACTION_BUTTON_SIZE * 2 - STATION_ACTION_BUTTON_GAP;
        int nameWidth = actionX - left - STATION_ACTION_BUTTON_GAP;

        if (this.editingStationIndex == stationIndex) {
            var editBox = new EditBox(this.font, left, y, nameWidth, STATION_ROW_HEIGHT, NICKNAME_LABEL);
            editBox.setMaxLength(64);
            editBox.setValue(station.nickname());
            addRenderableWidget(editBox);
            setInitialFocus(editBox);

            Button applyButton = createStationActionButton(APPLY_URL_BUTTON, CHECKMARK_SPRITE, actionX, y, () -> applyStationEdit(stationIndex, editBox.getValue()));
            addRenderableWidget(applyButton);
        } else {
            addRenderableWidget(Button.builder(stationMessage(station, nameWidth - 12), _ -> selectStation(station))
                    .bounds(left, y, nameWidth, STATION_ROW_HEIGHT)
                    .build());

            addRenderableWidget(createStationActionButton(EDIT_STATION_BUTTON, EDIT_SPRITE, actionX, y, () -> editStation(stationIndex)));
        }

        addRenderableWidget(createStationActionButton(REMOVE_STATION_BUTTON, DELETE_SPRITE,
                actionX + STATION_ACTION_BUTTON_SIZE + STATION_ACTION_BUTTON_GAP, y, () -> deleteStation(stationIndex)));
    }

    private void togglePlaying() {
        if (!this.playing) {
            if (this.currentUrl.isBlank()) {
                showInvalidUrlToast();
                return;
            }

            this.playing = true;
            sendSettings();
            rebuildWidgets();
            return;
        }

        this.playing = false;
        sendSettings();

        int x = this.playPauseButton.getX();
        int y = this.playPauseButton.getY();

        removeWidget(this.playPauseButton);
        this.playPauseButton = addRenderableWidget(createPlayPauseButton(x, y));
    }

    private Button createPlayPauseButton(int x, int y) {
        Button button = SpriteIconButton.builder(playPauseMessage(), _ -> togglePlaying(), true)
                .size(PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE)
                .sprite(this.playing ? STOP_SPRITE : RESUME_SPRITE, 16, 16)
                .withTootip()
                .build();
        button.setX(x);
        button.setY(y);
        return button;
    }

    private Button createApplyUrlButton(int x, int y) {
        Button button = SpriteIconButton.builder(APPLY_URL_BUTTON, _ -> applyCurrentUrl(), true)
                .size(APPLY_BUTTON_SIZE, APPLY_BUTTON_SIZE)
                .sprite(CHECKMARK_SPRITE, 16, 16)
                .withTootip()
                .build();
        button.setX(x);
        button.setY(y);
        return button;
    }

    private Button createStationActionButton(Component message, Identifier sprite, int x, int y, Runnable onPress) {
        Button button = SpriteIconButton.builder(message, _ -> onPress.run(), true)
                .size(STATION_ACTION_BUTTON_SIZE, STATION_ACTION_BUTTON_SIZE)
                .sprite(sprite, 16, 16)
                .withTootip()
                .build();
        button.setX(x);
        button.setY(y);
        return button;
    }

    private Component playPauseMessage() {
        return this.playing ? STOP_BUTTON : PLAY_BUTTON;
    }

    private void sendSettings() {
        Balm.networking().sendToServer(new UpdateRadioUrlMessage(this.pos, this.currentUrl, this.playing, this.savedStations));
    }

    private void selectStation(SavedRadioStation station) {
        this.currentUrl = station.url();
        this.urlField.setValue(station.url());
        this.nicknameField.setValue(station.nickname());
        sendSettings();
    }

    private void applyCurrentUrl() {
        validateCurrentUrl(() -> {
            this.currentUrl = this.urlField.getValue().trim();
            sendSettings();
            rebuildWidgets();
        });
    }

    private void addCurrentStation() {
        String stationUrl = this.currentUrl.trim();
        if (stationUrl.isBlank())
            return;

        SavedRadioStation station = SavedRadioStation.of(this.nicknameField.getValue(), stationUrl);
        int existingIndex = indexOfStation(stationUrl);
        if (existingIndex >= 0) {
            this.savedStations.set(existingIndex, station);
            sendSettings();
            rebuildWidgets();
            return;
        }

        if (this.savedStations.size() < MAX_SAVED_STATIONS) {
            this.savedStations.add(station);
            this.firstVisibleStationIndex = maxFirstVisibleStationIndex();
            sendSettings();
            rebuildWidgets();
        }
    }

    private void editStation(int stationIndex) {
        this.editingStationIndex = stationIndex;
        scrollStationIntoView(stationIndex);
        rebuildWidgets();
    }

    private void applyStationEdit(int stationIndex, String nickname) {
        if (stationIndex < 0 || stationIndex >= this.savedStations.size())
            return;

        SavedRadioStation station = this.savedStations.get(stationIndex);
        SavedRadioStation editedStation = SavedRadioStation.of(nickname, station.url());
        this.savedStations.set(stationIndex, editedStation);
        if (editedStation.url().equals(this.currentUrl)) {
            this.nicknameField.setValue(editedStation.nickname());
        }

        this.editingStationIndex = -1;
        sendSettings();
        rebuildWidgets();
    }

    private void deleteStation(int stationIndex) {
        if (stationIndex < 0 || stationIndex >= this.savedStations.size())
            return;

        this.savedStations.remove(stationIndex);
        if (this.editingStationIndex == stationIndex) {
            this.editingStationIndex = -1;
        } else if (this.editingStationIndex > stationIndex) {
            this.editingStationIndex--;
        }

        clampFirstVisibleStationIndex();
        sendSettings();
        rebuildWidgets();
    }

    private void updateActionButtons() {
        if (this.applyUrlButton != null) {
            this.applyUrlButton.active = canApplyCurrentUrl();
        }

        if (this.addStationButton != null) {
            this.addStationButton.active = canAddCurrentStation();
        }

        if (this.playPauseButton != null) {
            this.playPauseButton.active = canTogglePlaying();
        }
    }

    private boolean canApplyCurrentUrl() {
        String station = this.urlField == null ? this.currentUrl.trim() : this.urlField.getValue().trim();
        return !this.validatingUrl && !station.isBlank() && !station.equals(this.currentUrl);
    }

    private boolean canAddCurrentStation() {
        String station = this.currentUrl.trim();
        return !this.validatingUrl && isDraftCurrentUrl() && !station.isBlank() &&
                (indexOfStation(station) >= 0 || this.savedStations.size() < MAX_SAVED_STATIONS);
    }

    private boolean canTogglePlaying() {
        return !this.validatingUrl && (this.playing || isDraftCurrentUrl() && !this.currentUrl.isBlank());
    }

    private boolean isDraftCurrentUrl() {
        return this.urlField == null || this.urlField.getValue().trim().equals(this.currentUrl);
    }

    private void validateCurrentUrl(Runnable onValid) {
        String station = this.urlField.getValue().trim();
        if (!isValidUrlFormat(station)) {
            showInvalidUrlToast();
            return;
        }

        this.validatingUrl = true;
        updateActionButtons();
        CompletableFuture.runAsync(() -> {
            try {
                LavaPlayerAudioStream.validate(station);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }, Util.nonCriticalIoPool()).whenComplete((_, throwable) ->
                Minecraft.getInstance().execute(() -> {
                    this.validatingUrl = false;
                    if (throwable != null) {
                        Radioplayer.LOGGER.warn("Radio URL validation failed for {}", station, throwable);
                        showInvalidUrlToast();
                        updateActionButtons();
                        return;
                    }

                    onValid.run();
                }));
    }

    private static boolean isValidUrlFormat(String url) {
        try {
            var uri = new URI(url);
            String scheme = uri.getScheme();
            return uri.getHost() != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static void showInvalidUrlToast() {
        SystemToast.addOrUpdate(
                Minecraft.getInstance().gui.toastManager(),
                INVALID_URL_TOAST_ID,
                INVALID_URL_TITLE,
                INVALID_URL_DESCRIPTION);
    }

    private int indexOfStation(String url) {
        for (int index = 0; index < this.savedStations.size(); index++) {
            if (this.savedStations.get(index).url().equals(url))
                return index;
        }

        return -1;
    }

    private String nicknameFor(String url) {
        int stationIndex = indexOfStation(url);
        return stationIndex >= 0 ? this.savedStations.get(stationIndex).nickname() : "";
    }

    private Component stationMessage(SavedRadioStation station, int maxWidth) {
        String label = station.nickname();
        String visibleStation = this.font.plainSubstrByWidth(label, maxWidth);
        if (visibleStation.length() < label.length()) {
            visibleStation = this.font.plainSubstrByWidth(label, maxWidth - this.font.width("...")) + "...";
        }

        return Component.literal(visibleStation);
    }

    private int panelTop() {
        return Math.max(12, (this.height - panelHeight()) / 2);
    }

    private int panelHeight() {
        return 158 + VISIBLE_STATION_ROWS * (STATION_ROW_HEIGHT + STATION_ROW_GAP);
    }

    private int stationListTop(int panelTop) {
        return panelTop + 112;
    }

    private int stationListHeight() {
        return VISIBLE_STATION_ROWS * (STATION_ROW_HEIGHT + STATION_ROW_GAP) - STATION_ROW_GAP;
    }

    private int maxFirstVisibleStationIndex() {
        return Math.max(0, this.savedStations.size() - VISIBLE_STATION_ROWS);
    }

    private void clampFirstVisibleStationIndex() {
        this.firstVisibleStationIndex = Math.clamp(this.firstVisibleStationIndex, 0, maxFirstVisibleStationIndex());
    }

    private void scrollStationIntoView(int stationIndex) {
        if (stationIndex < this.firstVisibleStationIndex) {
            this.firstVisibleStationIndex = stationIndex;
        } else if (stationIndex >= this.firstVisibleStationIndex + VISIBLE_STATION_ROWS) {
            this.firstVisibleStationIndex = stationIndex - VISIBLE_STATION_ROWS + 1;
        }

        clampFirstVisibleStationIndex();
    }

    private static List<SavedRadioStation> sanitizeSavedStations(List<SavedRadioStation> stations) {
        List<SavedRadioStation> sanitizedStations = new ArrayList<>();
        if (stations == null)
            return sanitizedStations;

        for (SavedRadioStation station : stations) {
            if (station == null)
                continue;

            SavedRadioStation sanitizedStation = SavedRadioStation.of(station.nickname(), station.url());
            if (!sanitizedStation.url().isBlank() && sanitizedStations.stream().noneMatch(savedStation -> savedStation.url().equals(sanitizedStation.url()))) {
                sanitizedStations.add(sanitizedStation);
            }

            if (sanitizedStations.size() >= MAX_SAVED_STATIONS)
                break;
        }

        return sanitizedStations;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int panelWidth = Math.min(420, this.width - 32);
        int left = (this.width - panelWidth) / 2;
        int top = panelTop();
        int panelHeight = panelHeight();

        graphics.fill(left - PANEL_PADDING, top - PANEL_PADDING, left + panelWidth + PANEL_PADDING, top + panelHeight, 0xC0101010);
        graphics.outline(left - PANEL_PADDING, top - PANEL_PADDING, panelWidth + PANEL_PADDING * 2, panelHeight + PANEL_PADDING, 0xFF707070);
        graphics.centeredText(this.font, this.title, this.width / 2, top, 0xFFFFFFFF);
        graphics.text(this.font, URL_LABEL, left, top + 17, 0xFFA0A0A0);
        graphics.text(this.font, NICKNAME_LABEL, left, top + 57, 0xFFA0A0A0);
        graphics.text(this.font, STATIONS_LABEL, left, top + 100, 0xFFA0A0A0);
        if (this.savedStations.isEmpty()) {
            graphics.centeredText(this.font, EMPTY_STATIONS_LABEL, this.width / 2, top + 118, 0xFF808080);
        }

        extractStationScrollbar(graphics, left, top, panelWidth);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void extractStationScrollbar(GuiGraphicsExtractor graphics, int left, int top, int panelWidth) {
        if (this.savedStations.size() <= VISIBLE_STATION_ROWS)
            return;

        int trackX = left + panelWidth - STATION_SCROLLBAR_WIDTH;
        int trackY = stationListTop(top);
        int trackHeight = stationListHeight();
        int thumbHeight = Math.max(10, trackHeight * VISIBLE_STATION_ROWS / this.savedStations.size());
        int thumbRange = trackHeight - thumbHeight;
        int thumbY = trackY + (maxFirstVisibleStationIndex() == 0 ? 0 : thumbRange * this.firstVisibleStationIndex / maxFirstVisibleStationIndex());

        graphics.fill(trackX, trackY, trackX + STATION_SCROLLBAR_WIDTH, trackY + trackHeight, 0xFF303030);
        graphics.fill(trackX, thumbY, trackX + STATION_SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFFA0A0A0);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int panelWidth = Math.min(420, this.width - 32);
        int left = (this.width - panelWidth) / 2;
        int top = panelTop();
        int stationY = stationListTop(top);

        if (this.savedStations.size() > VISIBLE_STATION_ROWS &&
                mouseX >= left && mouseX <= left + panelWidth &&
                mouseY >= stationY && mouseY <= stationY + stationListHeight()) {
            this.firstVisibleStationIndex -= (int) Math.signum(scrollY);
            clampFirstVisibleStationIndex();
            rebuildWidgets();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
