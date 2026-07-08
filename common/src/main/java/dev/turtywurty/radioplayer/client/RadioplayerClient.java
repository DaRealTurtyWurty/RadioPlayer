package dev.turtywurty.radioplayer.client;

import dev.turtywurty.radioplayer.sound.RadioClientAudioManager;
import net.blay09.mods.balm.client.BalmClientRegistrars;
import net.blay09.mods.balm.client.platform.event.callback.ClientTickCallback;

public class RadioplayerClient {
    public static void initialize(BalmClientRegistrars registrars) {
        ClientTickCallback.AFTER.register(RadioClientAudioManager::tick);
    }
}
