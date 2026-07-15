package dev.turtywurty.mediaplayer.client;

import dev.turtywurty.mediaplayer.sound.RadioClientAudioManager;
import net.blay09.mods.balm.client.BalmClientRegistrars;
import net.blay09.mods.balm.client.platform.event.callback.ClientTickCallback;

public class MediaPlayerClient {
    private static boolean extractedFfprobe;

    public static void initialize(BalmClientRegistrars registrars) {
        ClientTickCallback.AFTER.register(minecraft -> {
            if (!extractedFfprobe) {
                FfprobeNativeExtractor.extract();
                extractedFfprobe = true;
            }

            RadioClientAudioManager.tick(minecraft);
        });
    }
}
