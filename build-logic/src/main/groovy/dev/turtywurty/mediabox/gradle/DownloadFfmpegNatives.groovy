package dev.turtywurty.mediabox.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

@CacheableTask
abstract class DownloadFfmpegNatives extends DefaultTask {
    @Input
    abstract MapProperty<String, String> getBundleUrls()

    @Input
    abstract MapProperty<String, String> getBundleChecksums()

    @Internal
    abstract DirectoryProperty getDestinationDirectory()

    @OutputFiles
    abstract ConfigurableFileCollection getDownloadedArchives()

    @TaskAction
    void downloadAndVerify() {
        Map<String, String> urls = bundleUrls.get()
        Map<String, String> checksums = bundleChecksums.get()
        File outputDirectory = destinationDirectory.get().asFile
        Files.createDirectories(outputDirectory.toPath())

        logger.lifecycle("Bundling FFmpeg natives for: ${urls.keySet().join(', ')}")

        urls.each { String id, String url ->
            String expectedChecksum = checksums.get(id)
            if (expectedChecksum == null || expectedChecksum.isBlank()) {
                throw new GradleException("Missing SHA-256 checksum for ${id}")
            }

            String fileName = fileName(url)
            File outputFile = new File(outputDirectory, fileName)
            File temporaryFile = new File(outputDirectory, "${fileName}.part")

            try {
                if (!outputFile.exists()) {
                    logger.lifecycle("Downloading ${id}...")
                    Files.deleteIfExists(temporaryFile.toPath())
                    DownloadFfmpegNatives.download(url, temporaryFile)
                    DownloadFfmpegNatives.verifyChecksum(id, temporaryFile, expectedChecksum)
                    Files.move(
                            temporaryFile.toPath(),
                            outputFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                    )
                }

                DownloadFfmpegNatives.verifyChecksum(id, outputFile, expectedChecksum)
            } catch (Exception exception) {
                Files.deleteIfExists(temporaryFile.toPath())
                Files.deleteIfExists(outputFile.toPath())
                throw new GradleException("Failed to download or verify ${id}", exception)
            }
        }
    }

    static String fileName(String url) {
        return url.substring(url.lastIndexOf('/') + 1)
    }

    static void download(String url, File outputFile) {
        URLConnection connection = URI.create(url).toURL().openConnection()
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.setRequestProperty('User-Agent', 'MediaBox Gradle build')
        connection.inputStream.withCloseable { InputStream input ->
            outputFile.withOutputStream { OutputStream output ->
                output << input
            }
        }
    }

    static void verifyChecksum(String id, File file, String expectedChecksum) {
        String actualChecksum = DownloadFfmpegNatives.sha256(file)
        if (!actualChecksum.equalsIgnoreCase(expectedChecksum)) {
            throw new GradleException("""SHA-256 mismatch for ${id}
Expected: ${expectedChecksum}
Actual:   ${actualChecksum}
File:     ${file.absolutePath}
""")
        }
    }

    static String sha256(File file) {
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        file.withInputStream { InputStream input ->
            byte[] buffer = new byte[8192]
            int read
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().collect { byte value -> String.format('%02x', value) }.join()
    }
}
