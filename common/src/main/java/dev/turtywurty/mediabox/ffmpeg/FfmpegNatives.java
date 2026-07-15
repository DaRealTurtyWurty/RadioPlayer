package dev.turtywurty.mediabox.ffmpeg;

import dev.turtywurty.mediabox.MediaBox;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class FfmpegNatives {
    private static final String RESOURCE_ROOT = "/ffmpeg/";
    private static final String NATIVE_VERSION = "bundle-1";
    private static final String VERSION_FILE_NAME = ".mediabox-native-version";
    private static final Set<Path> EXTRACTED_DIRECTORIES = new HashSet<>();

    private FfmpegNatives() {
    }

    public static synchronized void extract(Path gameDirectory) {
        Path installDirectory = getInstallDirectory(gameDirectory);
        if (EXTRACTED_DIRECTORIES.contains(installDirectory))
            return;

        NativeBundle nativeBundle = NativeBundle.current();
        if (nativeBundle == null) {
            MediaBox.LOGGER.error("FFmpeg is not bundled for {} ({})", System.getProperty("os.name"),
                    System.getProperty("os.arch"));
            return;
        }

        if (isCurrentInstallation(installDirectory, nativeBundle)) {
            EXTRACTED_DIRECTORIES.add(installDirectory);
            return;
        }

        boolean extracted = true;
        for (Tool tool : Tool.values()) {
            extracted &= extract(installDirectory, nativeBundle, tool);
        }

        if (extracted) {
            try {
                Files.writeString(installDirectory.resolve(VERSION_FILE_NAME), NATIVE_VERSION, StandardCharsets.UTF_8);
                EXTRACTED_DIRECTORIES.add(installDirectory);
            } catch (IOException | SecurityException exception) {
                MediaBox.LOGGER.error("Could not write the FFmpeg native version marker in {}", installDirectory,
                        exception);
            }
        }
    }

    public static Path requireFfmpeg(Path gameDirectory) throws IOException {
        return require(gameDirectory, Tool.FFMPEG);
    }

    public static Path requireFfprobe(Path gameDirectory) throws IOException {
        return require(gameDirectory, Tool.FFPROBE);
    }

    private static synchronized Path require(Path gameDirectory, Tool tool) throws IOException {
        extract(gameDirectory);

        NativeBundle nativeBundle = NativeBundle.current();
        if (nativeBundle == null) {
            throw new IOException("Bundled " + tool.displayName + " is unavailable for "
                    + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        }

        Path executable = getInstallDirectory(gameDirectory).resolve(nativeBundle.fileName(tool));
        if (!Files.isRegularFile(executable))
            throw new IOException("Bundled " + tool.displayName + " could not be extracted to " + executable);

        return executable;
    }

    private static boolean isCurrentInstallation(Path installDirectory, NativeBundle nativeBundle) {
        Path versionFile = installDirectory.resolve(VERSION_FILE_NAME);
        try {
            if (!Files.isRegularFile(versionFile)
                    || !NATIVE_VERSION.equals(Files.readString(versionFile, StandardCharsets.UTF_8).trim())) {
                return false;
            }

            for (Tool tool : Tool.values()) {
                Path executable = installDirectory.resolve(nativeBundle.fileName(tool));
                if (!Files.isRegularFile(executable))
                    return false;

                makeExecutable(executable, nativeBundle.windows);
            }

            return true;
        } catch (IOException | SecurityException exception) {
            MediaBox.LOGGER.warn("Could not validate the FFmpeg native installation in {}", installDirectory,
                    exception);
            return false;
        }
    }

    private static boolean extract(Path installDirectory, NativeBundle nativeBundle, Tool tool) {
        String fileName = nativeBundle.fileName(tool);
        String resourcePath = RESOURCE_ROOT + nativeBundle.resourceDirectory + "/" + fileName;
        Path target = installDirectory.resolve(fileName);

        try (InputStream resource = FfmpegNatives.class.getResourceAsStream(resourcePath)) {
            if (resource == null) {
                MediaBox.LOGGER.error("Bundled {} resource is missing: {}", tool.displayName, resourcePath);
                return false;
            }

            Files.createDirectories(installDirectory);
            Path temporaryFile = Files.createTempFile(installDirectory, fileName, ".tmp");
            try {
                Files.copy(resource, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
                makeExecutable(temporaryFile, nativeBundle.windows);
                moveIntoPlace(temporaryFile, target);
                makeExecutable(target, nativeBundle.windows);
                MediaBox.LOGGER.debug("Extracted {} to {}", tool.displayName, target);
                return true;
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
        } catch (IOException | SecurityException exception) {
            MediaBox.LOGGER.error("Could not extract {} to {}", tool.displayName, target, exception);
            return false;
        }
    }

    private static Path getInstallDirectory(Path gameDirectory) {
        return gameDirectory.toAbsolutePath().normalize().resolve("ffmpeg");
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

    private enum Tool {
        FFMPEG("FFmpeg"),
        FFPROBE("FFprobe");

        private final String displayName;

        Tool(String displayName) {
            this.displayName = displayName;
        }
    }

    private record NativeBundle(String resourceDirectory, boolean windows) {
        private String fileName(Tool tool) {
            return tool.name().toLowerCase(Locale.ROOT) + (this.windows ? ".exe" : "");
        }

        private static NativeBundle current() {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            boolean arm64 = architecture.equals("aarch64") || architecture.equals("arm64");
            boolean x64 = architecture.equals("amd64") || architecture.equals("x86_64");

            if (os.contains("win") && x64)
                return new NativeBundle("windows-x64", true);
            if (os.contains("linux") && x64)
                return new NativeBundle("linux-x64", false);
            if (os.contains("linux") && arm64)
                return new NativeBundle("linux-arm64", false);
            if (os.contains("mac") && x64)
                return new NativeBundle("macos-x64", false);
            if (os.contains("mac") && arm64)
                return new NativeBundle("macos-arm64", false);

            return null;
        }
    }
}
