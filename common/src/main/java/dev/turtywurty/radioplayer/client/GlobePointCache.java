package dev.turtywurty.radioplayer.client;

import de.sfuhrm.radiobrowser4j.*;
import dev.turtywurty.radioplayer.Radioplayer;
import dev.turtywurty.radioplayer.api.client.GlobePoint;

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

    private static GlobePoint createPoint(Station station) {
        if (station.getGeoLatitude() == null || station.getGeoLongitude() == null)
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

    private static int stationColor(Station station) {
        return 0xFF000000 | (Objects.hash(station.getStationUUID(), station.getUrl(), station.getName()) & 0x00FFFFFF);
    }

    private static String stationKey(GlobePoint point) {
        if (point instanceof RadioStationPoint radioStationPoint) {
            if (radioStationPoint.getStationId() != null)
                return radioStationPoint.getStationId().toString();

            if (radioStationPoint.getStationUrl() != null)
                return radioStationPoint.getStationUrl();
        }

        return point.getLatitude() + "," + point.getLongitude();
    }
}
