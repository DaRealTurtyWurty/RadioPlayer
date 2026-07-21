package dev.turtywurty.mediabox.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

final class ScreenPlaybackControlAccess {
    private ScreenPlaybackControlAccess() {
    }

    static boolean canControl(ServerPlayer player, BlockPos pos) {
        return player.blockPosition().closerThan(pos, 8.0);
    }
}
