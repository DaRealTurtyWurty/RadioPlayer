package dev.turtywurty.mediabox.block.entity;

import com.mojang.serialization.Codec;
import dev.turtywurty.mediabox.SavedRadioStation;
import dev.turtywurty.mediabox.block.ModBlockEntities;
import dev.turtywurty.mediabox.block.RadioPlayerBlock;
import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.cable.MediaPort;
import dev.turtywurty.mediabox.cable.MediaPortProvider;
import dev.turtywurty.mediabox.cable.MediaSignalType;
import dev.turtywurty.mediabox.cable.PortDirection;
import dev.turtywurty.mediabox.sound.AudioEmitter;
import dev.turtywurty.mediabox.sound.AudioPlaybackState;
import dev.turtywurty.mediabox.sound.AudioSourceProvider;
import dev.turtywurty.mediabox.sound.SpeakerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RadioPlayerBlockEntity extends BlockEntity implements AudioSourceProvider, MediaPortProvider {
    public static final int MAX_SAVED_STATIONS = 8;
    public static final Identifier AUDIO_OUTPUT_PORT_ID = MediaBox.id("radio_audio_output");

    private @NonNull String url = "";
    private boolean playing = false;
    private @NonNull List<SavedRadioStation> savedStations = List.of();

    public RadioPlayerBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(ModBlockEntities.radioPlayer.value(), worldPosition, blockState);
    }

    private static List<SavedRadioStation> loadSavedStations(ValueInput input) {
        ValueInput.ValueInputList stationInputs = input.childrenListOrEmpty("saved_stations_v2");
        if (!stationInputs.isEmpty()) {
            List<SavedRadioStation> stations = new ArrayList<>();
            for (ValueInput stationInput : stationInputs) {
                stations.add(SavedRadioStation.of(
                        stationInput.getStringOr("nickname", ""),
                        stationInput.getStringOr("url", "")));
            }

            return sanitizeSavedStations(stations);
        }

        return sanitizeSavedStations(input.listOrEmpty("saved_stations", Codec.STRING).stream()
                .map(url -> SavedRadioStation.of("", url))
                .toList());
    }

    private static List<SavedRadioStation> sanitizeSavedStations(List<SavedRadioStation> stations) {
        List<SavedRadioStation> sanitizedStations = new ArrayList<>();
        if (stations == null)
            return List.of();

        for (SavedRadioStation station : stations) {
            if (station == null)
                continue;

            SavedRadioStation sanitizedStation = SavedRadioStation.of(station.nickname(), station.url());
            if (!sanitizedStation.url().isBlank() && sanitizedStations.stream().noneMatch(savedStation -> savedStation.url().equals(sanitizedStation.url()))) {
                sanitizedStations.add(sanitizedStation);
            }

            if (sanitizedStations.size() >= MAX_SAVED_STATIONS)
                break;
        }

        return List.copyOf(sanitizedStations);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.putString("url", this.url);
        output.putBoolean("playing", this.playing);

        ValueOutput.ValueOutputList stations = output.childrenList("saved_stations_v2");
        for (SavedRadioStation station : this.savedStations) {
            ValueOutput stationOutput = stations.addChild();
            stationOutput.putString("nickname", station.nickname());
            stationOutput.putString("url", station.url());
        }
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        this.url = input.getStringOr("url", "");
        this.playing = input.getBooleanOr("playing", false);
        this.savedStations = loadSavedStations(input);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveCustomOnly(registries);
    }

    public @NonNull String getUrl() {
        return this.url;
    }

    public void setUrl(@NonNull String url) {
        this.url = url;
        update();
    }

    public boolean isPlaying() {
        return this.playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
        update();
    }

    public void setSettings(@NonNull String url, boolean playing) {
        this.url = url;
        this.playing = playing;
        update();
    }

    public void setSettings(@NonNull String url, boolean playing, List<SavedRadioStation> savedStations) {
        this.url = url;
        this.playing = playing;
        this.savedStations = sanitizeSavedStations(savedStations);
        update();
    }

    public @NonNull List<SavedRadioStation> getSavedStations() {
        return this.savedStations;
    }

    public void setSavedStations(List<SavedRadioStation> savedStations) {
        this.savedStations = sanitizeSavedStations(savedStations);
        update();
    }

    @Override
    public BlockPos getAudioSourcePos() {
        return getBlockPos();
    }

    @Override
    public AudioPlaybackState getAudioPlaybackState() {
        return AudioPlaybackState.streaming(this.url, this.playing);
    }

    @Override
    public List<AudioEmitter> getBuiltInAudioEmitters() {
        return List.of(new AudioEmitter(
                getBlockPos(),
                getBlockState().getValue(RadioPlayerBlock.FACING).asDirection8(),
                SpeakerType.FULL_RANGE,
                1.0F));
    }

    @Override
    public List<MediaPort> getMediaPorts() {
        return List.of(new MediaPort(
                AUDIO_OUTPUT_PORT_ID,
                getBlockState().getValue(RadioPlayerBlock.FACING).nearestCardinal().getOpposite(),
                PortDirection.OUTPUT,
                Set.of(MediaSignalType.AUDIO)));
    }

    @Override
    public boolean playsLoadingStatic() {
        return true;
    }

    private void update() {
        setChanged();

        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }
}
