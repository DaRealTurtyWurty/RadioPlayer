package dev.turtywurty.radioplayer.sound;

import dev.turtywurty.radioplayer.block.ModBlocks;
import dev.turtywurty.radioplayer.block.RadioPlayerBlock;
import dev.turtywurty.radioplayer.block.SpeakerBlock;
import dev.turtywurty.radioplayer.block.SpeakerBlockEntity;
import dev.turtywurty.radioplayer.block.entity.RadioPlayerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction8;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public final class RadioClientAudioManager {
    private static final Set<BlockPos> KNOWN_RADIOS = new HashSet<>();
    private static final Set<BlockPos> KNOWN_SPEAKERS = new HashSet<>();
    private static final Map<BlockPos, RadioAudioSource> ACTIVE_SOURCES = new HashMap<>();
    private static final Map<BlockPos, RadioStaticSoundInstance> ACTIVE_STATIC = new HashMap<>();
    private static final Map<BlockPos, RadioAudioEmitter> RADIO_EMITTERS = new HashMap<>();
    private static final Map<BlockPos, Set<RadioAudioEmitter>> EMITTERS_BY_RADIO = new HashMap<>();

    private RadioClientAudioManager() {
    }

    public static void registerRadio(BlockPos pos) {
        KNOWN_RADIOS.add(pos.immutable());
    }

    public static void registerSpeaker(BlockPos pos) {
        KNOWN_SPEAKERS.add(pos.immutable());
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            stopAll(minecraft);
            return;
        }

        EMITTERS_BY_RADIO.clear();

        KNOWN_RADIOS.removeIf(pos -> {
            BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);

            if (!(blockEntity instanceof RadioPlayerBlockEntity radio)) {
                stop(pos, minecraft);
                BlockState blockState = minecraft.level.getBlockState(pos);
                return !blockState.is(ModBlocks.radioPlayer.asBlock());
            }

            RADIO_EMITTERS.put(pos, new RadioAudioEmitter(
                    pos.immutable(),
                    radio.getBlockState().getValue(RadioPlayerBlock.FACING).asDirection8(),
                    SpeakerType.FULL_RANGE,
                    1.0F));

            String url = radio.getUrl();
            RadioAudioSource active = ACTIVE_SOURCES.get(pos);

            if (url.isBlank() || !radio.isPlaying()) {
                stop(pos, minecraft);
                return false;
            }

            if (active == null || !active.isFor(url) || active.shouldRestart(minecraft)) {
                stop(pos, minecraft);

                var source = new RadioAudioSource(pos, url);
                ACTIVE_SOURCES.put(pos, source);
                source.start(minecraft);
            }

            updateStatic(pos, minecraft);
            return false;
        });

        KNOWN_SPEAKERS.removeIf(pos -> {
            BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);

            if (!(blockEntity instanceof SpeakerBlockEntity speaker)) {
                BlockState blockState = minecraft.level.getBlockState(pos);
                return !(blockState.getBlock() instanceof SpeakerBlock);
            }

            RadioPlayerBlockEntity sourceRadio = speaker.findSourceRadio();
            if (sourceRadio != null && KNOWN_RADIOS.contains(sourceRadio.getBlockPos())) {
                SpeakerType speakerType = speaker.getBlockState().getBlock() instanceof SpeakerBlock speakerBlock
                        ? speakerBlock.getSpeakerType()
                        : SpeakerType.FULL_RANGE;

                var emitter = new RadioAudioEmitter(
                        pos.immutable(),
                        speaker.getBlockState().getValue(SpeakerBlock.FACING).asDirection8(),
                        speakerType,
                        1.0F);

                EMITTERS_BY_RADIO
                        .computeIfAbsent(sourceRadio.getBlockPos().immutable(), ignored -> new HashSet<>())
                        .add(emitter);
            }

            return false;
        });

        updateAudioSources(minecraft);
        stopOrphanedAudio(minecraft);
    }

    private static void updateStatic(BlockPos pos, Minecraft minecraft) {
        RadioAudioSource radioSound = ACTIVE_SOURCES.get(pos);
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
        RadioAudioSource source = ACTIVE_SOURCES.remove(pos);
        if (source != null) {
            source.stop(minecraft);
        }

        RADIO_EMITTERS.remove(pos);
        stopStatic(pos, minecraft);
    }

    private static void stopStatic(BlockPos pos, Minecraft minecraft) {
        RadioStaticSoundInstance staticSound = ACTIVE_STATIC.remove(pos);
        if (staticSound != null) {
            minecraft.getSoundManager().stop(staticSound);
        }
    }

    private static void stopAll(Minecraft minecraft) {
        for (BlockPos pos : Set.copyOf(ACTIVE_SOURCES.keySet())) {
            stop(pos, minecraft);
        }

        KNOWN_RADIOS.clear();
        KNOWN_SPEAKERS.clear();
        RADIO_EMITTERS.clear();
        EMITTERS_BY_RADIO.clear();
    }

    private static void stopOrphanedAudio(Minecraft minecraft) {
        Set<BlockPos> knownRadios = Set.copyOf(KNOWN_RADIOS);
        for (BlockPos pos : Set.copyOf(ACTIVE_SOURCES.keySet())) {
            if (!knownRadios.contains(pos)) {
                stop(pos, minecraft);
            }
        }
    }

    private static void updateAudioSources(Minecraft minecraft) {
        for (Map.Entry<BlockPos, RadioAudioSource> entry : ACTIVE_SOURCES.entrySet()) {
            RadioAudioSource source = entry.getValue();
            Set<RadioAudioEmitter> speakerEmitters = EMITTERS_BY_RADIO.getOrDefault(entry.getKey(), Set.of());

            List<RadioAudioEmitter> emitters = new ArrayList<>();

            if (speakerEmitters.isEmpty()) {
                emitters.add(RADIO_EMITTERS.getOrDefault(
                        entry.getKey(),
                        new RadioAudioEmitter(
                                entry.getKey(),
                                Direction8.NORTH,
                                SpeakerType.FULL_RANGE,
                                1.0F
                        )));
            }

            emitters.addAll(speakerEmitters);

            source.updateListener(minecraft.player);
            source.updateEmitters(emitters);
        }
    }

}
