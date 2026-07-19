package dev.turtywurty.mediabox.gradle

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.tukaani.xz.XZInputStream

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@CacheableTask
abstract class ExtractFfmpegNatives extends DefaultTask {
    @Input
    abstract MapProperty<String, String> getArchiveFileNames()

    @Input
    abstract MapProperty<String, String> getFfmpegEntryPaths()

    @Input
    abstract MapProperty<String, String> getFfprobeEntryPaths()

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    abstract ConfigurableFileCollection getArchiveFiles()

    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory()

    @Inject
    abstract FileSystemOperations getFileSystemOperations()

    @TaskAction
    void extract() {
        File destination = outputDirectory.get().asFile
        fileSystemOperations.delete { delete(destination) }
        Files.createDirectories(destination.toPath())

        Map<String, File> archivesByName = archiveFiles.files.collectEntries { File archive ->
            [(archive.name): archive]
        }

        archiveFileNames.get().each { String id, String archiveFileName ->
            File archive = archivesByName.get(archiveFileName)
            if (archive == null) {
                throw new GradleException("Missing FFmpeg archive ${archiveFileName} for ${id}")
            }

            ExtractFfmpegNatives.extractTool(
                    id,
                    'ffmpeg',
                    archive,
                    ffmpegEntryPaths.get().get(id),
                    destination
            )
            ExtractFfmpegNatives.extractTool(
                    id,
                    'ffprobe',
                    archive,
                    ffprobeEntryPaths.get().get(id),
                    destination
            )
        }
    }

    static void extractTool(
            String id,
            String tool,
            File archive,
            String archivePath,
            File destination
    ) {
        String outputName = tool + (id.startsWith('windows-') ? '.exe' : '')
        File outputFile = new File(destination, "ffmpeg/${id}/${outputName}")
        Files.createDirectories(outputFile.parentFile.toPath())

        if (archive.name.endsWith('.zip')) {
            ExtractFfmpegNatives.extractZipEntry(archive, archivePath, outputFile)
        } else if (archive.name.endsWith('.tar.xz')) {
            ExtractFfmpegNatives.extractTarXzEntry(archive, archivePath, outputFile)
        } else {
            throw new GradleException("Unsupported archive type: ${archive.name}")
        }
    }

    static void extractZipEntry(File archive, String entryName, File outputFile) {
        new ZipFile(archive).withCloseable { ZipFile zipFile ->
            ZipEntry entry = zipFile.getEntry(entryName)
            if (entry == null) {
                throw new GradleException("Missing ${entryName} in ${archive.absolutePath}")
            }

            zipFile.getInputStream(entry).withCloseable { InputStream input ->
                Files.copy(input, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    static void extractTarXzEntry(File archive, String entryName, File outputFile) {
        boolean extracted = false
        archive.withInputStream { InputStream fileInput ->
            new XZInputStream(fileInput).withCloseable { XZInputStream xzInput ->
                new TarArchiveInputStream(xzInput).withCloseable { TarArchiveInputStream tarInput ->
                    TarArchiveEntry entry
                    while ((entry = tarInput.nextEntry) != null) {
                        if (entry.name == entryName) {
                            outputFile.withOutputStream { OutputStream output ->
                                output << tarInput
                            }
                            extracted = true
                            break
                        }
                    }
                }
            }
        }

        if (!extracted) {
            throw new GradleException("Missing ${entryName} in ${archive.absolutePath}")
        }
    }
}
