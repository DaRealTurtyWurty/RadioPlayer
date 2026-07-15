package dev.turtywurty.mediabox.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientAudioSource {
    private static final Map<Identifier, ClientAudioSource> SOURCES_BY_SOUND_PATH = new ConcurrentHashMap<>();
    private final BlockPos sourcePos;
    private final AudioPlaybackState playbackState;
    private MediaSoundInstance sound;
    private volatile List<AudioEmitter> emitters = List.of();
    private volatile Vec3 listenerPos = Vec3.ZERO;
    private volatile Vec3 listenerRight = new Vec3(1, 0, 0);

    public ClientAudioSource(BlockPos sourcePos, AudioPlaybackState playbackState) {
        this.sourcePos = sourcePos.immutable();
        this.playbackState = playbackState;
    }

    public static @Nullable ClientAudioSource getBySoundPath(Identifier soundPath) {
        return SOURCES_BY_SOUND_PATH.get(soundPath);
    }

    public void start(Minecraft minecraft) {
        if (this.sound != null)
            return;

        this.sound = new MediaSoundInstance(this.sourcePos, this.playbackState);
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

    public boolean isFor(AudioPlaybackState playbackState) {
        return this.playbackState.equals(playbackState);
    }

    public boolean shouldRestart(Minecraft minecraft) {
        return this.sound == null ||
                (!minecraft.getSoundManager().isActive(this.sound) && this.sound.isPastStartupGracePeriod());
    }

    public boolean isStreamReady() {
        return this.sound != null && this.sound.isStreamReady();
    }

    public void updateEmitters(List<AudioEmitter> emitters) {
        this.emitters = List.copyOf(emitters);
    }

    public List<AudioEmitter> getEmitters() {
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

    public String getMediaLocation() {
        return this.playbackState.mediaLocation();
    }
}
