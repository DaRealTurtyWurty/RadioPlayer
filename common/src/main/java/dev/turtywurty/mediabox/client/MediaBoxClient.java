package dev.turtywurty.mediabox.client;

import dev.turtywurty.mediabox.client.cable.ClientCableState;
import dev.turtywurty.mediabox.client.screen.ClientScreenState;
import dev.turtywurty.mediabox.client.video.ClientScreenPlaybackState;
import dev.turtywurty.mediabox.client.video.ClientVideoManager;
import dev.turtywurty.mediabox.ffmpeg.FfmpegNatives;
import dev.turtywurty.mediabox.sound.ClientAudioManager;
import net.blay09.mods.balm.client.BalmClientRegistrars;
import net.blay09.mods.balm.client.platform.event.callback.ClientLifecycleCallback;
import net.blay09.mods.balm.client.platform.event.callback.ClientTickCallback;

public class MediaBoxClient {
    private static boolean extractedFfmpeg;

    public static void initialize(BalmClientRegistrars registrars) {
        ClientLifecycleCallback.DisconnectedFromServer.EVENT.register(_ -> {
            ClientCableState.clear();
            ClientScreenState.clear();
            ClientScreenPlaybackState.clear();
            ClientVideoManager.clear();
        });

        ClientTickCallback.AFTER.register(minecraft -> {
            if (!extractedFfmpeg) {
                FfmpegNatives.extract(minecraft.gameDirectory.toPath());
                extractedFfmpeg = true;
            }

            ClientAudioManager.tick(minecraft);
            if (minecraft.level != null) {
                ClientVideoManager.reconcile(minecraft);
            }
        });
    }
}
