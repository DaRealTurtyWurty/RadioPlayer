package dev.turtywurty.radioplayer.api.client;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public record GlobePoint(
        double latitude,
        double longitude,
        int color,
        float size,
        @Nullable Component tooltip,
        @Nullable Runnable clickAction
) {
    public static final float DEFAULT_SIZE = 10.0F;

    public GlobePoint {
        if (!Double.isFinite(latitude) || latitude < -90.0D || latitude > 90.0D)
            throw new IllegalArgumentException("Latitude must be between -90 and 90 degrees");

        if (!Double.isFinite(longitude))
            throw new IllegalArgumentException("Longitude must be finite");

        if (!Float.isFinite(size) || size <= 0.0F)
            throw new IllegalArgumentException("Size must be finite and greater than zero");
    }

    public GlobePoint(double latitude, double longitude, int color) {
        this(latitude, longitude, color, DEFAULT_SIZE, null, null);
    }

    public GlobePoint(double latitude, double longitude, int color, float size) {
        this(latitude, longitude, color, size, null, null);
    }

    public GlobePoint(double latitude, double longitude, int color, float size, @Nullable Component tooltip) {
        this(latitude, longitude, color, size, tooltip, null);
    }

    public void click() {
        if (this.clickAction != null)
            this.clickAction.run();
    }
}
