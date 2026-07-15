package dev.turtywurty.mediaplayer.mixin;

import dev.turtywurty.mediaplayer.block.SpeakerBlockEntity;
import dev.turtywurty.mediaplayer.block.entity.RadioPlayerBlockEntity;
import dev.turtywurty.mediaplayer.sound.RadioClientAudioManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @Inject(method = "onBlockEntityAdded", at = @At("TAIL"))
    private void mediaplayer$onBlockEntityAdded(BlockEntity blockEntity, CallbackInfo ci) {
        if (blockEntity instanceof RadioPlayerBlockEntity) {
            RadioClientAudioManager.registerRadio(blockEntity.getBlockPos());
        } else if (blockEntity instanceof SpeakerBlockEntity) {
            RadioClientAudioManager.registerSpeaker(blockEntity.getBlockPos());
        }
    }
}
