package dev.turtywurty.mediabox.cable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.Optional;

public final class CableItemData {
    private static final String PENDING_ENDPOINT = "mediabox_pending_endpoint";
    private static final String DIMENSION = "dimension";
    private static final String POSITION = "position";
    private static final String PORT_ID = "port_id";

    private CableItemData() {
    }

    public static Optional<PortEndpoint> getPendingEndpoint(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null)
            return Optional.empty();

        CompoundTag pending = customData.copyTag().getCompound(PENDING_ENDPOINT).orElse(null);
        if (pending == null)
            return Optional.empty();

        Identifier dimensionId = Identifier.tryParse(pending.getStringOr(DIMENSION, ""));
        Identifier portId = Identifier.tryParse(pending.getStringOr(PORT_ID, ""));
        Optional<Long> position = pending.getLong(POSITION);
        if (dimensionId == null || portId == null || position.isEmpty())
            return Optional.empty();

        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        return Optional.of(new PortEndpoint(dimension, BlockPos.of(position.get()), portId));
    }

    public static void setPendingEndpoint(ItemStack stack, PortEndpoint endpoint) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag pending = new CompoundTag();
            pending.putString(DIMENSION, endpoint.dimension().identifier().toString());
            pending.putLong(POSITION, endpoint.pos().asLong());
            pending.putString(PORT_ID, endpoint.portId().toString());
            root.put(PENDING_ENDPOINT, pending);
        });
    }

    public static void clearPendingEndpoint(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null)
            return;

        CompoundTag root = customData.copyTag();
        root.remove(PENDING_ENDPOINT);
        if (root.isEmpty())
            stack.remove(DataComponents.CUSTOM_DATA);
        else
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }
}
