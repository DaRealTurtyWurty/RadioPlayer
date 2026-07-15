package dev.turtywurty.mediabox.client;

import dev.turtywurty.mediabox.sound.ClientAudioManager;
import net.blay09.mods.balm.client.BalmClientRegistrars;
import net.blay09.mods.balm.client.platform.event.callback.ClientTickCallback;

public class MediaBoxClient {
    private static boolean extractedFfprobe;

    public static void initialize(BalmClientRegistrars registrars) {
        ClientTickCallback.AFTER.register(minecraft -> {
            if (!extractedFfprobe) {
                FfprobeNativeExtractor.extract();
                extractedFfprobe = true;
            }

            ClientAudioManager.tick(minecraft);
        });
    }
}
