package dev.turtywurty.mediabox.video;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

public sealed interface VideoSource {
    Codec<VideoSource> CODEC = Codec.STRING.dispatch(
            "type",
            VideoSource::typeName,
            VideoSource::codecForType
    );

    private static String typeName(VideoSource source) {
        return switch (source) {
            case RemoteUrl ignored -> "remote_url";
            case ServerAsset ignored -> "server_asset";
            case Builtin ignored -> "builtin";
            case LiveStream ignored -> "live_stream";
        };
    }

    private static MapCodec<? extends VideoSource> codecForType(
            String type
    ) {
        return switch (type) {
            case "remote_url" -> RemoteUrl.CODEC;
            case "server_asset" -> ServerAsset.CODEC;
            case "builtin" -> Builtin.CODEC;
            case "live_stream" -> LiveStream.CODEC;
            default -> throw new IllegalArgumentException(
                    "Unknown video source type: " + type
            );
        };
    }

    record RemoteUrl(String url) implements VideoSource {
        public static final MapCodec<RemoteUrl> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.STRING
                                .fieldOf("url")
                                .forGetter(RemoteUrl::url)
                ).apply(instance, RemoteUrl::new));
    }

    record ServerAsset(
            String assetId,
            String hash
    ) implements VideoSource {
        public static final MapCodec<ServerAsset> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.STRING
                                .fieldOf("asset_id")
                                .forGetter(ServerAsset::assetId),
                        Codec.STRING
                                .fieldOf("hash")
                                .forGetter(ServerAsset::hash)
                ).apply(instance, ServerAsset::new));
    }

    record Builtin(Identifier id) implements VideoSource {
        public static final MapCodec<Builtin> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Identifier.CODEC
                                .fieldOf("id")
                                .forGetter(Builtin::id)
                ).apply(instance, Builtin::new));
    }

    record LiveStream(String streamId) implements VideoSource {
        public static final MapCodec<LiveStream> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.STRING
                                .fieldOf("stream_id")
                                .forGetter(LiveStream::streamId)
                ).apply(instance, LiveStream::new));
    }
}