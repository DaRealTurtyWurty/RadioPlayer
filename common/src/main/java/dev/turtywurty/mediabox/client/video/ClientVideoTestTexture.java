package dev.turtywurty.mediabox.client.video;

import com.mojang.blaze3d.platform.NativeImage;
import dev.turtywurty.mediabox.MediaBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

public final class ClientVideoTestTexture {
    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    private static final Identifier LOCATION = MediaBox.id("dynamic/video_test");

    private static @Nullable DynamicTexture texture;

    private static @Nullable FfmpegVideoDecoder decoder;
    private static boolean receivedFrame;

    private ClientVideoTestTexture() {
    }

    public static Identifier location() {
        return LOCATION;
    }

    public static boolean isReady() {
        return texture != null && decoder != null && receivedFrame;
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null)
            return;

        ensureCreated(minecraft);

        if (texture == null || decoder == null)
            return;

        byte[] frame = decoder.takeLatestFrame();
        if (frame == null)
            return;

        NativeImage image = texture.getPixels();
        ByteBuffer pixelBytes = image.getPixelBytes();

        pixelBytes.clear();
        pixelBytes.put(frame);
        pixelBytes.flip();

        texture.upload();
        receivedFrame = true;
    }

    private static void ensureCreated(Minecraft minecraft) {
        if (texture != null)
            return;

        texture = new DynamicTexture(
                () -> "MediaBox video test",
                WIDTH,
                HEIGHT,
                true
        );

        minecraft.getTextureManager().register(LOCATION, texture);

        Path videoPath = minecraft.gameDirectory.toPath()
                .resolve("2021-06-17-183704972.mp4")
                .toAbsolutePath()
                .normalize();

        try {
            decoder = FfmpegVideoDecoder.open(
                    minecraft.gameDirectory.toPath(),
                    videoPath.toString(),
                    WIDTH,
                    HEIGHT,
                    20,
                    true
            );
        } catch (IOException exception) {
            MediaBox.LOGGER.error(
                    "Could not start test video {}",
                    videoPath,
                    exception
            );
        }
    }

    public static void close(Minecraft minecraft) {
        if (decoder != null) {
            decoder.close();
            decoder = null;
        }

        if (texture != null) {
            minecraft.getTextureManager().release(LOCATION);
            texture = null;
        }

        receivedFrame = false;
    }
}
