package dev.turtywurty.mediabox.video;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

public record ScreenPlaybackAssignment(
        UUID screenId,
        VideoSessionState session
) {
    public static final Codec<ScreenPlaybackAssignment> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC
                            .fieldOf("screen_id")
                            .forGetter(ScreenPlaybackAssignment::screenId),
                    VideoSessionState.CODEC
                            .fieldOf("session")
                            .forGetter(ScreenPlaybackAssignment::session)
            ).apply(instance, ScreenPlaybackAssignment::new));
}