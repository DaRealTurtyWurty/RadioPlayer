package dev.turtywurty.radioplayer.client;

import dev.turtywurty.radioplayer.sound.RadioClientAudioManager;
import net.blay09.mods.balm.client.BalmClientRegistrars;
import net.blay09.mods.balm.client.platform.event.callback.ClientTickCallback;

public class RadioplayerClient {
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
