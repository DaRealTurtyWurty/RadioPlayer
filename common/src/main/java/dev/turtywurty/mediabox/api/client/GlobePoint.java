package dev.turtywurty.mediabox.api.client;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class GlobePoint {
    public static final float DEFAULT_SIZE = 10.0F;
    private final double latitude;
    private final double longitude;
    private final int color;
    private final float size;
    private final @Nullable Component tooltip;
    private final @Nullable Runnable clickAction;

    public GlobePoint(double latitude, double longitude, int color, float size, @Nullable Component tooltip, @Nullable Runnable clickAction) {
        if (!Double.isFinite(latitude) || latitude < -90.0D || latitude > 90.0D)
            throw new IllegalArgumentException("Latitude must be between -90 and 90 degrees");

        if (!Double.isFinite(longitude) || longitude < -180.0D || longitude > 180.0D)
            throw new IllegalArgumentException("Longitude must be between -180 and 180 degrees");

        if (!Float.isFinite(size) || size <= 0.0F)
            throw new IllegalArgumentException("Size must be finite and greater than zero");

        this.latitude = latitude;
        this.longitude = longitude;
        this.color = color;
        this.size = size;
        this.tooltip = tooltip;
        this.clickAction = clickAction;
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
        if (this.clickAction != null) {
            this.clickAction.run();
        }
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getColor() {
        return color;
    }

    public float getSize() {
        return size;
    }

    public @Nullable Component getTooltip() {
        return tooltip;
    }

    public @Nullable Runnable getClickAction() {
        return clickAction;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (GlobePoint) obj;
        return Double.doubleToLongBits(this.latitude) == Double.doubleToLongBits(that.latitude) &&
                Double.doubleToLongBits(this.longitude) == Double.doubleToLongBits(that.longitude) &&
                this.color == that.color &&
                Float.floatToIntBits(this.size) == Float.floatToIntBits(that.size) &&
                Objects.equals(this.tooltip, that.tooltip) &&
                Objects.equals(this.clickAction, that.clickAction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, color, size, tooltip, clickAction);
    }

    @Override
    public String toString() {
        return "GlobePoint[" +
                "latitude=" + latitude + ", " +
                "longitude=" + longitude + ", " +
                "color=" + color + ", " +
                "size=" + size + ", " +
                "tooltip=" + tooltip + ", " +
                "clickAction=" + clickAction + ']';
    }
}
