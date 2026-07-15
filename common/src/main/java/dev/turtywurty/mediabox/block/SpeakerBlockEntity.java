package dev.turtywurty.mediabox.block;

import dev.turtywurty.mediabox.sound.AudioSourceProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class SpeakerBlockEntity extends BlockEntity {
    private static final int SOURCE_SEARCH_RADIUS = 8; // temp until we have a wiring system

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
        if (level == null)
            return null;

        if (this.linkedSourcePos != null) {
            BlockEntity blockEntity = level.getBlockEntity(this.linkedSourcePos);
            if (!(blockEntity instanceof AudioSourceProvider source)) {
                setLinkedSourcePos(null);
                return null;
            }

            return isUsableSource(source) ? source : null;
        }

        AudioSourceProvider closestSource = null;
        double closestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                this.worldPosition.offset(-SOURCE_SEARCH_RADIUS, -SOURCE_SEARCH_RADIUS, -SOURCE_SEARCH_RADIUS),
                this.worldPosition.offset(SOURCE_SEARCH_RADIUS, SOURCE_SEARCH_RADIUS, SOURCE_SEARCH_RADIUS))) {
            if (!(level.getBlockEntity(pos) instanceof AudioSourceProvider source) || !isUsableSource(source))
                continue;

            double distance = pos.distSqr(this.worldPosition);
            if (distance < closestDistance) {
                closestSource = source;
                closestDistance = distance;
            }
        }

        if (closestSource != null) {
            setLinkedSourcePos(closestSource.getAudioSourcePos());
        }

        return closestSource;
    }

    private void update() {
        setChanged();

        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }
}
