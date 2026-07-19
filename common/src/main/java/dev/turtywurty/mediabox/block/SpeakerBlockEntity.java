package dev.turtywurty.mediabox.block;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.cable.MediaPort;
import dev.turtywurty.mediabox.cable.MediaPortGeometry;
import dev.turtywurty.mediabox.cable.MediaPortProvider;
import dev.turtywurty.mediabox.cable.MediaSignalType;
import dev.turtywurty.mediabox.cable.PortDirection;
import dev.turtywurty.mediabox.cable.CableRouting;
import dev.turtywurty.mediabox.sound.AudioSourceProvider;
import dev.turtywurty.mediabox.sound.SpeakerType;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.List;
import java.util.Set;

public class SpeakerBlockEntity extends BlockEntity implements MediaPortProvider {
    public static final Identifier AUDIO_INPUT_PORT_ID = MediaBox.id("speaker_audio_input");

    private @Nullable BlockPos linkedSourcePos;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.speaker.value(), pos, state);
    }

    protected SpeakerBlockEntity(BlockEntityType<? extends SpeakerBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private static boolean isUsableSource(AudioSourceProvider source) {
        return source.getAudioPlaybackState().isPlayable();
    }

    public @Nullable BlockPos getLinkedSourcePos() {
        return this.linkedSourcePos;
    }

    public void setLinkedSourcePos(@Nullable BlockPos linkedSourcePos) {
        BlockPos immutablePos = linkedSourcePos == null ? null : linkedSourcePos.immutable();
        if (Objects.equals(immutablePos, this.linkedSourcePos))
            return;

        this.linkedSourcePos = immutablePos;
        update();
    }

    @Override
    public List<MediaPort> getMediaPorts() {
        Direction modelFacing = getBlockState().getValue(SpeakerBlock.FACING).nearestCardinal();
        SpeakerType speakerType = ((SpeakerBlock) getBlockState().getBlock()).getSpeakerType();
        return List.of(new MediaPort(
                AUDIO_INPUT_PORT_ID,
                modelFacing.getOpposite(),
                MediaPortGeometry.rotateFromNorth(speakerAttachmentPoint(speakerType), modelFacing),
                PortDirection.INPUT,
                1,
                Set.of(MediaSignalType.AUDIO)));
    }

    private static Vec3 speakerAttachmentPoint(SpeakerType speakerType) {
        return switch (speakerType) {
            case BOOKSHELF -> MediaPortGeometry.modelPoint(9.5, 6.5, 12.0);
            case FLOOR_STANDING -> MediaPortGeometry.modelPoint(9.5, 5.5, 12.0);
            case SUBWOOFER, BASS_REFLEX, HORN -> MediaPortGeometry.modelPoint(10.5, 5.5, 16.0);
            case FULL_RANGE, WOOFER, MID_RANGE, TWEETER -> MediaPortGeometry.modelPoint(10.5, 5.5, 14.0);
        };
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);

        if (this.linkedSourcePos != null) {
            output.store("LinkedSourcePos", BlockPos.CODEC, this.linkedSourcePos);
        }
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        this.linkedSourcePos = input.read("LinkedSourcePos", BlockPos.CODEC)
                .map(BlockPos::immutable)
                .orElse(null);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveCustomOnly(registries);
    }

    public @Nullable AudioSourceProvider findAudioSource() {
        Level level = getLevel();
        if (level == null || this.linkedSourcePos == null)
            return null;

        BlockEntity blockEntity = level.getBlockEntity(this.linkedSourcePos);
        return blockEntity instanceof AudioSourceProvider source && isUsableSource(source) ? source : null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpeakerBlockEntity speaker) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)
                || (level.getGameTime() + pos.asLong()) % 20L != 0L)
            return;

        CableRouting.updateSpeaker(serverLevel, speaker);
    }

    private void update() {
        setChanged();

        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }
}
