package dev.turtywurty.radioplayer.mixin;

import dev.turtywurty.radioplayer.sound.LavaPlayerAudioStream;
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
        String url = RadioSoundInstance.getUrl(location);
        if (url == null)
            return;

        cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
            try {
                AudioStream stream = LavaPlayerAudioStream.open(url);
                RadioSoundInstance.markReady(location);
                return stream;
            } catch (IOException exception) {
                throw new CompletionException("Failed to open audio stream for URL: " + url, exception);
            }
        }, Util.nonCriticalIoPool()));
    }
}
