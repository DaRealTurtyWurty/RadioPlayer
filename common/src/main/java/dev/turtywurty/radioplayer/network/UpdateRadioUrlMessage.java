package dev.turtywurty.radioplayer.network;

import dev.turtywurty.radioplayer.Radioplayer;
import dev.turtywurty.radioplayer.SavedRadioStation;
import dev.turtywurty.radioplayer.block.entity.RadioPlayerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public record UpdateRadioUrlMessage(BlockPos pos, String url, boolean playing, List<SavedRadioStation> savedStations) implements CustomPacketPayload {
    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_NICKNAME_LENGTH = 64;
    private static final int MAX_SAVED_STATIONS = 8;

    public static final CustomPacketPayload.Type<UpdateRadioUrlMessage> TYPE =
            new CustomPacketPayload.Type<>(Radioplayer.id("update_radio_url"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateRadioUrlMessage> CODEC = new StreamCodec<>() {
        @Override
        public UpdateRadioUrlMessage decode(RegistryFriendlyByteBuf input) {
            return new UpdateRadioUrlMessage(
                    BlockPos.STREAM_CODEC.decode(input),
                    ByteBufCodecs.stringUtf8(MAX_URL_LENGTH).decode(input),
                    ByteBufCodecs.BOOL.decode(input),
                    decodeSavedStations(input));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf output, UpdateRadioUrlMessage value) {
            BlockPos.STREAM_CODEC.encode(output, value.pos);
            ByteBufCodecs.stringUtf8(MAX_URL_LENGTH).encode(output, value.url);
            ByteBufCodecs.BOOL.encode(output, value.playing);
            encodeSavedStations(output, value.savedStations);
        }
    };

    public UpdateRadioUrlMessage {
        url = url == null ? "" : url;
        savedStations = sanitizeSavedStations(savedStations);
    }

    public static void handle(ServerPlayer player, UpdateRadioUrlMessage message) {
        if (!player.blockPosition().closerThan(message.pos, 8.0)) {
            return;
        }

        BlockEntity blockEntity = player.level().getBlockEntity(message.pos);
        if (blockEntity instanceof RadioPlayerBlockEntity radioPlayer) {
            radioPlayer.setSettings(message.url.trim(), message.playing, message.savedStations);
        }
    }

    private static List<SavedRadioStation> decodeSavedStations(RegistryFriendlyByteBuf input) {
        int count = ByteBufCodecs.VAR_INT.decode(input);
        if (count < 0 || count > MAX_SAVED_STATIONS) {
            throw new IllegalArgumentException("Invalid saved station count: " + count);
        }

        List<SavedRadioStation> stations = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            stations.add(SavedRadioStation.of(
                    ByteBufCodecs.stringUtf8(MAX_NICKNAME_LENGTH).decode(input),
                    ByteBufCodecs.stringUtf8(MAX_URL_LENGTH).decode(input)));
        }

        return stations;
    }

    private static void encodeSavedStations(RegistryFriendlyByteBuf output, List<SavedRadioStation> stations) {
        List<SavedRadioStation> sanitizedStations = sanitizeSavedStations(stations);
        ByteBufCodecs.VAR_INT.encode(output, sanitizedStations.size());
        for (SavedRadioStation station : sanitizedStations) {
            ByteBufCodecs.stringUtf8(MAX_NICKNAME_LENGTH).encode(output, station.nickname());
            ByteBufCodecs.stringUtf8(MAX_URL_LENGTH).encode(output, station.url());
        }
    }

    private static List<SavedRadioStation> sanitizeSavedStations(List<SavedRadioStation> stations) {
        List<SavedRadioStation> sanitizedStations = new ArrayList<>();
        if (stations == null) {
            return sanitizedStations;
        }

        for (SavedRadioStation station : stations) {
            if (station == null) {
                continue;
            }

            SavedRadioStation sanitizedStation = SavedRadioStation.of(truncate(station.nickname(), MAX_NICKNAME_LENGTH), station.url());
            if (!sanitizedStation.url().isBlank() && sanitizedStations.stream().noneMatch(savedStation -> savedStation.url().equals(sanitizedStation.url()))) {
                sanitizedStations.add(sanitizedStation);
            }

            if (sanitizedStations.size() >= MAX_SAVED_STATIONS) {
                break;
            }
        }

        return List.copyOf(sanitizedStations);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
