package dev.turtywurty.mediabox.block.entity;

import dev.turtywurty.mediabox.block.ModBlockEntities;
import dev.turtywurty.mediabox.screen.ScreenAssembly;
import dev.turtywurty.mediabox.screen.ScreenAssemblyManager;
import dev.turtywurty.mediabox.video.VideoSessionState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public class FlatScreenBlockEntity extends BlockEntity {
    private @Nullable UUID screenId;
    private @Nullable BlockPos controllerPos;

    private @Nullable ScreenState screenState; // only present on the controller panel, null on all other panels.
    private boolean checkedAssemblyAfterLoad;

    public FlatScreenBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(ModBlockEntities.flatScreen.value(), worldPosition, blockState);
    }

    public @Nullable UUID getScreenId() {
        return this.screenId;
    }

    public @Nullable BlockPos getControllerPos() {
        return this.controllerPos;
    }

    public @Nullable ScreenState getScreenState() {
        return this.screenState;
    }

    public boolean isController() {
        return Objects.equals(this.worldPosition, this.controllerPos);
    }

    public void setAssembly(ScreenAssembly assembly, @Nullable VideoSessionState input) {
        UUID newScreenId = assembly.id();
        BlockPos newControllerPos = assembly.controllerPos().immutable();
        ScreenState newScreenState = this.worldPosition.equals(newControllerPos)
                ? ScreenState.from(assembly, input)
                : null;
        if (Objects.equals(this.screenId, newScreenId)
                && Objects.equals(this.controllerPos, newControllerPos)
                && Objects.equals(this.screenState, newScreenState))
            return;

        this.screenId = newScreenId;
        this.controllerPos = newControllerPos;
        this.screenState = newScreenState;
        update();
    }

    public boolean setInput(@Nullable VideoSessionState input) {
        if (this.screenState == null)
            return false;

        ScreenState newState = this.screenState.withInput(input);
        if (Objects.equals(this.screenState, newState))
            return true;

        this.screenState = newState;
        update();
        return true;
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        if (this.screenId != null) {
            output.putString("screen_id", this.screenId.toString());
        }

        if (this.controllerPos != null) {
            output.store("controller_pos", BlockPos.CODEC, this.controllerPos);
        }

        if (this.screenState != null) {
            output.putBoolean("has_screen_state", true);
            output.putString("screen_facing", this.screenState.facing().getSerializedName());
            output.store("screen_origin", BlockPos.CODEC, this.screenState.origin());
            output.putInt("screen_width", this.screenState.width());
            output.putInt("screen_height", this.screenState.height());
            output.putInt("screen_panel_count", this.screenState.panelCount());
        }
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        this.screenId = parseUuid(input.getStringOr("screen_id", ""));
        this.controllerPos = input.read("controller_pos", BlockPos.CODEC)
                .map(BlockPos::immutable)
                .orElse(null);
        this.screenState = null;
        this.checkedAssemblyAfterLoad = false;

        if (!input.getBooleanOr("has_screen_state", false) || this.screenId == null || this.controllerPos == null)
            return;

        Direction facing = Direction.byName(input.getStringOr("screen_facing", ""));
        BlockPos origin = input.read("screen_origin", BlockPos.CODEC)
                .map(BlockPos::immutable)
                .orElse(null);
        int width = input.getIntOr("screen_width", 0);
        int height = input.getIntOr("screen_height", 0);
        int panelCount = input.getIntOr("screen_panel_count", 0);
        if (facing == null || !facing.getAxis().isHorizontal() || origin == null
                || width < 1 || height < 1 || panelCount < 1
                || panelCount > (long) width * height)
            return;

        boolean rectangular = panelCount == (long) width * height;
        this.screenState = new ScreenState(
                this.screenId,
                facing,
                origin,
                this.controllerPos,
                width,
                height,
                panelCount,
                rectangular,
                null
        );
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveCustomOnly(registries);
    }

    public static void serverTick(
            Level level,
            BlockPos pos,
            FlatScreenBlockEntity screen) {
        if (screen.checkedAssemblyAfterLoad)
            return;

        screen.checkedAssemblyAfterLoad = true;
        if (level instanceof ServerLevel serverLevel
                && (screen.screenId == null
                || screen.controllerPos == null
                || (screen.isController() && screen.screenState == null))) {
            ScreenAssemblyManager.rebuildFrom(serverLevel, pos);
        }
    }

    private void update() {
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    private static @Nullable UUID parseUuid(String value) {
        if (value.isBlank())
            return null;

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record ScreenState(
            UUID id,
            Direction facing,
            BlockPos origin,
            BlockPos controllerPos,
            int width,
            int height,
            int panelCount,
            boolean rectangular,
            @Nullable VideoSessionState input
    ) {
        public ScreenState {
            var assembly = new ScreenAssembly(
                    id, facing, origin, controllerPos, width, height, panelCount, rectangular
            );
            origin = assembly.origin();
            controllerPos = assembly.controllerPos();
        }

        public static ScreenState from(ScreenAssembly assembly, @Nullable VideoSessionState input) {
            return new ScreenState(
                    assembly.id(),
                    assembly.facing(),
                    assembly.origin(),
                    assembly.controllerPos(),
                    assembly.width(),
                    assembly.height(),
                    assembly.panelCount(),
                    assembly.rectangular(),
                    input
            );
        }

        public ScreenAssembly assembly() {
            return new ScreenAssembly(
                    this.id,
                    this.facing,
                    this.origin,
                    this.controllerPos,
                    this.width,
                    this.height,
                    this.panelCount,
                    this.rectangular
            );
        }

        public ScreenState withInput(@Nullable VideoSessionState input) {
            return new ScreenState(
                    this.id,
                    this.facing,
                    this.origin,
                    this.controllerPos,
                    this.width,
                    this.height,
                    this.panelCount,
                    this.rectangular,
                    input
            );
        }
    }
}
