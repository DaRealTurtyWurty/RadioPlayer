package dev.turtywurty.radioplayer.client;

import de.sfuhrm.radiobrowser4j.*;
import dev.turtywurty.radioplayer.Radioplayer;
import dev.turtywurty.radioplayer.api.client.GlobePoint;
import net.minecraft.util.Mth;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GlobePointCache {
    private static final int POINT_FETCH_PAGE_SIZE = 1_000;
    private static final long POINT_LOAD_RETRY_COOLDOWN_MS = 15_000L;
    private static final List<String> RADIO_BROWSER_ENDPOINTS = List.of(
            "https://all.api.radio-browser.info/",
            "https://de1.api.radio-browser.info/",
            "https://fi1.api.radio-browser.info/",
            "https://at1.api.radio-browser.info/"
    );
    private static final AdvancedSearch STATION_SEARCH = AdvancedSearch.builder()
            .hasGeoInfo(true)
            .hideBroken(true)
            .order(FieldName.VOTES)
            .reverse(true)
            .build();
    private static final ExecutorService POINT_LOADER_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, Radioplayer.MOD_ID + "-globe-point-loader");
        thread.setDaemon(true);
        return thread;
    });

    private static final Object LOCK = new Object();
    private static final List<GlobePoint> POINTS = new ArrayList<>();
    private static final Set<String> LOADED_STATION_KEYS = new HashSet<>();
    private static final Map<String, CompletableFuture<List<GlobePoint>>> CELL_LOAD_FUTURES = new HashMap<>();
    private static CompletableFuture<Void> pointLoadFuture;
    private static volatile int requestedPointCount;
    private static volatile int loadedPointCount;
    private static int nextStationOffset;
    private static volatile long nextPointLoadRetryTimeMs;
    private static volatile boolean pointLoadingExhausted;

    private GlobePointCache() {
    }

    public static int loadedPointCount() {
        return loadedPointCount;
    }

    public static List<GlobePoint> snapshot() {
        synchronized (LOCK) {
            return List.copyOf(POINTS);
        }
    }

    public static CompletableFuture<List<GlobePoint>> requestCellPoints(GlobePoint point, double cellSizeDegrees) {
        if (cellSizeDegrees <= 0.0D)
            return CompletableFuture.completedFuture(snapshot());

        CellBounds bounds = CellBounds.forPoint(point, cellSizeDegrees);
        String cellKey = bounds.key();
        synchronized (LOCK) {
            CompletableFuture<List<GlobePoint>> existingFuture = CELL_LOAD_FUTURES.get(cellKey);
            if (existingFuture != null && !existingFuture.isDone())
                return existingFuture;

            CompletableFuture<List<GlobePoint>> future = CompletableFuture
                    .supplyAsync(() -> loadCellPoints(bounds), POINT_LOADER_EXECUTOR)
                    .whenComplete((_, throwable) -> {
                        if (throwable != null)
                            Radioplayer.LOGGER.error("Failed to load globe points for cell {}", cellKey, throwable);

                        synchronized (LOCK) {
                            CELL_LOAD_FUTURES.remove(cellKey);
                        }
                    });
            CELL_LOAD_FUTURES.put(cellKey, future);
            return future;
        }
    }

    public static void requestPoints(int pointCount) {
        synchronized (LOCK) {
            long currentTime = System.currentTimeMillis();
            if (pointCount <= requestedPointCount || pointLoadingExhausted)
                return;

            if (currentTime < nextPointLoadRetryTimeMs)
                return;

            requestedPointCount = pointCount;
            if (pointLoadFuture != null && !pointLoadFuture.isDone())
                return;

            pointLoadFuture = CompletableFuture
                    .runAsync(GlobePointCache::loadRequestedPoints, POINT_LOADER_EXECUTOR)
                    .whenComplete((ignored, throwable) -> {
                        if (throwable != null) {
                            Radioplayer.LOGGER.error("Failed to load globe points", throwable);
                            synchronized (LOCK) {
                                requestedPointCount = loadedPointCount;
                                nextPointLoadRetryTimeMs = System.currentTimeMillis() + POINT_LOAD_RETRY_COOLDOWN_MS;
                            }
                        }
                    });
        }
    }

    private static void loadRequestedPoints() {
        while (!pointLoadingExhausted) {
            int targetPointCount = requestedPointCount;
            int pointsToLoad = targetPointCount - loadedPointCount;
            if (pointsToLoad <= 0)
                return;

            int stationOffset;
            synchronized (LOCK) {
                stationOffset = nextStationOffset;
            }

            List<Station> stations = fetchStations(stationOffset, POINT_FETCH_PAGE_SIZE);
            if (stations.isEmpty()) {
                pointLoadingExhausted = true;
                return;
            }

            synchronized (LOCK) {
                nextStationOffset += stations.size();
                nextPointLoadRetryTimeMs = 0L;
            }

            List<GlobePoint> loadedPoints = createLodOrderedPoints(stations.stream()
                    .map(GlobePointCache::createPoint)
                    .filter(Objects::nonNull)
                    .toList());
            appendLoadedPoints(loadedPoints);
        }
    }

    private static void appendLoadedPoints(List<GlobePoint> loadedPoints) {
        synchronized (LOCK) {
            for (GlobePoint point : loadedPoints) {
                String stationKey = stationKey(point);
                if (LOADED_STATION_KEYS.add(stationKey)) {
                    POINTS.add(point);
                }
            }

            loadedPointCount = POINTS.size();
        }
    }

    private static List<GlobePoint> createLodOrderedPoints(List<GlobePoint> points) {
        Map<Integer, List<GlobePoint>> buckets = new TreeMap<>();
        for (GlobePoint point : points) {
            buckets.computeIfAbsent(bucketKey(point), ignored -> new ArrayList<>()).add(point);
        }

        List<List<GlobePoint>> bucketedPoints = new ArrayList<>(buckets.values());
        for (List<GlobePoint> bucket : bucketedPoints) {
            bucket.sort(Comparator
                    .comparingInt(GlobePointCache::stationBitrate).reversed()
                    .thenComparing(GlobePointCache::stationName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingDouble(GlobePoint::getLatitude)
                    .thenComparingDouble(GlobePoint::getLongitude));
        }

        bucketedPoints.sort(Comparator.<List<GlobePoint>>comparingInt(List::size).reversed());

        List<GlobePoint> lodOrderedPoints = new ArrayList<>(points.size());
        for (int pointIndex = 0; lodOrderedPoints.size() < points.size(); pointIndex++) {
            boolean addedPoint = false;
            for (List<GlobePoint> bucket : bucketedPoints) {
                if (pointIndex < bucket.size()) {
                    lodOrderedPoints.add(bucket.get(pointIndex));
                    addedPoint = true;
                }
            }

            if (!addedPoint)
                break;
        }

        return lodOrderedPoints;
    }

    private static int bucketKey(GlobePoint point) {
        int latitudeBucket = Math.clamp((int) ((point.getLatitude() + 90.0D) / 10.0D), 0, 17);
        int longitudeBucket = Math.floorMod((int) ((point.getLongitude() + 180.0D) / 10.0D), 36);
        return latitudeBucket * 36 + longitudeBucket;
    }

    private static int stationBitrate(GlobePoint point) {
        if (point instanceof RadioStationPoint radioStationPoint && radioStationPoint.getBitrate() != null)
            return radioStationPoint.getBitrate();

        return 0;
    }

    private static String stationName(GlobePoint point) {
        if (point instanceof RadioStationPoint radioStationPoint && radioStationPoint.getStationName() != null)
            return radioStationPoint.getStationName();

        return "";
    }

    private static String createUserAgent() {
        return Radioplayer.MOD_ID + "/" + Radioplayer.VERSION + " (Minecraft " + Radioplayer.MINECRAFT_VERSION + "; " + System.getProperty("os.name").toLowerCase(Locale.ROOT) + ")";
    }

    private static List<Station> fetchStations(int offset, int limit) {
        String agent = createUserAgent();
        RuntimeException lastException = null;
        for (String endpoint : RADIO_BROWSER_ENDPOINTS) {
            try {
                var browser = new RadioBrowser(
                        ConnectionParams.builder()
                                .apiUrl(endpoint)
                                .userAgent(agent)
                                .timeout(5_000)
                                .build());

                Radioplayer.LOGGER.info("Loading {} globe stations from RadioBrowser endpoint: {} offset {}", limit, endpoint, offset);
                return browser.listStationsWithAdvancedSearch(Paging.at(offset, limit), STATION_SEARCH);
            } catch (RuntimeException exception) {
                lastException = exception;
                Radioplayer.LOGGER.debug("Failed to load globe points from RadioBrowser endpoint: {}", endpoint, exception);
            }
        }

        throw new IllegalStateException("Every RadioBrowser endpoint failed", lastException);
    }

    private static List<GlobePoint> loadCellPoints(CellBounds bounds) {
        int offset = 0;
        while (true) {
            List<Station> stations = fetchStationsInCell(bounds, offset, POINT_FETCH_PAGE_SIZE);
            if (stations.isEmpty())
                break;

            List<GlobePoint> loadedPoints = createLodOrderedPoints(stations.stream()
                    .filter(bounds::contains)
                    .map(GlobePointCache::createPoint)
                    .filter(Objects::nonNull)
                    .toList());
            appendLoadedPoints(loadedPoints);

            if (stations.size() < POINT_FETCH_PAGE_SIZE)
                break;

            offset += stations.size();
        }

        return snapshot().stream()
                .filter(bounds::contains)
                .toList();
    }

    private static List<Station> fetchStationsInCell(CellBounds bounds, int offset, int limit) {
        String agent = createUserAgent();
        RuntimeException lastException = null;
        for (String endpoint : RADIO_BROWSER_ENDPOINTS) {
            try {
                var browser = new RadioBrowser(
                        ConnectionParams.builder()
                                .apiUrl(endpoint)
                                .userAgent(agent)
                                .timeout(5_000)
                                .build());

                Radioplayer.LOGGER.info("Loading {} globe stations from RadioBrowser endpoint: {} offset {} cell {}",
                        limit, endpoint, offset, bounds.key());
                return browser.listStationsWithAdvancedSearch(
                        Paging.at(offset, limit),
                        STATION_SEARCH,
                        new GeoDistanceParameter(bounds.centerLatitude(), bounds.centerLongitude(), bounds.radiusMeters()));
            } catch (RuntimeException exception) {
                lastException = exception;
                Radioplayer.LOGGER.debug("Failed to load globe cell points from RadioBrowser endpoint: {}", endpoint, exception);
            }
        }

        throw new IllegalStateException("Every RadioBrowser endpoint failed", lastException);
    }

    private static GlobePoint createPoint(Station station) {
        if (!hasValidCoordinates(station))
            return null;

        return new RadioStationPoint(
                station.getGeoLatitude(),
                station.getGeoLongitude(),
                stationColor(station),
                1.0F,
                station.getStationUUID(),
                station.getName(),
                station.getUrl(),
                station.getBitrate());
    }

    private static boolean hasValidCoordinates(Station station) {
        Double latitude = station.getGeoLatitude();
        Double longitude = station.getGeoLongitude();
        return latitude != null &&
                longitude != null &&
                Double.isFinite(latitude) &&
                Double.isFinite(longitude) &&
                latitude >= -90.0D &&
                latitude <= 90.0D &&
                longitude >= -180.0D &&
                longitude <= 180.0D;
    }

    private static int stationColor(Station station) {
        return 0xFF000000 | (Objects.hash(station.getStationUUID(), station.getUrl(), station.getName()) & 0x00FFFFFF);
    }

    private static String stationKey(GlobePoint point) {
        if (point instanceof RadioStationPoint radioStationPoint)
            return radioStationPoint.getStationKey();

        return point.getLatitude() + "," + point.getLongitude();
    }

    private static final class GeoDistanceParameter extends Parameter {
        private final double latitude;
        private final double longitude;
        private final double distanceMeters;

        private GeoDistanceParameter(double latitude, double longitude, double distanceMeters) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.distanceMeters = distanceMeters;
        }

        @Override
        protected void apply(Map<String, String> requestParams) {
            requestParams.put("geo_lat", Double.toString(this.latitude));
            requestParams.put("geo_long", Double.toString(this.longitude));
            requestParams.put("geo_distance", Double.toString(this.distanceMeters));
        }
    }

    private record CellBounds(
            int latitudeCell,
            int longitudeCell,
            int longitudeCellCount,
            double minLatitude,
            double maxLatitude,
            double minLongitude,
            double maxLongitude,
            double centerLatitude,
            double centerLongitude,
            double radiusMeters
    ) {
        private static CellBounds forPoint(GlobePoint point, double cellSizeDegrees) {
            int latitudeCell = Mth.floor((point.getLatitude() + 90.0D) / cellSizeDegrees);
            int longitudeCell = Mth.floor((point.getLongitude() + 180.0D) / cellSizeDegrees);
            int longitudeCellCount = Mth.ceil(360.0D / cellSizeDegrees);
            longitudeCell = Math.floorMod(longitudeCell, longitudeCellCount);

            double minLatitude = Math.max(-90.0D, latitudeCell * cellSizeDegrees - 90.0D);
            double maxLatitude = Math.min(90.0D, minLatitude + cellSizeDegrees);
            double minLongitude = Math.max(-180.0D, longitudeCell * cellSizeDegrees - 180.0D);
            double maxLongitude = Math.min(180.0D, minLongitude + cellSizeDegrees);
            double centerLatitude = (minLatitude + maxLatitude) * 0.5D;
            double centerLongitude = (minLongitude + maxLongitude) * 0.5D;
            double radiusMeters = Math.max(
                    distanceMeters(centerLatitude, centerLongitude, minLatitude, minLongitude),
                    distanceMeters(centerLatitude, centerLongitude, maxLatitude, maxLongitude));

            return new CellBounds(
                    latitudeCell,
                    longitudeCell,
                    longitudeCellCount,
                    minLatitude,
                    maxLatitude,
                    minLongitude,
                    maxLongitude,
                    centerLatitude,
                    centerLongitude,
                    radiusMeters);
        }

        private static double distanceMeters(double latitudeA, double longitudeA, double latitudeB, double longitudeB) {
            double latA = Math.toRadians(latitudeA);
            double latB = Math.toRadians(latitudeB);
            double deltaLat = Math.toRadians(latitudeB - latitudeA);
            double deltaLon = Math.toRadians(longitudeB - longitudeA);
            double halfChordLength = Math.sin(deltaLat * 0.5D) * Math.sin(deltaLat * 0.5D) +
                    Math.cos(latA) * Math.cos(latB) * Math.sin(deltaLon * 0.5D) * Math.sin(deltaLon * 0.5D);
            return 6_371_000.0D * 2.0D * Math.atan2(Math.sqrt(halfChordLength), Math.sqrt(1.0D - halfChordLength));
        }

        private boolean contains(Station station) {
            return hasValidCoordinates(station) &&
                    contains(station.getGeoLatitude(), station.getGeoLongitude());
        }

        private boolean contains(GlobePoint point) {
            return contains(point.getLatitude(), point.getLongitude());
        }

        private boolean contains(double latitude, double longitude) {
            return latitude >= this.minLatitude &&
                    latitude < this.maxLatitude &&
                    longitude >= this.minLongitude &&
                    longitude < this.maxLongitude;
        }

        private String key() {
            return this.latitudeCell + ":" + this.longitudeCell + ":" + this.longitudeCellCount;
        }
    }
}
