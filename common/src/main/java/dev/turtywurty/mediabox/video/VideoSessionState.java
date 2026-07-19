package dev.turtywurty.mediabox.video;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

public record VideoSessionState(
        UUID sessionId,
        VideoSource source,
        PlaybackStatus status,
        long epochGameTick,
        double positionAtEpochSeconds,
        boolean looping
) {
    public static final Codec<VideoSessionState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC
                            .fieldOf("session_id")
                            .forGetter(VideoSessionState::sessionId),
                    VideoSource.CODEC
                            .fieldOf("source")
                            .forGetter(VideoSessionState::source),
                    PlaybackStatus.CODEC
                            .fieldOf("status")
                            .forGetter(VideoSessionState::status),
                    Codec.LONG
                            .fieldOf("epoch_game_tick")
                            .forGetter(VideoSessionState::epochGameTick),
                    Codec.DOUBLE
                            .fieldOf("position_at_epoch_seconds")
                            .forGetter(VideoSessionState::positionAtEpochSeconds),
                    Codec.BOOL
                            .fieldOf("looping")
                            .forGetter(VideoSessionState::looping)
            ).apply(instance, VideoSessionState::new));
}
