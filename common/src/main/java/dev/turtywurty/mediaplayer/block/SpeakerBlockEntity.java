package dev.turtywurty.mediaplayer.block;

import dev.turtywurty.mediaplayer.block.entity.RadioPlayerBlockEntity;
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
    private static final int RADIO_SEARCH_RADIUS = 8;
    private static final String LINKED_RADIO_POS_TAG = "linked_radio_pos";

    private @Nullable BlockPos linkedRadioPos;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.speaker.value(), pos, state);
    }

    protected SpeakerBlockEntity(BlockEntityType<? extends SpeakerBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private static boolean isUsableSource(RadioPlayerBlockEntity radio) {
        return radio.isPlaying() && !radio.getUrl().isBlank();
    }

    public @Nullable BlockPos getLinkedRadioPos() {
        return this.linkedRadioPos;
    }

    public void setLinkedRadioPos(@Nullable BlockPos linkedRadioPos) {
        BlockPos immutablePos = linkedRadioPos == null ? null : linkedRadioPos.immutable();
        if (Objects.equals(immutablePos, this.linkedRadioPos))
            return;

        this.linkedRadioPos = immutablePos;
        update();
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);

        if (this.linkedRadioPos != null) {
            output.store(LINKED_RADIO_POS_TAG, BlockPos.CODEC, this.linkedRadioPos);
        }
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        this.linkedRadioPos = input.read(LINKED_RADIO_POS_TAG, BlockPos.CODEC)
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

    public @Nullable RadioPlayerBlockEntity findSourceRadio() {
        Level level = getLevel();
        if (level == null)
            return null;

        if (this.linkedRadioPos != null) {
            BlockEntity blockEntity = level.getBlockEntity(this.linkedRadioPos);
            if (!(blockEntity instanceof RadioPlayerBlockEntity radio)) {
                setLinkedRadioPos(null);
                return null;
            }

            return isUsableSource(radio) ? radio : null;
        }

        RadioPlayerBlockEntity closestRadio = null;
        double closestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                this.worldPosition.offset(-RADIO_SEARCH_RADIUS, -RADIO_SEARCH_RADIUS, -RADIO_SEARCH_RADIUS),
                this.worldPosition.offset(RADIO_SEARCH_RADIUS, RADIO_SEARCH_RADIUS, RADIO_SEARCH_RADIUS))) {
            if (!(level.getBlockEntity(pos) instanceof RadioPlayerBlockEntity radio) || !isUsableSource(radio))
                continue;

            double distance = pos.distSqr(this.worldPosition);
            if (distance < closestDistance) {
                closestRadio = radio;
                closestDistance = distance;
            }
        }

        if (closestRadio != null) {
            setLinkedRadioPos(closestRadio.getBlockPos());
        }

        return closestRadio;
    }

    private void update() {
        setChanged();

        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }
}
