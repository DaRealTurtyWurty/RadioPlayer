package dev.turtywurty.mediaplayer.sound;

import net.minecraft.client.sounds.AudioStream;

import java.io.IOException;

/**
 * Opens media locations for the Minecraft sound engine.
 * Implementations can support future location formats used by discs, CDs, or cassettes.
 */
public interface AudioStreamDecoder {
    boolean supports(String mediaLocation);

    AudioStream open(String mediaLocation) throws IOException;
}
