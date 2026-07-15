package dev.turtywurty.mediaplayer.sound;

import dev.turtywurty.mediaplayer.block.SpeakerBlock;
import dev.turtywurty.mediaplayer.block.SpeakerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction8;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClientAudioManager {
    private static final Set<BlockPos> KNOWN_SOURCES = new HashSet<>();
    private static final Set<BlockPos> KNOWN_SPEAKERS = new HashSet<>();
    private static final Map<BlockPos, ClientAudioSource> ACTIVE_SOURCES = new HashMap<>();
    private static final Map<BlockPos, RadioStaticSoundInstance> ACTIVE_LOADING_STATIC = new HashMap<>();
    private static final Map<BlockPos, List<AudioEmitter>> BUILT_IN_EMITTERS = new HashMap<>();
    private static final Map<BlockPos, Set<AudioEmitter>> SPEAKER_EMITTERS_BY_SOURCE = new HashMap<>();

    private ClientAudioManager() {
    }

    public static void registerAudioSource(BlockPos pos) {
        KNOWN_SOURCES.add(pos.immutable());
    }

    public static void registerSpeaker(BlockPos pos) {
        KNOWN_SPEAKERS.add(pos.immutable());
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            stopAll(minecraft);
            return;
        }

        SPEAKER_EMITTERS_BY_SOURCE.clear();

        KNOWN_SOURCES.removeIf(pos -> {
            BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
            if (!(blockEntity instanceof AudioSourceProvider provider)) {
                unregisterSource(pos, minecraft);
                return true;
            }

            BlockPos sourcePos = provider.getAudioSourcePos().immutable();
            BUILT_IN_EMITTERS.put(sourcePos, List.copyOf(provider.getBuiltInAudioEmitters()));

            AudioPlaybackState playbackState = provider.getAudioPlaybackState();
            ClientAudioSource active = ACTIVE_SOURCES.get(sourcePos);
            if (!playbackState.isPlayable()) {
                stopPlayback(sourcePos, minecraft);
                return false;
            }

            if (active == null || !active.isFor(playbackState) || active.shouldRestart(minecraft)) {
                stopPlayback(sourcePos, minecraft);

                var source = new ClientAudioSource(sourcePos, playbackState);
                ACTIVE_SOURCES.put(sourcePos, source);
                source.start(minecraft);
            }

            updateLoadingStatic(sourcePos, provider.playsLoadingStatic(), minecraft);
            return false;
        });

        KNOWN_SPEAKERS.removeIf(pos -> {
            BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
            if (!(blockEntity instanceof SpeakerBlockEntity speaker)) {
                BlockState blockState = minecraft.level.getBlockState(pos);
                return !(blockState.getBlock() instanceof SpeakerBlock);
            }

            AudioSourceProvider sourceProvider = speaker.findAudioSource();
            if (sourceProvider != null) {
                BlockPos sourcePos = sourceProvider.getAudioSourcePos().immutable();
                if (KNOWN_SOURCES.contains(sourcePos)) {
                    SpeakerType speakerType = speaker.getBlockState().getBlock() instanceof SpeakerBlock speakerBlock
                            ? speakerBlock.getSpeakerType()
                            : SpeakerType.FULL_RANGE;

                    var emitter = new AudioEmitter(
                            pos.immutable(),
                            speaker.getBlockState().getValue(SpeakerBlock.FACING).asDirection8(),
                            speakerType,
                            1.0F);

                    SPEAKER_EMITTERS_BY_SOURCE
                            .computeIfAbsent(sourcePos, ignored -> new HashSet<>())
                            .add(emitter);
                }
            }

            return false;
        });

        updateAudioSources(minecraft);
        stopOrphanedAudio(minecraft);
    }

    private static void updateLoadingStatic(BlockPos pos, boolean enabled, Minecraft minecraft) {
        ClientAudioSource audioSource = ACTIVE_SOURCES.get(pos);
        RadioStaticSoundInstance staticSound = ACTIVE_LOADING_STATIC.get(pos);

        if (!enabled || audioSource == null) {
            stopLoadingStatic(pos, minecraft);
            return;
        }

        if (staticSound != null && staticSound.isStopped()) {
            stopLoadingStatic(pos, minecraft);
            return;
        }

        if (audioSource.isStreamReady()) {
            if (staticSound != null) {
                staticSound.fadeOut();
            }

            return;
        }

        if (staticSound == null) {
            RadioStaticSoundInstance newStaticSound = new RadioStaticSoundInstance(pos);
            ACTIVE_LOADING_STATIC.put(pos, newStaticSound);
            minecraft.getSoundManager().play(newStaticSound);
        }
    }

    private static void stopPlayback(BlockPos pos, Minecraft minecraft) {
        ClientAudioSource source = ACTIVE_SOURCES.remove(pos);
        if (source != null) {
            source.stop(minecraft);
        }

        stopLoadingStatic(pos, minecraft);
    }

    private static void unregisterSource(BlockPos pos, Minecraft minecraft) {
        stopPlayback(pos, minecraft);
        BUILT_IN_EMITTERS.remove(pos);
        SPEAKER_EMITTERS_BY_SOURCE.remove(pos);
    }

    private static void stopLoadingStatic(BlockPos pos, Minecraft minecraft) {
        RadioStaticSoundInstance staticSound = ACTIVE_LOADING_STATIC.remove(pos);
        if (staticSound != null) {
            minecraft.getSoundManager().stop(staticSound);
        }
    }

    private static void stopAll(Minecraft minecraft) {
        for (BlockPos pos : Set.copyOf(ACTIVE_SOURCES.keySet())) {
            stopPlayback(pos, minecraft);
        }

        KNOWN_SOURCES.clear();
        KNOWN_SPEAKERS.clear();
        BUILT_IN_EMITTERS.clear();
        SPEAKER_EMITTERS_BY_SOURCE.clear();
    }

    private static void stopOrphanedAudio(Minecraft minecraft) {
        Set<BlockPos> knownSources = Set.copyOf(KNOWN_SOURCES);
        for (BlockPos pos : Set.copyOf(ACTIVE_SOURCES.keySet())) {
            if (!knownSources.contains(pos)) {
                unregisterSource(pos, minecraft);
            }
        }
    }

    private static void updateAudioSources(Minecraft minecraft) {
        for (Map.Entry<BlockPos, ClientAudioSource> entry : ACTIVE_SOURCES.entrySet()) {
            ClientAudioSource source = entry.getValue();
            Set<AudioEmitter> speakerEmitters = SPEAKER_EMITTERS_BY_SOURCE.getOrDefault(entry.getKey(), Set.of());

            List<AudioEmitter> emitters = new ArrayList<>();
            if (speakerEmitters.isEmpty()) {
                emitters.addAll(BUILT_IN_EMITTERS.getOrDefault(
                        entry.getKey(),
                        List.of(new AudioEmitter(entry.getKey(), Direction8.NORTH, SpeakerType.FULL_RANGE, 1.0F))));
            }

            emitters.addAll(speakerEmitters);
            source.updateListener(minecraft.player);
            source.updateEmitters(emitters);
        }
    }
}
