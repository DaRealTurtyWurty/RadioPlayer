package dev.turtywurty.mediabox.client.gui;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.api.client.GlobePoint;
import dev.turtywurty.mediabox.client.GlobePointCache;
import dev.turtywurty.mediabox.client.RadioStationPoint;
import dev.turtywurty.mediabox.client.gui.widget.GlobeWidget;
import dev.turtywurty.mediabox.client.gui.widget.StationListWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

import java.util.*;

public class GlobeScreen extends Screen {
    public static final Component TITLE = Component.translatable("screen." + MediaBox.MOD_ID + ".globe.title");
    private static final int MIN_PADDING = 24;
    private static final int MAX_PADDING = 64;
    private static final float MIN_ZOOM = 1.0F;
    private static final float MAX_ZOOM = 25.0F;
    private static final float INITIAL_ZOOM = 1.0F;
    private static final int PANEL_PADDING = 8;
    private static final LodLevel[] LOD_LEVELS = {
            new LodLevel(1.0F, 200, 12.0D, 5.0F),
            new LodLevel(1.25F, 350, 10.0D, 4.4F),
            new LodLevel(1.5F, 550, 8.5D, 3.8F),
            new LodLevel(1.85F, 800, 7.0D, 3.2F),
            new LodLevel(2.25F, 1_100, 5.5D, 2.75F),
            new LodLevel(2.75F, 1_500, 4.5D, 2.35F),
            new LodLevel(3.3F, 2_000, 3.5D, 2.0F),
            new LodLevel(4.0F, 2_600, 2.75D, 1.7F),
            new LodLevel(5.0F, 3_400, 2.0D, 1.45F),
            new LodLevel(6.25F, 4_200, 1.5D, 1.2F),
            new LodLevel(7.75F, 5_000, 1.1D, 1.0F),
            new LodLevel(9.0F, 6_000, 0.8D, 0.85F),
            new LodLevel(11.0F, 7_500, 0.6D, 0.7F),
            new LodLevel(13.5F, 9_000, 0.45D, 0.58F),
            new LodLevel(16.5F, 11_000, 0.35D, 0.48F),
            new LodLevel(20.0F, 13_000, 0.25D, 0.4F),
            new LodLevel(23.0F, 15_000, 0.18D, 0.34F),
            new LodLevel(MAX_ZOOM, Integer.MAX_VALUE, 0.12D, 0.28F)
    };
    private final Map<GlobePoint, List<GlobePoint>> hiddenPointsByVisiblePoint = new HashMap<>();
    private final Map<GlobePoint, List<GlobePoint>> hiddenPointsByGeographicCell = new HashMap<>();
    private final Map<GlobePoint, List<GlobePoint>> hiddenPointsByScreenCollision = new HashMap<>();
    private GlobeWidget globeWidget;
    private StationListWidget stationListWidget;
    private int visibleLoadedPointCount = -1;
    private double visibleCellSize = -1.0D;
    private float visiblePointSize = -1.0F;
    private GlobePoint selectedCollapsedPoint;
    private List<GlobePoint> selectedStationPoints = List.of();
    private boolean selectedHiddenPointsLoading;
    private String stationSearchQuery = "";
    private float preservedZoom = INITIAL_ZOOM;
    private float preservedYawDegrees;
    private float preservedPitchDegrees;
    private boolean preservedRotationSet;

    public GlobeScreen() {
        super(TITLE);
    }

    private static List<GlobePoint> selectedCellPoints(GlobePoint selectedPoint, List<GlobePoint> cellPoints) {
        List<GlobePoint> points = new ArrayList<>();
        Set<String> addedPoints = new HashSet<>();
        points.add(selectedPoint);
        addedPoints.add(stationKey(selectedPoint));
        for (GlobePoint point : cellPoints) {
            if (addedPoints.add(stationKey(point))) {
                points.add(point);
            }
        }

        return List.copyOf(points);
    }

    private static String stationKey(GlobePoint point) {
        if (point instanceof RadioStationPoint radioStationPoint)
            return radioStationPoint.getStationKey();

        return point.getLatitude() + "," + point.getLongitude();
    }

    private static int spatialCellKey(GlobePoint point, double cellSizeDegrees) {
        int latitudeCell = Mth.floor((point.getLatitude() + 90.0D) / cellSizeDegrees);
        int longitudeCell = Mth.floor((point.getLongitude() + 180.0D) / cellSizeDegrees);
        int longitudeCellCount = Mth.ceil(360.0D / cellSizeDegrees);
        return latitudeCell * longitudeCellCount + Math.floorMod(longitudeCell, longitudeCellCount);
    }

    @Override
    protected void init() {
        int padding = Mth.clamp(Math.min(this.width, this.height) / 10, MIN_PADDING, MAX_PADDING);
        int panelGap = selectedHiddenPanelVisible() ? MIN_PADDING : 0;
        int panelWidth = selectedHiddenPanelVisible() ? StationListWidget.WIDTH + panelGap : 0;
        int availableWidth = Math.max(32, this.width - panelWidth);
        int size = Math.max(32, Math.min(availableWidth, this.height) - padding * 2);
        int globeX = selectedHiddenPanelVisible()
                ? Math.max(0, (availableWidth - size) / 2)
                : (this.width - size) / 2;
        GlobeWidget.Builder builder = GlobeWidget.builder()
                .bounds(globeX, (this.height - size) / 2, size, size)
                .message(TITLE)
                .points(List.of())
                .zoom(this.preservedZoom, MIN_ZOOM, MAX_ZOOM)
                .zoomStep(1.15D)
                .maxPitch(85.0F)
                .autoRotationPeriod(360_000L)
                .onPointClick(this::handlePointClick)
                .onPointCollisions(this::handlePointCollisions);
        if (this.preservedRotationSet) {
            builder.initialRotation(this.preservedYawDegrees, this.preservedPitchDegrees);
        }

        this.globeWidget = builder.build();
        addRenderableWidget(this.globeWidget);
        addStationListWidget();
        this.visibleLoadedPointCount = -1;
        this.visibleCellSize = -1.0D;
        this.visiblePointSize = -1.0F;
        GlobePointCache.requestPoints(lodLevelForZoom(this.preservedZoom).maxPoints());
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        updateVisiblePoints();
        extractGlobePanel(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void updateVisiblePoints() {
        if (this.globeWidget == null)
            return;

        LodLevel lodLevel = lodLevelForZoom(this.globeWidget.getZoom());
        GlobePointCache.requestPoints(lodLevel.maxPoints());
        int currentLoadedPointCount = GlobePointCache.loadedPointCount();
        if (lodLevel.pointSize() != this.visiblePointSize) {
            this.visiblePointSize = lodLevel.pointSize();
            this.globeWidget.setPointSizeMultiplier(lodLevel.pointSize());
        }

        if (currentLoadedPointCount != this.visibleLoadedPointCount ||
                lodLevel.cellSizeDegrees() != this.visibleCellSize) {
            List<GlobePoint> visiblePoints = visiblePointsForLod(lodLevel);
            this.visibleLoadedPointCount = currentLoadedPointCount;
            this.visibleCellSize = lodLevel.cellSizeDegrees();
            this.globeWidget.setPoints(visiblePoints);
            rebuildHiddenPointGroups();
            updateSelectedHiddenPoints(visiblePoints);
        }
    }

    private List<GlobePoint> visiblePointsForLod(LodLevel lodLevel) {
        this.hiddenPointsByGeographicCell.clear();
        Map<Integer, GlobePoint> visiblePointByCell = new LinkedHashMap<>();
        List<GlobePoint> visiblePoints = new ArrayList<>();
        for (GlobePoint point : GlobePointCache.snapshot()) {
            int cellKey = spatialCellKey(point, lodLevel.cellSizeDegrees());
            GlobePoint visiblePoint = visiblePointByCell.get(cellKey);
            if (visiblePoint == null && visiblePoints.size() < lodLevel.maxPoints()) {
                visiblePointByCell.put(cellKey, point);
                visiblePoints.add(point);
            } else if (visiblePoint != null) {
                this.hiddenPointsByGeographicCell.computeIfAbsent(visiblePoint, _ -> new ArrayList<>()).add(point);
            }
        }

        return visiblePoints;
    }

    private void handlePointCollisions(GlobeWidget.PointCollisionResult collisionResult) {
        List<GlobePoint> previousSelectedHiddenPoints = this.selectedCollapsedPoint == null
                ? List.of()
                : this.hiddenPointsByVisiblePoint.getOrDefault(this.selectedCollapsedPoint, List.of());
        this.hiddenPointsByScreenCollision.clear();
        for (GlobeWidget.HiddenPointGroup hiddenPointGroup : collisionResult.hiddenPointGroups()) {
            this.hiddenPointsByScreenCollision
                    .computeIfAbsent(hiddenPointGroup.visiblePoint(), _ -> new ArrayList<>())
                    .add(hiddenPointGroup.hiddenPoint());
        }

        rebuildHiddenPointGroups();
        if (this.selectedCollapsedPoint != null) {
            List<GlobePoint> currentSelectedHiddenPoints = this.hiddenPointsByVisiblePoint.getOrDefault(this.selectedCollapsedPoint, List.of());
            if (!currentSelectedHiddenPoints.equals(previousSelectedHiddenPoints) && !currentSelectedHiddenPoints.isEmpty()) {
                this.selectedStationPoints = selectedCellPoints(this.selectedCollapsedPoint, currentSelectedHiddenPoints);
                updateStationListWidget();
            }
        }
    }

    private void rebuildHiddenPointGroups() {
        this.hiddenPointsByVisiblePoint.clear();
        mergeHiddenPointGroups(this.hiddenPointsByGeographicCell);
        mergeHiddenPointGroups(this.hiddenPointsByScreenCollision);
    }

    private void mergeHiddenPointGroups(Map<GlobePoint, List<GlobePoint>> hiddenPointGroups) {
        for (Map.Entry<GlobePoint, List<GlobePoint>> entry : hiddenPointGroups.entrySet()) {
            List<GlobePoint> mergedPoints = this.hiddenPointsByVisiblePoint.computeIfAbsent(entry.getKey(), _ -> new ArrayList<>());
            for (GlobePoint hiddenPoint : entry.getValue()) {
                if (!mergedPoints.contains(hiddenPoint)) {
                    mergedPoints.add(hiddenPoint);
                }
            }
        }
    }

    private void handlePointClick(GlobePoint point) {
        List<GlobePoint> hiddenPoints = this.hiddenPointsByVisiblePoint.get(point);
        this.selectedCollapsedPoint = point;
        this.selectedStationPoints = selectedCellPoints(point, hiddenPoints == null ? List.of() : hiddenPoints);
        preserveGlobeView();
        rebuildWidgets();
        if (hiddenPoints != null && !hiddenPoints.isEmpty()) {
            this.selectedHiddenPointsLoading = true;
            GlobePointCache.requestCellPoints(point, this.visibleCellSize)
                    .whenComplete((cellPoints, throwable) -> Minecraft.getInstance().execute(() -> {
                        if (this.selectedCollapsedPoint != point)
                            return;

                        this.selectedHiddenPointsLoading = false;
                        if (throwable == null) {
                            this.selectedStationPoints = selectedCellPoints(point, cellPoints);
                        }

                        preserveGlobeView();
                        rebuildWidgets();
                    }));
        } else {
            this.selectedHiddenPointsLoading = false;
        }
    }

    private void updateSelectedHiddenPoints(List<GlobePoint> visiblePoints) {
        if (this.selectedCollapsedPoint == null)
            return;

        if (visiblePoints.contains(this.selectedCollapsedPoint)) {
            List<GlobePoint> hiddenPoints = this.hiddenPointsByVisiblePoint.get(this.selectedCollapsedPoint);
            this.selectedStationPoints = selectedCellPoints(this.selectedCollapsedPoint, hiddenPoints == null ? List.of() : hiddenPoints);
            updateStationListWidget();
            return;
        }

        this.selectedCollapsedPoint = null;
        this.selectedStationPoints = List.of();
        this.selectedHiddenPointsLoading = false;
        preserveGlobeView();
        rebuildWidgets();
    }

    private void addStationListWidget() {
        if (!selectedHiddenPanelVisible())
            return;

        this.stationListWidget = new StationListWidget(hiddenPanelLeft(), hiddenPanelTop(), hiddenPanelHeight(), GlobePoint::click);
        this.stationListWidget.setSearchQuery(this.stationSearchQuery);
        this.stationListWidget.setSearchResponder(query -> this.stationSearchQuery = query);
        updateStationListWidget();
        addRenderableWidget(this.stationListWidget);
    }

    private void updateStationListWidget() {
        if (this.stationListWidget == null)
            return;

        this.stationListWidget.setStations(this.selectedStationPoints);
        this.stationListWidget.setLoading(this.selectedHiddenPointsLoading);
    }

    private void extractGlobePanel(GuiGraphicsExtractor graphics) {
        if (this.globeWidget == null)
            return;

        int left = this.globeWidget.getX() - PANEL_PADDING;
        int top = this.globeWidget.getY() - PANEL_PADDING;
        int width = this.globeWidget.getWidth() + PANEL_PADDING * 2;
        int height = this.globeWidget.getHeight() + PANEL_PADDING * 2;
        graphics.fill(left, top, left + width, top + height, 0xC0101010);
        graphics.outline(left, top, width, height, 0xFF707070);
    }

    private boolean selectedHiddenPanelVisible() {
        return !this.selectedStationPoints.isEmpty();
    }

    private int hiddenPanelLeft() {
        return Math.max(0, this.width - StationListWidget.WIDTH - MIN_PADDING);
    }

    private int hiddenPanelTop() {
        if (this.globeWidget != null)
            return this.globeWidget.getY() - PANEL_PADDING;

        return Math.max(MIN_PADDING, (this.height - hiddenPanelHeight()) / 2);
    }

    private int hiddenPanelHeight() {
        if (this.globeWidget != null)
            return this.globeWidget.getHeight() + PANEL_PADDING * 2;

        return StationListWidget.preferredHeight(Math.max(1, this.height - MIN_PADDING * 2));
    }

    private void preserveGlobeView() {
        if (this.globeWidget == null)
            return;

        this.preservedZoom = this.globeWidget.getZoom();
        this.preservedYawDegrees = this.globeWidget.getYawDegrees();
        this.preservedPitchDegrees = this.globeWidget.getPitchDegrees();
        this.preservedRotationSet = this.globeWidget.isUserControlled();
    }

    private LodLevel lodLevelForZoom(float zoom) {
        LodLevel lodLevel = LOD_LEVELS[0];
        for (LodLevel level : LOD_LEVELS) {
            if (zoom >= level.minZoom()) {
                lodLevel = level;
            } else {
                break;
            }
        }

        return lodLevel;
    }

    private record LodLevel(float minZoom, int maxPoints, double cellSizeDegrees, float pointSize) {
    }
}
