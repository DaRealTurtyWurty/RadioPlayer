package dev.turtywurty.mediaplayer.client;

import dev.turtywurty.mediaplayer.MediaPlayer;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Locale;
import java.util.Set;

public final class FfprobeNativeExtractor {
    private static final String RESOURCE_ROOT = "/ffprobe/";

    private FfprobeNativeExtractor() {
    }

    public static void extract() {
        NativeBinary nativeBinary = NativeBinary.current();
        if (nativeBinary == null) {
            MediaPlayer.LOGGER.warn("FFprobe is not bundled for {} ({})", System.getProperty("os.name"),
                    System.getProperty("os.arch"));
            return;
        }

        Path target = getExecutablePath(nativeBinary);
        if (target == null) {
            MediaPlayer.LOGGER.warn("Cannot extract FFprobe before the Minecraft client is initialized");
            return;
        }

        Path ffmpegDirectory = target.getParent();
        String resourcePath = RESOURCE_ROOT + nativeBinary.resourceDirectory() + "/" + nativeBinary.fileName();

        if (Files.isRegularFile(target)) {
            try {
                makeExecutable(target, nativeBinary.windows());
            } catch (IOException | SecurityException exception) {
                MediaPlayer.LOGGER.warn("Could not set executable permissions on FFprobe at {}", target, exception);
            }

            return;
        }

        try (InputStream resource = FfprobeNativeExtractor.class.getResourceAsStream(resourcePath)) {
            if (resource == null) {
                MediaPlayer.LOGGER.error("Bundled FFprobe resource is missing: {}", resourcePath);
                return;
            }

            Files.createDirectories(ffmpegDirectory);
            Path temporaryFile = Files.createTempFile(ffmpegDirectory, nativeBinary.fileName(), ".tmp");
            try {
                Files.copy(resource, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
                makeExecutable(temporaryFile, nativeBinary.windows());
                moveIntoPlace(temporaryFile, target);
                makeExecutable(target, nativeBinary.windows());
                MediaPlayer.LOGGER.debug("Extracted FFprobe to {}", target);
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
        } catch (IOException | SecurityException exception) {
            MediaPlayer.LOGGER.error("Could not extract FFprobe to {}", target, exception);
        }
    }

    public static Path getExecutablePath() {
        NativeBinary nativeBinary = NativeBinary.current();
        return nativeBinary == null ? null : getExecutablePath(nativeBinary);
    }

    private static Path getExecutablePath(NativeBinary nativeBinary) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null)
            return null;

        return minecraft.gameDirectory.toPath()
                .resolve(MediaPlayer.MOD_ID)
                .resolve("ffmpeg")
                .resolve(nativeBinary.fileName());
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
            // Non-POSIX file systems do not expose executable permissions.
        }
    }

    private record NativeBinary(String resourceDirectory, String fileName, boolean windows) {
        private static NativeBinary current() {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            boolean arm64 = architecture.equals("aarch64") || architecture.equals("arm64");
            boolean x64 = architecture.equals("amd64") || architecture.equals("x86_64");

            if (os.contains("win") && x64)
                return new NativeBinary("windows-x64", "ffprobe.exe", true);

            if (os.contains("linux") && x64)
                return new NativeBinary("linux-x64", "ffprobe", false);

            if (os.contains("linux") && arm64)
                return new NativeBinary("linux-arm64", "ffprobe", false);

            if (os.contains("mac") && x64)
                return new NativeBinary("macos-x64", "ffprobe", false);

            if (os.contains("mac") && arm64)
                return new NativeBinary("macos-arm64", "ffprobe", false);

            return null;
        }
    }
}
