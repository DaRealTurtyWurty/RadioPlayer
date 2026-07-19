package dev.turtywurty.mediabox.client;

import dev.turtywurty.mediabox.ffmpeg.FfmpegNatives;
import dev.turtywurty.mediabox.sound.ClientAudioManager;
import dev.turtywurty.mediabox.client.cable.ClientCableState;
import net.blay09.mods.balm.client.BalmClientRegistrars;
import net.blay09.mods.balm.client.platform.event.callback.ClientTickCallback;
import net.blay09.mods.balm.client.platform.event.callback.ClientLifecycleCallback;

public class MediaBoxClient {
    private static boolean extractedFfmpeg;

    public static void initialize(BalmClientRegistrars registrars) {
        ClientLifecycleCallback.DisconnectedFromServer.EVENT.register(minecraft -> ClientCableState.clear());

        ClientTickCallback.AFTER.register(minecraft -> {
            if (!extractedFfmpeg) {
                FfmpegNatives.extract(minecraft.gameDirectory.toPath());
                extractedFfmpeg = true;
            }

            ClientAudioManager.tick(minecraft);
        });
    }
}
