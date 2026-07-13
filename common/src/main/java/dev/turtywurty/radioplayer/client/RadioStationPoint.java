package dev.turtywurty.radioplayer.client;

import dev.turtywurty.radioplayer.Radioplayer;
import dev.turtywurty.radioplayer.api.client.GlobePoint;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

public class RadioStationPoint extends GlobePoint {
    private final @Nullable UUID stationId;
    private final @Nullable String stationName;
    private final @Nullable String stationUrl;
    private final @Nullable Integer bitrate;

    public RadioStationPoint(double latitude, double longitude, int color, float size, @Nullable UUID stationId, @Nullable String stationName, @Nullable String stationUrl, @Nullable Integer bitrate) {
        super(latitude, longitude, color, size);
        this.stationId = stationId;
        this.stationName = stationName;
        this.stationUrl = stationUrl;
        this.bitrate = bitrate;
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String roundedCoordinate(double value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    public @Nullable UUID getStationId() {
        return stationId;
    }

    public @Nullable String getStationName() {
        return stationName;
    }

    public @Nullable String getStationUrl() {
        return stationUrl;
    }

    public @Nullable Integer getBitrate() {
        return bitrate;
    }

    public String getStationKey() {
        String normalizedUrl = normalize(this.stationUrl);
        if (!normalizedUrl.isBlank())
            return "url:" + normalizedUrl;

        String normalizedName = normalize(this.stationName);
        if (!normalizedName.isBlank())
            return "name:" + normalizedName +
                    "|bitrate:" + (this.bitrate == null ? "" : this.bitrate) +
                    "|lat:" + roundedCoordinate(getLatitude()) +
                    "|lon:" + roundedCoordinate(getLongitude());

        if (this.stationId != null)
            return "id:" + this.stationId;

        return "point:" + roundedCoordinate(getLatitude()) + "," + roundedCoordinate(getLongitude());
    }

    @Override
    public void click() {
        super.click();
        Radioplayer.LOGGER.info("""
                        Radio station point clicked:
                          stationId={}
                          name={}
                          url={}
                          bitrate={}
                          latitude={}
                          longitude={}
                          color={}
                          size={}""",
                this.stationId,
                this.stationName,
                this.stationUrl,
                this.bitrate,
                getLatitude(),
                getLongitude(),
                String.format("0x%08X", getColor()),
                getSize());
    }
}
