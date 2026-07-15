package dev.turtywurty.mediaplayer.mixin;

import dev.turtywurty.mediaplayer.sound.AudioStreamDecoders;
import dev.turtywurty.mediaplayer.sound.ClientAudioSource;
import dev.turtywurty.mediaplayer.sound.MediaSoundInstance;
import dev.turtywurty.mediaplayer.sound.SpatialMixerAudioStream;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin {
    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void mediaplayer$getStream(Identifier location, boolean looping,
                                       CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        ClientAudioSource source = ClientAudioSource.getBySoundPath(location);
        if (source == null)
            return;

        cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
            try {
                AudioStream decodedStream = AudioStreamDecoders.open(source.getMediaLocation());
                MediaSoundInstance.markReady(location);
                return new SpatialMixerAudioStream(source, decodedStream);
            } catch (IOException exception) {
                throw new CompletionException("Failed to open media: " + source.getMediaLocation(), exception);
            }
        }, Util.nonCriticalIoPool()));
    }
}
