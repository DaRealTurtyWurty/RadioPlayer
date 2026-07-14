package dev.turtywurty.radioplayer.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RadioAudioSource {
    private static final Map<Identifier, RadioAudioSource> SOURCES_BY_SOUND_PATH = new ConcurrentHashMap<>();
    private final BlockPos radioPos;
    private String url;
    private RadioSoundInstance sound;
    private volatile List<RadioAudioEmitter> emitters = List.of();
    private volatile Vec3 listenerPos = Vec3.ZERO;
    private volatile Vec3 listenerRight = new Vec3(1, 0, 0);

    public RadioAudioSource(BlockPos radioPos, String url) {
        this.radioPos = radioPos.immutable();
        this.url = url;
    }

    public static @Nullable RadioAudioSource getBySoundPath(Identifier soundPath) {
        return SOURCES_BY_SOUND_PATH.get(soundPath);
    }

    public void start(Minecraft minecraft) {
        if (this.sound != null)
            return;

        this.sound = new RadioSoundInstance(this.radioPos, this.url);
        SOURCES_BY_SOUND_PATH.put(this.sound.getSoundPath(), this);
        minecraft.getSoundManager().play(this.sound);
    }

    public void stop(Minecraft minecraft) {
        if (this.sound != null) {
            this.sound.stop();
            SOURCES_BY_SOUND_PATH.remove(this.sound.getSoundPath());
            minecraft.getSoundManager().stop(this.sound);
            this.sound = null;
        }
    }

    public boolean isFor(String url) {
        return this.url.equals(url);
    }

    public boolean shouldRestart(Minecraft minecraft) {
        return this.sound == null ||
                (!minecraft.getSoundManager().isActive(this.sound) && this.sound.isPastStartupGracePeriod());
    }

    public boolean isStreamReady() {
        return this.sound != null && this.sound.isStreamReady();
    }

    public void updateEmitters(List<RadioAudioEmitter> emitters) {
        this.emitters = List.copyOf(emitters);
    }

    public List<RadioAudioEmitter> getEmitters() {
        return this.emitters;
    }

    public void updateListener(Player player) {
        this.listenerPos = player.position();

        float yaw = player.getYRot();
        double yawRadians = Math.toRadians(yaw);
        this.listenerRight = new Vec3(-Math.cos(yawRadians), 0, -Math.sin(yawRadians));
    }

    public Vec3 getListenerPos() {
        return this.listenerPos;
    }

    public Vec3 getListenerRight() {
        return this.listenerRight;
    }

    public String getUrl() {
        return this.url;
    }
}
