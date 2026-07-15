package dev.turtywurty.mediabox.client.gui.widget;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.api.client.GlobePoint;
import dev.turtywurty.mediabox.client.RadioStationPoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class StationListWidget extends AbstractWidget {
    public static final Component STATIONS_LABEL = Component.translatable("screen." + MediaBox.MOD_ID + ".globe.stations");
    public static final Component EMPTY_STATIONS_LABEL = Component.translatable("screen." + MediaBox.MOD_ID + ".globe.stations.empty");
    public static final Component SEARCH_LABEL = Component.translatable("screen." + MediaBox.MOD_ID + ".globe.stations.search");

    public static final int WIDTH = 180;
    public static final int PADDING = 8;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 3;
    private static final int SCROLLBAR_WIDTH = 2;
    private static final int SCROLLBAR_GAP = 4;
    private static final int SEARCH_BOX_HEIGHT = 20;
    private static final int SEARCH_BOX_GAP = 6;
    private static final int MIN_LIST_HEIGHT = ROW_HEIGHT;
    private static final int HEADER_HEIGHT = PADDING + 14 + 6 + SEARCH_BOX_HEIGHT + SEARCH_BOX_GAP;

    private final EditBox searchField;
    private final Consumer<GlobePoint> stationClickHandler;
    private final List<Button> stationRowButtons = new ArrayList<>();
    private List<GlobePoint> stations = List.of();
    private Consumer<String> searchResponder = _ -> {
    };
    private boolean loading;
    private int firstVisibleStationIndex;

    public StationListWidget(int x, int y, int height, Consumer<GlobePoint> stationClickHandler) {
        super(x, y, WIDTH, height, STATIONS_LABEL);
        this.stationClickHandler = stationClickHandler;
        this.searchField = new EditBox(
                Minecraft.getInstance().font,
                x + PADDING,
                y + searchTopOffset(),
                WIDTH - PADDING * 2,
                SEARCH_BOX_HEIGHT,
                SEARCH_LABEL);
        this.searchField.setMaxLength(128);
        this.searchField.setResponder(query -> {
            this.firstVisibleStationIndex = 0;
            this.searchResponder.accept(query);
        });
    }

    public static int preferredHeight(int availableHeight) {
        return Math.max(HEADER_HEIGHT + MIN_LIST_HEIGHT + PADDING, availableHeight);
    }

    private static int searchTopOffset() {
        return PADDING + 20;
    }

    private static Component pointMessage(GlobePoint point) {
        return Component.literal(stationLabel(point));
    }

    private static String stationLabel(GlobePoint point) {
        if (!(point instanceof RadioStationPoint radioStationPoint))
            return "%.4f, %.4f".formatted(point.getLatitude(), point.getLongitude());

        if (radioStationPoint.getStationName() != null && !radioStationPoint.getStationName().isBlank())
            return radioStationPoint.getStationName();

        if (radioStationPoint.getStationUrl() != null && !radioStationPoint.getStationUrl().isBlank())
            return radioStationPoint.getStationUrl();

        return "%.4f, %.4f".formatted(point.getLatitude(), point.getLongitude());
    }

    public void setSearchQuery(String query) {
        this.searchField.setValue(query);
    }

    public void setSearchResponder(Consumer<String> searchResponder) {
        this.searchResponder = searchResponder == null ? _ -> {
        } : searchResponder;
    }

    public void setStations(List<GlobePoint> stations) {
        this.stations = List.copyOf(stations);
        clampFirstVisibleStationIndex();
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xC0101010);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), 0xFF707070);
        graphics.text(Minecraft.getInstance().font, STATIONS_LABEL, getX() + PADDING, getY() + PADDING, 0xFFA0A0A0);
        if (this.loading) {
            var font = Minecraft.getInstance().font;
            graphics.text(font, Component.literal("..."), getX() + getWidth() - PADDING - font.width("..."), getY() + PADDING, 0xFFA0A0A0);
        }

        this.searchField.extractRenderState(graphics, mouseX, mouseY, partialTick);

        List<GlobePoint> filteredStations = filteredStations();
        if (filteredStations.isEmpty()) {
            graphics.centeredText(Minecraft.getInstance().font, EMPTY_STATIONS_LABEL, getX() + getWidth() / 2, listTop(), 0xFF808080);
        }

        graphics.enableScissor(getX() + PADDING, listTop(), getX() + getWidth() - PADDING, listTop() + listHeight());
        extractStationRows(graphics, filteredStations, mouseX, mouseY, partialTick);
        graphics.disableScissor();
        extractScrollbar(graphics, filteredStations.size());
    }

    private void extractStationRows(GuiGraphicsExtractor graphics, List<GlobePoint> filteredStations, int mouseX, int mouseY, float partialTick) {
        int rowX = getX() + PADDING;
        int rowY = listTop();
        ensureStationRowButtons(visibleRowSlots());
        int lastVisibleIndex = Math.min(filteredStations.size(), this.firstVisibleStationIndex + visibleRowSlots());
        for (int index = this.firstVisibleStationIndex; index < lastVisibleIndex; index++) {
            GlobePoint point = filteredStations.get(index);
            int visibleIndex = index - this.firstVisibleStationIndex;
            Button button = this.stationRowButtons.get(visibleIndex);
            button.setMessage(pointMessage(point));
            button.setX(rowX);
            button.setY(rowY + visibleIndex * (ROW_HEIGHT + ROW_GAP));
            button.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void ensureStationRowButtons(int rowCount) {
        while (this.stationRowButtons.size() < rowCount) {
            this.stationRowButtons.add(Button.builder(Component.empty(), _ -> {
            }).bounds(0, 0, getRowWidth(), ROW_HEIGHT).build());
        }
    }

    private void extractScrollbar(GuiGraphicsExtractor graphics, int stationCount) {
        if (stationCount <= fullyVisibleRows())
            return;

        int trackX = getX() + getWidth() - PADDING - SCROLLBAR_WIDTH;
        int trackY = listTop();
        int trackHeight = listHeight();
        int thumbHeight = Math.max(10, trackHeight * fullyVisibleRows() / stationCount);
        int thumbRange = trackHeight - thumbHeight;
        int thumbY = trackY + (maxFirstVisibleStationIndex() == 0 ? 0 : thumbRange * this.firstVisibleStationIndex / maxFirstVisibleStationIndex());

        graphics.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackHeight, 0xFF303030);
        graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFFA0A0A0);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (this.searchField.mouseClicked(event, doubleClick)) {
            setFocused(true);
            return true;
        }

        List<GlobePoint> filteredStations = filteredStations();
        int rowWidth = getRowWidth();
        int rowX = getX() + PADDING;
        int rowY = listTop();
        int lastVisibleIndex = Math.min(filteredStations.size(), this.firstVisibleStationIndex + visibleRowSlots());
        for (int index = this.firstVisibleStationIndex; index < lastVisibleIndex; index++) {
            int visibleIndex = index - this.firstVisibleStationIndex;
            int y = rowY + visibleIndex * (ROW_HEIGHT + ROW_GAP);
            if (event.x() >= rowX && event.x() < rowX + rowWidth && event.y() >= y && event.y() < y + ROW_HEIGHT) {
                Button.playButtonClickSound(Minecraft.getInstance().getSoundManager());
                this.stationClickHandler.accept(filteredStations.get(index));
                return true;
            }
        }

        return isMouseOver(event.x(), event.y());
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        return this.searchField.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        return this.searchField.charTyped(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (filteredStations().size() > fullyVisibleRows() &&
                mouseX >= getX() && mouseX <= getX() + getWidth() &&
                mouseY >= listTop() && mouseY <= listTop() + listHeight()) {
            this.firstVisibleStationIndex -= (int) Math.signum(scrollY);
            clampFirstVisibleStationIndex();
            return true;
        }

        return this.searchField.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        this.searchField.setFocused(focused);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private List<GlobePoint> filteredStations() {
        String query = this.searchField.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty())
            return this.stations;

        return this.stations.stream()
                .filter(point -> stationLabel(point).toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private void clampFirstVisibleStationIndex() {
        this.firstVisibleStationIndex = Math.clamp(this.firstVisibleStationIndex, 0, maxFirstVisibleStationIndex());
    }

    private int maxFirstVisibleStationIndex() {
        return Math.max(0, filteredStations().size() - fullyVisibleRows());
    }

    private int fullyVisibleRows() {
        return Math.max(1, (listHeight() + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
    }

    private int visibleRowSlots() {
        return Math.max(1, (int) Math.ceil((listHeight() + ROW_GAP) / (double) (ROW_HEIGHT + ROW_GAP)));
    }

    private int getRowWidth() {
        return getWidth() - PADDING * 2 - SCROLLBAR_WIDTH - SCROLLBAR_GAP;
    }

    private int listTop() {
        return getY() + searchTopOffset() + SEARCH_BOX_HEIGHT + SEARCH_BOX_GAP;
    }

    private int listHeight() {
        return Math.max(ROW_HEIGHT, getY() + getHeight() - PADDING - listTop());
    }
}
