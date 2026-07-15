package dev.turtywurty.mediabox.sound;

import dev.turtywurty.mediabox.MediaBox;
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

public class RadioStaticSoundInstance implements TickableSoundInstance {
    private static final int FADE_OUT_TICKS = 20;
    private static final float BASE_VOLUME = 0.42F;
    private static final Identifier SOUND_RESOURCE = MediaBox.id("radio_static");

    private final BlockPos radioPos;
    private final Identifier identifier;
    private final Sound sound;
    private final WeighedSoundEvents soundEvents;
    private float volume = BASE_VOLUME;
    private float pitch = 1.0F;
    private int ticks;
    private int fadeOutTicks;
    private boolean stopped;

    public RadioStaticSoundInstance(BlockPos radioPos) {
        this.radioPos = radioPos;
        this.identifier = MediaBox.id("radio_static/" + radioPos.asLong());
        this.sound = new Sound(
                SOUND_RESOURCE,
                ConstantFloat.of(1.0F),
                ConstantFloat.of(1.0F),
                1,
                Sound.Type.FILE,
                false,
                false,
                16);
        this.soundEvents = new WeighedSoundEvents(this.identifier, null);
        this.soundEvents.addSound(this.sound);
    }

    public void fadeOut() {
        if (!this.stopped && this.fadeOutTicks <= 0) {
            this.fadeOutTicks = FADE_OUT_TICKS;
        }
    }

    @Override
    public boolean isStopped() {
        return this.stopped;
    }

    @Override
    public void tick() {
        this.ticks++;
        float volumeVariation = (float) (Math.sin(this.ticks * 0.45F) * 0.07F + Math.sin(this.ticks * 0.13F) * 0.04F);
        float pitchVariation = (float) (Math.sin(this.ticks * 0.37F) * 0.06F + Math.sin(this.ticks * 0.09F) * 0.03F);
        float fadeMultiplier = 1.0F;

        if (this.fadeOutTicks > 0) {
            this.fadeOutTicks--;
            fadeMultiplier = (float) this.fadeOutTicks / FADE_OUT_TICKS;

            if (this.fadeOutTicks == 0) {
                this.stopped = true;
            }
        }

        this.volume = Math.max(0.0F, (BASE_VOLUME + volumeVariation) * fadeMultiplier);
        this.pitch = Math.max(0.75F, 1.0F + pitchVariation);
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
        return true;
    }

    @Override
    public boolean isRelative() {
        return false;
    }

    @Override
    public int getDelay() {
        return 0;
    }

    @Override
    public float getVolume() {
        return this.volume;
    }

    @Override
    public float getPitch() {
        return this.pitch;
    }

    @Override
    public double getX() {
        return this.radioPos.getX() + 0.5;
    }

    @Override
    public double getY() {
        return this.radioPos.getY() + 0.5;
    }

    @Override
    public double getZ() {
        return this.radioPos.getZ() + 0.5;
    }

    @Override
    public @NonNull Attenuation getAttenuation() {
        return Attenuation.LINEAR;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public boolean canPlaySound() {
        return !this.stopped;
    }
}
