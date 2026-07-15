package dev.turtywurty.mediabox.mixin;

import dev.turtywurty.mediabox.block.SpeakerBlockEntity;
import dev.turtywurty.mediabox.sound.AudioSourceProvider;
import dev.turtywurty.mediabox.sound.ClientAudioManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @Inject(method = "onBlockEntityAdded", at = @At("TAIL"))
    private void mediabox$onBlockEntityAdded(BlockEntity blockEntity, CallbackInfo ci) {
        if (blockEntity instanceof AudioSourceProvider) {
            ClientAudioManager.registerAudioSource(blockEntity.getBlockPos());
        } else if (blockEntity instanceof SpeakerBlockEntity) {
            ClientAudioManager.registerSpeaker(blockEntity.getBlockPos());
        }
    }
}
