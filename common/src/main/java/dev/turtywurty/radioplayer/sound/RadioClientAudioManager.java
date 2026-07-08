package dev.turtywurty.radioplayer.sound;

import dev.turtywurty.radioplayer.block.ModBlocks;
import dev.turtywurty.radioplayer.block.entity.RadioPlayerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class RadioClientAudioManager {
    private static final Set<BlockPos> KNOWN_RADIOS = new HashSet<>();
    private static final Map<BlockPos, RadioSoundInstance> ACTIVE_SOUNDS = new HashMap<>();
    private static final Map<BlockPos, RadioStaticSoundInstance> ACTIVE_STATIC = new HashMap<>();

    private RadioClientAudioManager() {
    }

    public static void registerRadio(BlockPos pos) {
        KNOWN_RADIOS.add(pos.immutable());
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            stopAll(minecraft);
            return;
        }

        KNOWN_RADIOS.removeIf(pos -> {
            BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);

            if (!(blockEntity instanceof RadioPlayerBlockEntity radio)) {
                stop(pos, minecraft);
                BlockState blockState = minecraft.level.getBlockState(pos);
                return !blockState.is(ModBlocks.radioPlayer.asBlock());
            }

            String url = radio.getUrl();
            RadioSoundInstance active = ACTIVE_SOUNDS.get(pos);

            if (url.isBlank() || !radio.isPlaying()) {
                stop(pos, minecraft);
                return false;
            }

            if (active == null || !active.getUrl().equals(url) ||
                    (!minecraft.getSoundManager().isActive(active) && active.isPastStartupGracePeriod())) {
                stop(pos, minecraft);

                RadioSoundInstance sound = new RadioSoundInstance(pos, url);
                ACTIVE_SOUNDS.put(pos, sound);
                minecraft.getSoundManager().play(sound);
            }

            updateStatic(pos, minecraft);
            return false;
        });
    }

    private static void updateStatic(BlockPos pos, Minecraft minecraft) {
        RadioSoundInstance radioSound = ACTIVE_SOUNDS.get(pos);
        RadioStaticSoundInstance staticSound = ACTIVE_STATIC.get(pos);

        if (radioSound == null) {
            stopStatic(pos, minecraft);
            return;
        }

        if (staticSound != null && staticSound.isStopped()) {
            stopStatic(pos, minecraft);
            return;
        }

        if (radioSound.isStreamReady()) {
            if (staticSound != null) {
                staticSound.fadeOut();
            }

            return;
        }

        if (staticSound == null) {
            RadioStaticSoundInstance newStaticSound = new RadioStaticSoundInstance(pos);
            ACTIVE_STATIC.put(pos, newStaticSound);
            minecraft.getSoundManager().play(newStaticSound);
        }
    }

    private static void stop(BlockPos pos, Minecraft minecraft) {
        RadioSoundInstance sound = ACTIVE_SOUNDS.remove(pos);
        if (sound != null) {
            sound.stop();
            minecraft.getSoundManager().stop(sound);
        }

        stopStatic(pos, minecraft);
    }

    private static void stopStatic(BlockPos pos, Minecraft minecraft) {
        RadioStaticSoundInstance staticSound = ACTIVE_STATIC.remove(pos);
        if (staticSound != null) {
            minecraft.getSoundManager().stop(staticSound);
        }
    }

    private static void stopAll(Minecraft minecraft) {
        for (BlockPos pos : Set.copyOf(ACTIVE_SOUNDS.keySet())) {
            stop(pos, minecraft);
        }

        KNOWN_RADIOS.clear();
    }
}
