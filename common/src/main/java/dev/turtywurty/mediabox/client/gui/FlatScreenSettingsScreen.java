package dev.turtywurty.mediabox.client.gui;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.client.video.ClientScreenPlaybackState;
import dev.turtywurty.mediabox.client.video.ClientVideoManager;
import dev.turtywurty.mediabox.client.video.ClientVideoSession;
import dev.turtywurty.mediabox.network.JumpScreenPlaybackToPresentMessage;
import dev.turtywurty.mediabox.network.SeekScreenPlaybackAbsoluteMessage;
import dev.turtywurty.mediabox.network.SeekScreenPlaybackRelativeMessage;
import dev.turtywurty.mediabox.network.SetScreenVideoUrlMessage;
import dev.turtywurty.mediabox.network.ToggleScreenPlaybackMessage;
import dev.turtywurty.mediabox.video.PlaybackStatus;
import dev.turtywurty.mediabox.video.VideoSessionState;
import dev.turtywurty.mediabox.video.VideoSource;
import net.blay09.mods.balm.Balm;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.DoubleConsumer;

public final class FlatScreenSettingsScreen extends Screen {
    private static final String TRANSLATION_PREFIX = "screen." + MediaBox.MOD_ID + ".flat_screen";

    public static final Component TITLE = Component.translatable(TRANSLATION_PREFIX);
    public static final Component URL_LABEL = Component.translatable(TRANSLATION_PREFIX + ".url");
    public static final Component PLAY_BUTTON = Component.translatable(TRANSLATION_PREFIX + ".play");
    public static final Component PAUSE_BUTTON = Component.translatable(TRANSLATION_PREFIX + ".pause");
    public static final Component RESUME_BUTTON = Component.translatable(TRANSLATION_PREFIX + ".resume");
    public static final Component SEEK_BACKWARD_BUTTON = Component.translatable(TRANSLATION_PREFIX + ".seek_backward");
    public static final Component SEEK_FORWARD_BUTTON = Component.translatable(TRANSLATION_PREFIX + ".seek_forward");
    public static final Component JUMP_TO_PRESENT_BUTTON = Component.translatable(TRANSLATION_PREFIX + ".jump_to_present");
    public static final Component NO_MEDIA = Component.translatable(TRANSLATION_PREFIX + ".no_media");
    public static final Component LOADING_MEDIA = Component.translatable(TRANSLATION_PREFIX + ".loading");
    public static final Component LIVE_MEDIA = Component.translatable(TRANSLATION_PREFIX + ".live");
    public static final Component PROGRESS = Component.translatable(TRANSLATION_PREFIX + ".progress");
    public static final Component CANCEL_BUTTON = Component.translatable("gui.cancel");

    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 124;
    private static final int PANEL_PADDING = 12;
    private static final int CONTROL_GAP = 6;
    private static final int SEEK_BUTTON_WIDTH = 64;
    private static final int PAUSE_BUTTON_WIDTH = 100;
    private static final int JUMP_BUTTON_WIDTH = 120;
    private static final double SEEK_STEP_SECONDS = 5.0;

    private final BlockPos pos;
    private final UUID screenId;
    private final String initialUrl;
    private EditBox urlField;
    private Button playButton;
    private Button pauseButton;
    private Button seekBackwardButton;
    private Button seekForwardButton;
    private Button jumpToPresentButton;
    private PlaybackProgressSlider progressSlider;

    public FlatScreenSettingsScreen(BlockPos pos, UUID screenId, String initialUrl) {
        super(TITLE);
        this.pos = pos.immutable();
        this.screenId = screenId;
        this.initialUrl = initialUrl;
    }

    @Override
    protected void init() {
        int panelWidth = panelWidth();
        int left = (this.width - panelWidth) / 2;
        int top = panelTop();

        this.urlField = new EditBox(this.font, left, top + 28, panelWidth, 20, URL_LABEL);
        this.urlField.setMaxLength(2048);
        this.urlField.setValue(this.initialUrl);
        this.urlField.setResponder(_ -> updatePlayButton());
        addRenderableWidget(this.urlField);
        setInitialFocus(this.urlField);

        int topButtonWidth = (panelWidth - 8) / 2;
        this.playButton = addRenderableWidget(Button.builder(PLAY_BUTTON, _ -> submitUrl())
                .bounds(left, top + 52, topButtonWidth, 20)
                .build());
        addRenderableWidget(Button.builder(CANCEL_BUTTON, _ -> onClose())
                .bounds(left + topButtonWidth + 8, top + 52, topButtonWidth, 20)
                .build());

        this.progressSlider = addRenderableWidget(new PlaybackProgressSlider(
                left,
                top + 80,
                panelWidth,
                this::seekAbsolute
        ));

        int controlsWidth = SEEK_BUTTON_WIDTH * 2 + PAUSE_BUTTON_WIDTH + CONTROL_GAP * 2;
        int controlsLeft = left + (panelWidth - controlsWidth) / 2;

        this.seekBackwardButton = addRenderableWidget(Button.builder(
                        SEEK_BACKWARD_BUTTON,
                        _ -> seekRelative(-SEEK_STEP_SECONDS))
                .bounds(controlsLeft, top + 104, SEEK_BUTTON_WIDTH, 20)
                .build());
        this.pauseButton = addRenderableWidget(Button.builder(
                        PAUSE_BUTTON,
                        _ -> togglePaused())
                .bounds(controlsLeft + SEEK_BUTTON_WIDTH + CONTROL_GAP,
                        top + 104, PAUSE_BUTTON_WIDTH, 20)
                .build());
        this.seekForwardButton = addRenderableWidget(Button.builder(
                        SEEK_FORWARD_BUTTON,
                        _ -> seekRelative(SEEK_STEP_SECONDS))
                .bounds(controlsLeft + SEEK_BUTTON_WIDTH + CONTROL_GAP + PAUSE_BUTTON_WIDTH + CONTROL_GAP,
                        top + 104, SEEK_BUTTON_WIDTH, 20)
                .build());
        this.jumpToPresentButton = addRenderableWidget(Button.builder(
                        JUMP_TO_PRESENT_BUTTON,
                        _ -> jumpToPresent())
                .bounds(left + panelWidth - JUMP_BUTTON_WIDTH, top + 104, JUMP_BUTTON_WIDTH, 20)
                .build());

        updatePlayButton();
        refreshTransportControls();
    }

    @Override
    public void tick() {
        super.tick();
        refreshTransportControls();
    }

    private void updatePlayButton() {
        if (this.playButton != null)
            this.playButton.active = isAllowedUrl(this.urlField.getValue().trim());
    }

    private void refreshTransportControls() {
        if (this.progressSlider == null)
            return;

        Optional<VideoSessionState> state = ClientScreenPlaybackState.get(this.screenId);
        Optional<ClientVideoSession> clientSession = state
                .flatMap(value -> ClientVideoManager.get(value.sessionId()));
        OptionalDouble duration = clientSession
                .map(ClientVideoSession::durationSeconds)
                .orElseGet(OptionalDouble::empty);
        boolean live = state
                .map(value -> value.source() instanceof VideoSource.LiveStream)
                .orElse(false)
                || clientSession.isPresent() && duration.isEmpty();

        boolean hasMedia = state.isPresent() && state.get().status() != PlaybackStatus.STOPPED;
        double position = state.map(this::positionSeconds).orElse(0.0);
        if (duration.isPresent()) {
            double length = duration.getAsDouble();
            if (state.orElseThrow().looping())
                position = wrapPosition(position, length);
            else
                position = Math.clamp(position, 0.0, length);
        }

        this.progressSlider.setPlayback(position, duration, hasMedia, live, clientSession.isPresent());
        this.pauseButton.active = hasMedia;
        this.pauseButton.setMessage(state
                .filter(value -> value.status() == PlaybackStatus.PAUSED)
                .isPresent() ? RESUME_BUTTON : PAUSE_BUTTON);
        this.seekBackwardButton.active = hasMedia;
        this.seekForwardButton.active = hasMedia;
        this.jumpToPresentButton.visible = live;
        this.jumpToPresentButton.active = hasMedia && live;

        int panelWidth = panelWidth();
        int left = (this.width - panelWidth) / 2;
        if (live) {
            layoutLiveControls(left, panelWidth);
        } else {
            layoutOnDemandControls(left, panelWidth);
        }
    }

    private void layoutOnDemandControls(int left, int panelWidth) {
        int desiredWidth = SEEK_BUTTON_WIDTH * 2 + PAUSE_BUTTON_WIDTH + CONTROL_GAP * 2;
        int seekWidth = SEEK_BUTTON_WIDTH;
        int pauseWidth = PAUSE_BUTTON_WIDTH;
        if (panelWidth < desiredWidth) {
            double scale = (panelWidth - CONTROL_GAP * 2) / (double) (SEEK_BUTTON_WIDTH * 2 + PAUSE_BUTTON_WIDTH);
            seekWidth = Math.max(32, (int) Math.floor(SEEK_BUTTON_WIDTH * scale));
            pauseWidth = panelWidth - CONTROL_GAP * 2 - seekWidth * 2;
            desiredWidth = panelWidth;
        }

        int x = left + (panelWidth - desiredWidth) / 2;
        int y = panelTop() + 104;
        this.seekBackwardButton.setRectangle(seekWidth, 20, x, y);
        this.pauseButton.setRectangle(pauseWidth, 20, x + seekWidth + CONTROL_GAP, y);
        this.seekForwardButton.setRectangle(
                seekWidth,
                20,
                x + seekWidth + CONTROL_GAP + pauseWidth + CONTROL_GAP,
                y
        );
    }

    private void layoutLiveControls(int left, int panelWidth) {
        int desiredWidth = SEEK_BUTTON_WIDTH * 2 + PAUSE_BUTTON_WIDTH + JUMP_BUTTON_WIDTH + CONTROL_GAP * 3;
        int seekWidth = SEEK_BUTTON_WIDTH;
        int pauseWidth = PAUSE_BUTTON_WIDTH;
        int jumpWidth = JUMP_BUTTON_WIDTH;
        if (panelWidth < desiredWidth) {
            double scale = (panelWidth - CONTROL_GAP * 3)
                    / (double) (SEEK_BUTTON_WIDTH * 2 + PAUSE_BUTTON_WIDTH + JUMP_BUTTON_WIDTH);
            seekWidth = Math.max(32, (int) Math.floor(SEEK_BUTTON_WIDTH * scale));
            pauseWidth = Math.max(48, (int) Math.floor(PAUSE_BUTTON_WIDTH * scale));
            jumpWidth = panelWidth - CONTROL_GAP * 3 - seekWidth * 2 - pauseWidth;
            desiredWidth = panelWidth;
        }

        int x = left + (panelWidth - desiredWidth) / 2;
        int y = panelTop() + 104;
        this.seekBackwardButton.setRectangle(seekWidth, 20, x, y);
        x += seekWidth + CONTROL_GAP;
        this.pauseButton.setRectangle(pauseWidth, 20, x, y);
        x += pauseWidth + CONTROL_GAP;
        this.seekForwardButton.setRectangle(seekWidth, 20, x, y);
        x += seekWidth + CONTROL_GAP;
        this.jumpToPresentButton.setRectangle(jumpWidth, 20, x, y);
    }

    private double positionSeconds(VideoSessionState state) {
        return this.minecraft.level == null
                ? state.positionAtEpochSeconds()
                : state.positionAt(this.minecraft.level.getGameTime());
    }

    private void submitUrl() {
        String url = this.urlField.getValue().trim();
        if (!isAllowedUrl(url))
            return;

        Balm.networking().sendToServer(new SetScreenVideoUrlMessage(this.pos, url));
    }

    private void seekAbsolute(double seconds) {
        Balm.networking().sendToServer(new SeekScreenPlaybackAbsoluteMessage(this.pos, seconds));
    }

    private void seekRelative(double seconds) {
        Balm.networking().sendToServer(new SeekScreenPlaybackRelativeMessage(this.pos, seconds));
    }

    private void togglePaused() {
        Balm.networking().sendToServer(new ToggleScreenPlaybackMessage(this.pos));
    }

    private void jumpToPresent() {
        Balm.networking().sendToServer(new JumpScreenPlaybackToPresentMessage(this.pos));
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

    private static double wrapPosition(double position, double duration) {
        double wrapped = position % duration;
        return wrapped < 0.0 ? wrapped + duration : wrapped;
    }

    private static String formatTime(double seconds) {
        long totalSeconds = Math.max(0L, Math.round(seconds));
        long hours = totalSeconds / 3600L;
        long minutes = totalSeconds % 3600L / 60L;
        long remainingSeconds = totalSeconds % 60L;
        return hours > 0L
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainingSeconds)
                : String.format(Locale.ROOT, "%d:%02d", minutes, remainingSeconds);
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

    private static final class PlaybackProgressSlider extends AbstractSliderButton {
        private final DoubleConsumer seekHandler;
        private OptionalDouble duration = OptionalDouble.empty();
        private boolean hasMedia;
        private boolean live;
        private boolean prepared;
        private boolean pointerAdjusting;
        private int holdManualValueTicks;

        private PlaybackProgressSlider(int x, int y, int width, DoubleConsumer seekHandler) {
            super(x, y, width, 20, NO_MEDIA, 0.0);
            this.seekHandler = seekHandler;
        }

        public void setPlayback(
                double position,
                OptionalDouble duration,
                boolean hasMedia,
                boolean live,
                boolean prepared
        ) {
            this.duration = duration;
            this.hasMedia = hasMedia;
            this.live = live;
            this.prepared = prepared;
            this.active = hasMedia && duration.isPresent();

            if (!this.pointerAdjusting && this.holdManualValueTicks-- <= 0) {
                this.value = duration.isPresent()
                        ? Math.clamp(position / duration.getAsDouble(), 0.0, 1.0)
                        : 0.0;
            }
            updateMessage();
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            this.pointerAdjusting = this.active;
            super.onClick(event, doubleClick);
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            boolean submit = this.pointerAdjusting && this.active && this.duration.isPresent();
            this.pointerAdjusting = false;
            super.onRelease(event);
            if (submit) {
                this.holdManualValueTicks = 10;
                this.seekHandler.accept(this.value * this.duration.getAsDouble());
            }
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            double previous = this.value;
            boolean handled = super.keyPressed(event);
            if (handled && this.active && this.duration.isPresent()
                    && Double.compare(previous, this.value) != 0) {
                this.holdManualValueTicks = 10;
                this.seekHandler.accept(this.value * this.duration.getAsDouble());
            }
            return handled;
        }

        @Override
        protected void updateMessage() {
            if (!this.hasMedia) {
                setMessage(NO_MEDIA);
            } else if (this.live) {
                setMessage(LIVE_MEDIA);
            } else if (this.duration.isEmpty()) {
                setMessage(this.prepared ? LIVE_MEDIA : LOADING_MEDIA);
            } else {
                double length = this.duration.getAsDouble();
                setMessage(Component.translatable(
                        TRANSLATION_PREFIX + ".progress",
                        formatTime(this.value * length),
                        formatTime(length)
                ));
            }
        }

        @Override
        protected void applyValue() {
            // Mouse seeks are submitted on release so dragging does not flood the server.
        }
    }
}
