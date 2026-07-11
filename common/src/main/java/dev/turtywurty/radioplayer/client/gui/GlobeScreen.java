package dev.turtywurty.radioplayer.client.gui;

import dev.turtywurty.radioplayer.Radioplayer;
import dev.turtywurty.radioplayer.api.client.GlobePoint;
import dev.turtywurty.radioplayer.client.GlobePointCache;
import dev.turtywurty.radioplayer.client.gui.widget.GlobeWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GlobeScreen extends Screen {
    public static final Component TITLE = Component.translatable("screen." + Radioplayer.MOD_ID + ".globe.title");
    private static final int MIN_PADDING = 24;
    private static final int MAX_PADDING = 64;
    private static final float MIN_ZOOM = 1.0F;
    private static final float MAX_ZOOM = 25.0F;
    private static final float INITIAL_ZOOM = 1.0F;
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
            new LodLevel(MAX_ZOOM, Integer.MAX_VALUE, 0.0D, 0.28F)
    };

    private GlobeWidget globeWidget;
    private int visibleLoadedPointCount = -1;
    private double visibleCellSize = -1.0D;
    private float visiblePointSize = -1.0F;

    public GlobeScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        int padding = Mth.clamp(Math.min(this.width, this.height) / 10, MIN_PADDING, MAX_PADDING);
        int size = Math.max(32, Math.min(this.width, this.height) - padding * 2);
        this.globeWidget = GlobeWidget.builder()
                .bounds((this.width - size) / 2, (this.height - size) / 2, size, size)
                .message(TITLE)
                .points(List.of())
                .zoom(INITIAL_ZOOM, MIN_ZOOM, MAX_ZOOM)
                .zoomStep(1.15D)
                .maxPitch(85.0F)
                .autoRotationPeriod(360_000L)
                .build();
        addRenderableWidget(this.globeWidget);
        GlobePointCache.requestPoints(lodLevelForZoom(INITIAL_ZOOM).maxPoints());
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        updateVisiblePoints();
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
        }
    }

    private List<GlobePoint> visiblePointsForLod(LodLevel lodLevel) {
        if (lodLevel.cellSizeDegrees() <= 0.0D)
            return GlobePointCache.snapshot();

        List<GlobePoint> visiblePoints = new ArrayList<>();
        Set<Integer> occupiedCells = new HashSet<>();
        for (GlobePoint point : GlobePointCache.snapshot()) {
            if (occupiedCells.add(spatialCellKey(point, lodLevel.cellSizeDegrees()))) {
                visiblePoints.add(point);
                if (visiblePoints.size() >= lodLevel.maxPoints())
                    break;
            }
        }

        return visiblePoints;
    }

    private static int spatialCellKey(GlobePoint point, double cellSizeDegrees) {
        int latitudeCell = Mth.floor((point.getLatitude() + 90.0D) / cellSizeDegrees);
        int longitudeCell = Mth.floor((point.getLongitude() + 180.0D) / cellSizeDegrees);
        int longitudeCellCount = Mth.ceil(360.0D / cellSizeDegrees);
        return latitudeCell * longitudeCellCount + Math.floorMod(longitudeCell, longitudeCellCount);
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
