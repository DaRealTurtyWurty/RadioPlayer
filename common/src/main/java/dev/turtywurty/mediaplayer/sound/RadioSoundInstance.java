package dev.turtywurty.mediaplayer.sound;

import dev.turtywurty.mediaplayer.MediaPlayer;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RadioSoundInstance implements TickableSoundInstance {
    private static final String STREAM_PATH = "radio_stream/";
    private static final long STARTUP_GRACE_PERIOD_MS = 3000L;
    private static final Map<Identifier, String> URLS_BY_SOUND_PATH = new ConcurrentHashMap<>();
    private static final Map<Identifier, Boolean> READY_BY_SOUND_PATH = new ConcurrentHashMap<>();

    private final BlockPos radioPos;
    private final String url;
    private final Identifier identifier;
    private final Sound sound;
    private final WeighedSoundEvents soundEvents;
    private final long createdAtMillis = System.currentTimeMillis();
    private boolean stopped;

    public RadioSoundInstance(BlockPos radioPos, String url) {
        this.radioPos = radioPos;
        this.url = url;

        this.identifier = MediaPlayer.id(STREAM_PATH + Long.toHexString(radioPos.asLong()) + "/" + Integer.toHexString(url.hashCode()));
        this.sound = new Sound(
                this.identifier,
                ConstantFloat.of(1.0F),
                ConstantFloat.of(1.0F),
                1,
                Sound.Type.FILE,
                true,
                false,
                16);
        this.soundEvents = new WeighedSoundEvents(this.identifier, null);
        this.soundEvents.addSound(this.sound);

        URLS_BY_SOUND_PATH.put(this.sound.getPath(), this.url);
        READY_BY_SOUND_PATH.put(this.sound.getPath(), false);
    }

    public static @Nullable String getUrl(Identifier soundPath) {
        return URLS_BY_SOUND_PATH.get(soundPath);
    }

    public static void markReady(Identifier soundPath) {
        READY_BY_SOUND_PATH.put(soundPath, true);
    }

    public String getUrl() {
        return this.url;
    }

    public BlockPos getRadioPos() {
        return this.radioPos;
    }

    public void stop() {
        this.stopped = true;
        URLS_BY_SOUND_PATH.remove(this.sound.getPath(), this.url);
        READY_BY_SOUND_PATH.remove(this.sound.getPath());
    }

    public boolean isPastStartupGracePeriod() {
        return System.currentTimeMillis() - this.createdAtMillis >= STARTUP_GRACE_PERIOD_MS;
    }

    public boolean isStreamReady() {
        return READY_BY_SOUND_PATH.getOrDefault(this.sound.getPath(), false);
    }

    @Override
    public boolean isStopped() {
        return this.stopped;
    }

    @Override
    public void tick() {

    }

    @Override
    public @NonNull Identifier getIdentifier() {
        return this.identifier;
    }

    @Override
    public @Nullable WeighedSoundEvents resolve(@NonNull SoundManager soundManager) {
        return this.soundEvents;
    }

    @Override
    public @Nullable Sound getSound() {
        return this.sound;
    }

    @Override
    public @NonNull SoundSource getSource() {
        return SoundSource.RECORDS;
    }

    @Override
    public boolean isLooping() {
        return false;
    }

    @Override
    public boolean isRelative() {
        return true;
    }

    @Override
    public int getDelay() {
        return 0;
    }

    @Override
    public float getVolume() {
        return 1.0F;
    }

    @Override
    public float getPitch() {
        return 1.0F;
    }

    @Override
    public double getX() {
        return 0;
    }

    @Override
    public double getY() {
        return 0;
    }

    @Override
    public double getZ() {
        return 0;
    }

    @Override
    public @NonNull Attenuation getAttenuation() {
        return Attenuation.NONE;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public boolean canPlaySound() {
        return !this.stopped && !this.url.isBlank();
    }

    public Identifier getSoundPath() {
        return this.sound.getPath();
    }
}
