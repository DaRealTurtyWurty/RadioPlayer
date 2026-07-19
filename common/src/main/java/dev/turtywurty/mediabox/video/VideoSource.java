package dev.turtywurty.mediabox.video;

import net.minecraft.resources.Identifier;

public sealed interface VideoSource {
    record RemoteUrl(String url) implements VideoSource {
    }

    record ServerAsset(String assetId, String hash) implements VideoSource {
    }

    record Builtin(Identifier id) implements VideoSource {
    }

    record LiveStream(String streamId) implements VideoSource {
    }
}
