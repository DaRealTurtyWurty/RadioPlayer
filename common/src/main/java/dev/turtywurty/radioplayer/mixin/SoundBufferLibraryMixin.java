package dev.turtywurty.radioplayer.mixin;

import dev.turtywurty.radioplayer.sound.LavaPlayerAudioStream;
import dev.turtywurty.radioplayer.sound.RadioAudioSource;
import dev.turtywurty.radioplayer.sound.RadioMixerAudioStream;
import dev.turtywurty.radioplayer.sound.RadioSoundInstance;
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
    private void radioplayer$getStream(Identifier location, boolean looping,
                                       CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        RadioAudioSource source = RadioAudioSource.getBySoundPath(location);
        if (source == null)
            return;

        cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
            try {
                AudioStream decodedStream = LavaPlayerAudioStream.open(source.getUrl());
                RadioSoundInstance.markReady(location);
                return new RadioMixerAudioStream(source, decodedStream);
            } catch (IOException exception) {
                throw new CompletionException("Failed to open audio stream for URL: " + source.getUrl(), exception);
            }
        }, Util.nonCriticalIoPool()));
    }
}
