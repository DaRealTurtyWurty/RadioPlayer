package dev.turtywurty.mediabox.client.ytdlp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.turtywurty.mediabox.MediaBox;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Resolves supported web pages to one muxed media URL and its required request headers. */
public final class YtDlpVideoResolver {
    private static final long TIMEOUT_SECONDS = 45L;
    private static final long CACHE_LIFETIME_NANOS = Duration.ofMinutes(30).toNanos();
    private static final int MAX_OUTPUT_BYTES = 2 * 1024 * 1024;
    private static final String FORMAT_SELECTOR = "b[protocol^=http]/b";
    private static final Map<String, CachedMedia> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> HEADERS_BY_MEDIA_URL = new ConcurrentHashMap<>();

    private YtDlpVideoResolver() {
    }

    public static Optional<ResolvedMedia> resolve(Path gameDirectory, String pageUrl) {
        CachedMedia cached = CACHE.get(pageUrl);
        long now = System.nanoTime();
        if (cached != null && now - cached.resolvedAtNanos() < CACHE_LIFETIME_NANOS) {
            registerHeaders(cached.media());
            return Optional.of(cached.media());
        }

        Optional<Path> executable;
        try {
            executable = YtDlpManager.installIfAccepted(gameDirectory).get(2, TimeUnit.MINUTES);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException | TimeoutException exception) {
            MediaBox.LOGGER.warn("Could not make yt-dlp available for video URL resolution", exception);
            return Optional.empty();
        }
        if (executable.isEmpty())
            return Optional.empty();

        Optional<ResolvedMedia> resolved = resolveWithExecutable(executable.get(), pageUrl);
        resolved.ifPresent(media -> {
            CACHE.put(pageUrl, new CachedMedia(media, System.nanoTime()));
            registerHeaders(media);
        });
        return resolved;
    }

    /** FFmpeg/FFprobe input options for a URL previously returned by this resolver. */
    public static List<String> inputOptions(String mediaUrl) {
        Map<String, String> headers = HEADERS_BY_MEDIA_URL.get(mediaUrl);
        if (headers == null || headers.isEmpty())
            return List.of();

        StringBuilder block = new StringBuilder();
        headers.forEach((name, value) -> {
            if (name.matches("[A-Za-z0-9-]+") && !value.contains("\r") && !value.contains("\n")) {
                block.append(name).append(": ").append(value).append("\r\n");
            }
        });
        return block.isEmpty() ? List.of() : List.of("-headers", block.toString());
    }

    public static void clear() {
        CACHE.clear();
        HEADERS_BY_MEDIA_URL.clear();
    }

    private static Optional<ResolvedMedia> resolveWithExecutable(Path executable, String pageUrl) {
        Process process = null;
        try {
            Process runningProcess = new ProcessBuilder(
                    executable.toString(),
                    "--ignore-config",
                    "--no-plugin-dirs",
                    "--no-playlist",
                    "--no-warnings",
                    "--no-progress",
                    "--skip-download",
                    "--socket-timeout", "15",
                    "--format", FORMAT_SELECTOR,
                    "--dump-single-json",
                    "--",
                    pageUrl
            ).redirectErrorStream(true).start();
            process = runningProcess;

            CompletableFuture<byte[]> outputFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return runningProcess.getInputStream().readNBytes(MAX_OUTPUT_BYTES + 1);
                } catch (IOException exception) {
                    throw new CompletionException(exception);
                }
            });

            if (!runningProcess.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                runningProcess.destroyForcibly();
                outputFuture.cancel(true);
                MediaBox.LOGGER.warn("yt-dlp timed out while resolving a video URL");
                return Optional.empty();
            }

            byte[] outputBytes = outputFuture.get(5, TimeUnit.SECONDS);
            if (outputBytes.length > MAX_OUTPUT_BYTES) {
                runningProcess.destroyForcibly();
                MediaBox.LOGGER.warn("yt-dlp produced unexpectedly large output while resolving a video URL");
                return Optional.empty();
            }

            if (runningProcess.exitValue() != 0) {
                MediaBox.LOGGER.warn("yt-dlp could not resolve a playable video URL (exit code {})",
                        runningProcess.exitValue());
                return Optional.empty();
            }

            String output = new String(outputBytes, StandardCharsets.UTF_8).trim();
            ResolvedMedia resolved = parseResolvedMedia(output, pageUrl);
            if (resolved == null) {
                MediaBox.LOGGER.warn("yt-dlp did not return a valid HTTP media URL and request headers");
                return Optional.empty();
            }
            return Optional.of(resolved);
        } catch (IOException exception) {
            MediaBox.LOGGER.warn("Could not run yt-dlp to resolve a video URL", exception);
            return Optional.empty();
        } catch (ExecutionException | TimeoutException exception) {
            MediaBox.LOGGER.warn("Could not read yt-dlp output", exception);
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            if (process != null && process.isAlive())
                process.destroyForcibly();
        }
    }

    private static ResolvedMedia parseResolvedMedia(String json, String pageUrl) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject selected = selectedFormat(root);
            String url = string(selected, "url").or(() -> string(root, "url")).orElse("");
            if (!isAllowedRemoteUrl(url))
                return null;

            Map<String, String> headers = new LinkedHashMap<>();
            copyHeaders(root, headers);
            if (selected != root)
                copyHeaders(selected, headers);
            headers.putIfAbsent("Referer", pageUrl);
            return new ResolvedMedia(url, headers);
        } catch (RuntimeException exception) {
            MediaBox.LOGGER.warn("Could not parse yt-dlp media metadata", exception);
            return null;
        }
    }

    private static JsonObject selectedFormat(JsonObject root) {
        for (String field : List.of("requested_downloads", "requested_formats")) {
            JsonElement element = root.get(field);
            if (element != null && element.isJsonArray() && !element.getAsJsonArray().isEmpty()) {
                JsonElement first = element.getAsJsonArray().get(0);
                if (first.isJsonObject() && first.getAsJsonObject().has("url"))
                    return first.getAsJsonObject();
            }
        }
        return root;
    }

    private static void copyHeaders(JsonObject object, Map<String, String> destination) {
        JsonElement element = object.get("http_headers");
        if (element == null || !element.isJsonObject())
            return;
        element.getAsJsonObject().entrySet().forEach(entry -> {
            if (entry.getValue().isJsonPrimitive())
                destination.put(entry.getKey(), entry.getValue().getAsString());
        });
    }

    private static Optional<String> string(JsonObject object, String field) {
        JsonElement element = object.get(field);
        return element != null && element.isJsonPrimitive()
                ? Optional.of(element.getAsString())
                : Optional.empty();
    }

    private static void registerHeaders(ResolvedMedia media) {
        if (!media.httpHeaders().isEmpty())
            HEADERS_BY_MEDIA_URL.put(media.url(), media.httpHeaders());
    }

    private static boolean isAllowedRemoteUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public record ResolvedMedia(String url, Map<String, String> httpHeaders) {
        public ResolvedMedia {
            httpHeaders = Map.copyOf(httpHeaders);
        }
    }

    private record CachedMedia(ResolvedMedia media, long resolvedAtNanos) {
    }
}
