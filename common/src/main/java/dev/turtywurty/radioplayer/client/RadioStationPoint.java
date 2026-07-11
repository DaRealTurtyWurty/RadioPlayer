package dev.turtywurty.radioplayer.client;

import dev.turtywurty.radioplayer.api.client.GlobePoint;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public class RadioStationPoint extends GlobePoint {
    private final @Nullable UUID stationId;
    private final String stationName;
    private final String stationUrl;
    private final @Nullable Integer bitrate;

    public RadioStationPoint(double latitude, double longitude, int color, float size, @Nullable UUID stationId, @Nullable String stationName, @Nullable String stationUrl, @Nullable Integer bitrate) {
        super(latitude, longitude, color, size);
        this.stationId = stationId;
        this.stationName = stationName;
        this.stationUrl = stationUrl;
        this.bitrate = bitrate;
    }

    public @Nullable UUID getStationId() {
        return stationId;
    }

    public String getStationName() {
        return stationName;
    }

    public String getStationUrl() {
        return stationUrl;
    }

    public @Nullable Integer getBitrate() {
        return bitrate;
    }
}
