package dev.turtywurty.mediabox.client.ytdlp;

import dev.turtywurty.mediabox.MediaBox;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Finds an existing yt-dlp installation and owns the user's consent for a
 * separately downloaded fallback. MediaBox never downloads yt-dlp unless the
 * user has explicitly accepted.
 */
public final class YtDlpManager {
    public static final String VERSION = "2026.07.04";

    private static final String CONSENT_PROPERTY = "yt-dlp-consent";
    private static final String ACCEPTED = "accepted";
    private static final String DECLINED = "declined";
    private static final int COPY_BUFFER_SIZE = 64 * 1024;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static Path gameDirectory;
    private static Consent consent;
    private static CompletableFuture<Optional<Path>> systemDetection;
    private static Path systemExecutable;
    private static CompletableFuture<Optional<Path>> installation;
    private static Path verifiedExecutable;

    private YtDlpManager() {
    }

    public static synchronized Consent consent(Path requestedGameDirectory) {
        initialize(requestedGameDirectory);
        return consent;
    }

    /** Finds and validates yt-dlp from the launch environment's PATH. */
    public static synchronized CompletableFuture<Optional<Path>> detectSystemInstallation(
            Path requestedGameDirectory
    ) {
        initialize(requestedGameDirectory);
        if (systemDetection != null)
            return systemDetection;

        systemDetection = CompletableFuture.supplyAsync(YtDlpManager::findSystemExecutable)
                .thenApply(executable -> {
                    synchronized (YtDlpManager.class) {
                        systemExecutable = executable.orElse(null);
                    }
                    executable.ifPresent(path -> MediaBox.LOGGER.info("Using yt-dlp from the system PATH: {}", path));
                    return executable;
                });
        return systemDetection;
    }

    public static synchronized CompletableFuture<Optional<Path>> acceptAndInstall(Path requestedGameDirectory) {
        return acceptAndInstall(requestedGameDirectory, ProgressListener.NONE);
    }

    public static synchronized CompletableFuture<Optional<Path>> acceptAndInstall(
            Path requestedGameDirectory,
            ProgressListener progressListener
    ) {
        initialize(requestedGameDirectory);
        consent = Consent.ACCEPTED;
        saveConsent();
        return ensureInstalled(progressListener);
    }

    public static synchronized void decline(Path requestedGameDirectory) {
        initialize(requestedGameDirectory);
        consent = Consent.DECLINED;
        verifiedExecutable = null;
        saveConsent();
    }

    /**
     * Starts verification or installation for a previously accepted choice. This
     * method does nothing when consent has not been granted.
     */
    public static synchronized CompletableFuture<Optional<Path>> installIfAccepted(Path requestedGameDirectory) {
        initialize(requestedGameDirectory);
        if (isUsableSystemExecutable())
            return CompletableFuture.completedFuture(Optional.of(systemExecutable));
        if (consent != Consent.ACCEPTED)
            return CompletableFuture.completedFuture(Optional.empty());

        return ensureInstalled(ProgressListener.NONE);
    }

    /**
     * Returns a validated PATH installation, or a consented managed installation
     * after successful checksum verification during this session.
     */
    public static synchronized Optional<Path> executable(Path requestedGameDirectory) {
        initialize(requestedGameDirectory);
        if (isUsableSystemExecutable())
            return Optional.of(systemExecutable);
        if (consent != Consent.ACCEPTED || verifiedExecutable == null
                || !Files.isRegularFile(verifiedExecutable)) {
            return Optional.empty();
        }

        return Optional.of(verifiedExecutable);
    }

    private static void initialize(Path requestedGameDirectory) {
        Path normalizedDirectory = requestedGameDirectory.toAbsolutePath().normalize();
        if (normalizedDirectory.equals(gameDirectory) && consent != null)
            return;

        gameDirectory = normalizedDirectory;
        consent = loadConsent();
        systemDetection = null;
        systemExecutable = null;
        installation = null;
        verifiedExecutable = null;
    }

    private static Optional<Path> findSystemExecutable() {
        String pathValue = System.getenv("PATH");
        if (pathValue == null || pathValue.isBlank())
            return Optional.empty();

        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String executableName = windows ? "yt-dlp.exe" : "yt-dlp";
        for (String pathEntry : pathValue.split(java.io.File.pathSeparator, -1)) {
            String cleanedEntry = stripSurroundingQuotes(pathEntry.trim());
            if (cleanedEntry.isEmpty())
                continue;

            try {
                Path candidate = Path.of(cleanedEntry).resolve(executableName).toAbsolutePath().normalize();
                if (!Files.isRegularFile(candidate) || (!windows && !Files.isExecutable(candidate)))
                    continue;

                if (validateSystemExecutable(candidate))
                    return Optional.of(candidate);
            } catch (InvalidPathException | SecurityException ignored) {
                // Ignore malformed or inaccessible PATH entries.
            }
        }

        return Optional.empty();
    }

    private static boolean validateSystemExecutable(Path candidate) {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    candidate.toString(),
                    "--ignore-config",
                    "--no-plugin-dirs",
                    "--version"
            ).redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }

            String version = new String(process.getInputStream().readNBytes(256), StandardCharsets.UTF_8).trim();
            return process.exitValue() == 0 && !version.isBlank();
        } catch (IOException | SecurityException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null && process.isAlive())
                process.destroyForcibly();
        }
    }

    private static String stripSurroundingQuotes(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1)
                : value;
    }

    private static boolean isUsableSystemExecutable() {
        return systemExecutable != null && Files.isRegularFile(systemExecutable);
    }

    private static CompletableFuture<Optional<Path>> ensureInstalled(ProgressListener progressListener) {
        if (installation != null)
            return installation;

        PlatformAsset asset = PlatformAsset.current();
        if (asset == null) {
            progressListener.update(InstallStage.FAILED, 0, 0);
            MediaBox.LOGGER.error("yt-dlp is not available for {} ({})", System.getProperty("os.name"),
                    System.getProperty("os.arch"));
            installation = CompletableFuture.completedFuture(Optional.empty());
            return installation;
        }

        installation = CompletableFuture.supplyAsync(() -> install(asset, progressListener));
        return installation;
    }

    private static Optional<Path> install(PlatformAsset asset, ProgressListener progressListener) {
        Path installDirectory = gameDirectory.resolve("yt-dlp");
        Path executable = installDirectory.resolve(asset.localFileName());

        try {
            progressListener.update(InstallStage.CHECKING, 0, asset.size());
            if (Files.isRegularFile(executable)
                    && asset.sha256().equals(sha256(executable, asset.size(), progressListener))) {
                makeExecutable(executable, asset.windows());
                markVerified(executable);
                progressListener.update(InstallStage.COMPLETE, asset.size(), asset.size());
                return Optional.of(executable);
            }

            Files.createDirectories(installDirectory);
            Path temporaryFile = Files.createTempFile(installDirectory, asset.localFileName(), ".download");
            try {
                download(asset, temporaryFile, progressListener);
                progressListener.update(InstallStage.FINALIZING, asset.size(), asset.size());
                makeExecutable(temporaryFile, asset.windows());
                moveIntoPlace(temporaryFile, executable);
                makeExecutable(executable, asset.windows());
            } finally {
                Files.deleteIfExists(temporaryFile);
            }

            markVerified(executable);
            progressListener.update(InstallStage.COMPLETE, asset.size(), asset.size());
            MediaBox.LOGGER.info("Installed yt-dlp {} to {}", VERSION, executable);
            return Optional.of(executable);
        } catch (IOException | InterruptedException | SecurityException exception) {
            if (exception instanceof InterruptedException)
                Thread.currentThread().interrupt();

            progressListener.update(InstallStage.FAILED, 0, asset.size());
            MediaBox.LOGGER.error("Could not install yt-dlp {}", VERSION, exception);
            return Optional.empty();
        }
    }

    private static void download(PlatformAsset asset, Path destination, ProgressListener progressListener)
            throws IOException, InterruptedException {
        URI uri = URI.create("https://github.com/yt-dlp/yt-dlp/releases/download/" + VERSION + "/" + asset.assetName());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "MediaBox/" + MediaBox.VERSION)
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("yt-dlp download returned HTTP " + response.statusCode());
        }

        MessageDigest digest = sha256Digest();
        long copied = 0;
        progressListener.update(InstallStage.DOWNLOADING, 0, asset.size());
        try (InputStream input = new DigestInputStream(response.body(), digest);
             OutputStream output = Files.newOutputStream(destination, StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                copied += read;
                if (copied > asset.size())
                    throw new IOException("yt-dlp download exceeded its expected size");

                output.write(buffer, 0, read);
                progressListener.update(InstallStage.DOWNLOADING, copied, asset.size());
            }
        }

        if (copied != asset.size())
            throw new IOException("yt-dlp download was " + copied + " bytes; expected " + asset.size());

        String actualHash = HexFormat.of().formatHex(digest.digest());
        if (!asset.sha256().equals(actualHash))
            throw new IOException("yt-dlp checksum mismatch; expected " + asset.sha256() + " but got " + actualHash);
    }

    private static String sha256(Path file, long expectedSize, ProgressListener progressListener) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
            progressListener.update(InstallStage.VERIFYING, 0, expectedSize);
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            long readTotal = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                readTotal += read;
                progressListener.update(InstallStage.VERIFYING, readTotal, expectedSize);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static synchronized void markVerified(Path executable) {
        if (consent == Consent.ACCEPTED)
            verifiedExecutable = executable;
    }

    private static Consent loadConsent() {
        Path configFile = configFile();
        if (!Files.isRegularFile(configFile))
            return Consent.UNDECIDED;

        var properties = new Properties();
        try (var reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return switch (properties.getProperty(CONSENT_PROPERTY, "").trim().toLowerCase(Locale.ROOT)) {
                case ACCEPTED -> Consent.ACCEPTED;
                case DECLINED -> Consent.DECLINED;
                default -> Consent.UNDECIDED;
            };
        } catch (IOException | SecurityException exception) {
            MediaBox.LOGGER.error("Could not read yt-dlp consent from {}", configFile, exception);
            return Consent.UNDECIDED;
        }
    }

    private static void saveConsent() {
        Path configFile = configFile();
        try {
            Files.createDirectories(configFile.getParent());
            Path temporaryFile = Files.createTempFile(configFile.getParent(), "mediabox-client", ".tmp");
            try {
                var properties = new Properties();
                properties.setProperty(CONSENT_PROPERTY, consent == Consent.ACCEPTED ? ACCEPTED : DECLINED);
                try (var writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                    properties.store(writer, "MediaBox client choices");
                }
                moveIntoPlace(temporaryFile, configFile);
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
        } catch (IOException | SecurityException exception) {
            MediaBox.LOGGER.error("Could not save yt-dlp consent to {}", configFile, exception);
        }
    }

    private static Path configFile() {
        return gameDirectory.resolve("config").resolve("mediabox-client.properties");
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void makeExecutable(Path file, boolean windows) throws IOException {
        if (windows)
            return;

        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, permissions);
        } catch (UnsupportedOperationException ignored) {
            if (!file.toFile().setExecutable(true, false))
                throw new IOException("Could not mark yt-dlp executable: " + file);
        }
    }

    public enum Consent {
        UNDECIDED,
        ACCEPTED,
        DECLINED
    }

    public enum InstallStage {
        CHECKING,
        VERIFYING,
        DOWNLOADING,
        FINALIZING,
        COMPLETE,
        FAILED
    }

    @FunctionalInterface
    public interface ProgressListener {
        ProgressListener NONE = (_, _, _) -> {
        };

        void update(InstallStage stage, long completedBytes, long totalBytes);
    }

    private record PlatformAsset(String assetName, String localFileName, long size, String sha256, boolean windows) {
        private static PlatformAsset current() {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            boolean arm64 = architecture.equals("aarch64") || architecture.equals("arm64");
            boolean x64 = architecture.equals("amd64") || architecture.equals("x86_64");

            if (os.contains("win") && x64) {
                return new PlatformAsset("yt-dlp.exe", "yt-dlp.exe", 18_226_085,
                        "52fe3c26dcf71fbdc85b528589020bb0b8e383155cfa81b64dd447bbe35e24b8", true);
            }
            if (os.contains("win") && arm64) {
                return new PlatformAsset("yt-dlp_arm64.exe", "yt-dlp.exe", 22_250_288,
                        "1525690b037ecc0bb677e38e7147b0025179cbc9a8d0c57264e3100b18099280", true);
            }
            if (os.contains("linux") && x64) {
                return new PlatformAsset("yt-dlp_linux", "yt-dlp", 39_924_536,
                        "6bbb3d314cde4febe36e5fa1d55462e29c974f63444e707871834f6d8cc210ae", false);
            }
            if (os.contains("linux") && arm64) {
                return new PlatformAsset("yt-dlp_linux_aarch64", "yt-dlp", 39_675_904,
                        "b6ce97646773070d7a7ffd6bbbdcaecb47c48483909c54c915bf08a7a9b5e0b1", false);
            }
            if (os.contains("mac") && (x64 || arm64)) {
                return new PlatformAsset("yt-dlp_macos", "yt-dlp", 38_256_544,
                        "498bd0dae17855c599d371d68ec5bafc439a9d8640e838be25c765a9792f261b", false);
            }

            return null;
        }
    }
}
