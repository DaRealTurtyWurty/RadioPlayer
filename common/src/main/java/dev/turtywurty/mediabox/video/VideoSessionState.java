package dev.turtywurty.mediabox.video;

import java.util.UUID;

public record VideoSessionState(
        UUID sessionId,
        VideoSource source,
        PlaybackStatus status,
        long epochGameTick,
        double positionAtEpochSeconds,
        boolean looping
) {
}
