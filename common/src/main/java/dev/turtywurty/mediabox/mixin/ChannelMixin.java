package dev.turtywurty.mediabox.mixin;

import com.mojang.blaze3d.audio.Channel;
import dev.turtywurty.mediabox.sound.SpatialMixerAudioStream;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Channel.class)
public class ChannelMixin {
    @Shadow
    private AudioStream stream;

    @Shadow
    @Final
    private int source;

    @Inject(method = "play", at = @At("TAIL"))
    private void mediabox$onPlaybackStarted(CallbackInfo ci) {
        if (this.stream instanceof SpatialMixerAudioStream mediaStream) {
            mediaStream.onPlaybackStarted();
            mediaStream.onPlaybackCursor(AL10.alGetSourcei(this.source, AL11.AL_SAMPLE_OFFSET));
        }
    }

    @Inject(method = "removeProcessedBuffers", at = @At("RETURN"))
    private void mediabox$onBuffersProcessed(CallbackInfoReturnable<Integer> cir) {
        if (this.stream instanceof SpatialMixerAudioStream mediaStream) {
            mediaStream.onBuffersProcessed(cir.getReturnValue());
        }
    }

    @Inject(method = "updateStream", at = @At("TAIL"))
    private void mediabox$updatePlaybackCursor(CallbackInfo ci) {
        if (this.stream instanceof SpatialMixerAudioStream mediaStream) {
            mediaStream.onPlaybackCursor(AL10.alGetSourcei(this.source, AL11.AL_SAMPLE_OFFSET));
        }
    }
}
