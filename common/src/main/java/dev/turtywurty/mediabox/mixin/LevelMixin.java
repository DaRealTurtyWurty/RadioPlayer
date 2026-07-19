package dev.turtywurty.mediabox.mixin;

import dev.turtywurty.mediabox.cable.CableConnectionLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public class LevelMixin {
    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void mediabox$popBlockedVisibleCables(
            BlockPos pos,
            BlockState oldState,
            BlockState newState,
            CallbackInfo ci) {
        if ((Object) this instanceof ServerLevel serverLevel && !oldState.equals(newState))
            CableConnectionLifecycle.onBlockChanged(serverLevel, pos);
    }
}
