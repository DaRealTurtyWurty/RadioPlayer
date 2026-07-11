package dev.turtywurty.radioplayer.client.gui;

import dev.turtywurty.radioplayer.Radioplayer;
import dev.turtywurty.radioplayer.api.client.GlobePoint;
import dev.turtywurty.radioplayer.client.gui.widget.GlobeWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class GlobeScreen extends Screen {
    public static final Component TITLE = Component.translatable("screen." + Radioplayer.MOD_ID + ".globe.title");
    private static final int MIN_PADDING = 24;
    private static final int MAX_PADDING = 64;
    private static final int MOCK_POINT_COUNT = 20;

    private final List<GlobePoint> mockPoints = createMockPoints();

    public GlobeScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        int padding = Mth.clamp(Math.min(this.width, this.height) / 10, MIN_PADDING, MAX_PADDING);
        int size = Math.max(32, Math.min(this.width, this.height) - padding * 2);
        addRenderableWidget(GlobeWidget.builder()
                .bounds((this.width - size) / 2, (this.height - size) / 2, size, size)
                .message(TITLE)
                .points(this.mockPoints)
                .zoom(1.0F, 1.0F, 5.0F)
                .zoomStep(1.15D)
                .maxPitch(85.0F)
                .autoRotationPeriod(360_000L)
                .build());
    }

    private List<GlobePoint> createMockPoints() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<GlobePoint> points = new ArrayList<>(MOCK_POINT_COUNT);
        for (int index = 0; index < MOCK_POINT_COUNT; index++) {
            double latitude = Math.toDegrees(Math.asin(random.nextDouble(-1.0D, 1.0D)));
            double longitude = random.nextDouble(-180.0D, 180.0D);
            int color = 0xFF000000 | Mth.hsvToRgb(
                    random.nextFloat(),
                    random.nextFloat(0.65F, 1.0F),
                    random.nextFloat(0.85F, 1.0F)
            );
            float size = (float) random.nextDouble(6.0D, 12.0D);
            Component tooltip = Component.literal(String.format(
                    Locale.ROOT,
                    "Mock point %d (%.2f, %.2f)",
                    index + 1,
                    latitude,
                    longitude
            ));
            points.add(new GlobePoint(
                    latitude,
                    longitude,
                    color,
                    size,
                    tooltip,
                    () -> {
                        if (this.minecraft.player != null)
                            this.minecraft.player.sendSystemMessage(tooltip);
                    }
            ));
        }

        return List.copyOf(points);
    }
}
