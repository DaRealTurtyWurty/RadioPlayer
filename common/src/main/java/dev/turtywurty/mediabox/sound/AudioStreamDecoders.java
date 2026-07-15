package dev.turtywurty.mediabox.sound;

import net.minecraft.client.sounds.AudioStream;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AudioStreamDecoders {
    private static final List<AudioStreamDecoder> DECODERS = new CopyOnWriteArrayList<>();

    private AudioStreamDecoders() {
    }

    /**
     * Registers a decoder ahead of the default FFmpeg decoder.
     */
    public static void register(@NonNull AudioStreamDecoder decoder) {
        DECODERS.add(decoder);
    }

    public static AudioStream open(String mediaLocation) throws IOException {
        for (AudioStreamDecoder decoder : DECODERS) {
            if (decoder.supports(mediaLocation)) {
                return decoder.open(mediaLocation);
            }
        }

        return FfmpegAudioStream.open(mediaLocation);
    }
}
