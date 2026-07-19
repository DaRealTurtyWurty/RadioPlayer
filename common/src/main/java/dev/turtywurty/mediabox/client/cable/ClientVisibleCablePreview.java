package dev.turtywurty.mediabox.client.cable;

import dev.turtywurty.mediabox.cable.CableItemData;
import dev.turtywurty.mediabox.cable.CableConnectionRules;
import dev.turtywurty.mediabox.cable.MediaPortLookup;
import dev.turtywurty.mediabox.cable.MediaSignalType;
import dev.turtywurty.mediabox.cable.PortEndpoint;
import dev.turtywurty.mediabox.cable.ResolvedMediaPort;
import dev.turtywurty.mediabox.cable.VisibleCableCollision;
import dev.turtywurty.mediabox.cable.VisibleCableRoute;
import dev.turtywurty.mediabox.cable.VisibleCableRouteFactory;
import dev.turtywurty.mediabox.cable.concealed.ConcealedCablePortProvider;
import dev.turtywurty.mediabox.item.CableItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

public final class ClientVisibleCablePreview {
    private static PreviewKey cachedKey;
    private static Preview cachedPreview;

    private ClientVisibleCablePreview() {
    }

    public static Optional<Preview> get(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || !(minecraft.hitResult instanceof BlockHitResult hit))
            return Optional.empty();

        ItemStack stack = heldCableStack(minecraft);
        if (!(stack.getItem() instanceof CableItem cableItem))
            return Optional.empty();

        Optional<PortEndpoint> firstEndpoint = CableItemData.getPendingEndpoint(stack);
        if (firstEndpoint.isEmpty())
            return Optional.empty();

        Optional<ResolvedMediaPort> first = MediaPortLookup.resolve(level, firstEndpoint.get());
        Optional<ResolvedMediaPort> second = MediaPortLookup.resolve(
                level,
                hit.getBlockPos(),
                hit.getDirection(),
                hit.getLocation());
        if (first.isEmpty() || second.isEmpty() || first.get().endpoint().equals(second.get().endpoint()))
            return Optional.empty();
        if (isConcealedTerminal(level, first.get()) && isConcealedTerminal(level, second.get()))
            return Optional.empty();

        boolean creative = minecraft.player.isCreative();
        PreviewKey key = new PreviewKey(
                first.get().endpoint(),
                second.get().endpoint(),
                stack.getCount(),
                cableItem.signalType(),
                creative);
        if (key.equals(cachedKey) && cachedPreview != null)
            return Optional.of(cachedPreview);

        cachedKey = key;
        cachedPreview = calculate(
                level,
                first.get(),
                second.get(),
                stack.getCount(),
                cableItem.signalType(),
                creative);
        showStatus(minecraft, cachedPreview);
        return Optional.of(cachedPreview);
    }

    public static void invalidate() {
        cachedKey = null;
        cachedPreview = null;
    }

    private static Preview calculate(
            ClientLevel level,
            ResolvedMediaPort first,
            ResolvedMediaPort second,
            int availableItems,
            MediaSignalType signalType,
            boolean creative) {
        VisibleCableRouteFactory.PurchasedRoute route = VisibleCableRouteFactory.create(first, second);
        boolean affordable = creative || availableItems >= route.cableItems();
        boolean supported = first.port().supports(signalType) && second.port().supports(signalType);
        boolean compatible = CableConnectionRules.directionsAreCompatible(first.port(), second.port());
        boolean capacityAvailable = CableConnectionRules.hasCapacity(
                first.port(),
                ClientCableState.connectionCount(first.endpoint()))
                && CableConnectionRules.hasCapacity(
                second.port(),
                ClientCableState.connectionCount(second.endpoint()));
        boolean clear = supported && compatible && capacityAvailable && VisibleCableCollision.isClear(
                level,
                route.route(),
                first.endpoint().pos(),
                second.endpoint().pos());
        return new Preview(
                route.route(),
                affordable && supported && compatible && capacityAvailable && clear,
                route.cableItems(),
                supported && compatible && capacityAvailable && !clear,
                !supported,
                !compatible,
                !capacityAvailable);
    }

    private static ItemStack heldCableStack(Minecraft minecraft) {
        ItemStack mainHand = minecraft.player.getMainHandItem();
        if (mainHand.getItem() instanceof CableItem)
            return mainHand;
        ItemStack offHand = minecraft.player.getOffhandItem();
        return offHand.getItem() instanceof CableItem ? offHand : ItemStack.EMPTY;
    }

    private static boolean isConcealedTerminal(ClientLevel level, ResolvedMediaPort port) {
        return level.getBlockEntity(port.endpoint().pos()) instanceof ConcealedCablePortProvider;
    }

    private static void showStatus(Minecraft minecraft, Preview preview) {
        if (preview.unsupported()) {
            minecraft.player.sendOverlayMessage(Component.literal("Those ports do not support this cable type"));
        } else if (preview.incompatible()) {
            minecraft.player.sendOverlayMessage(Component.literal("Those port directions are incompatible"));
        } else if (preview.full()) {
            minecraft.player.sendOverlayMessage(Component.literal("That port has reached its connection limit"));
        } else if (preview.blocked()) {
            minecraft.player.sendOverlayMessage(Component.literal("Cable path is blocked"));
        } else if (preview.valid()) {
            minecraft.player.sendOverlayMessage(Component.literal(
                    "Cable route: " + preview.requiredItems()
                            + (preview.requiredItems() == 1 ? " cable" : " cables")));
        } else {
            minecraft.player.sendOverlayMessage(Component.literal(
                    "Needs " + preview.requiredItems() + " cable items"));
        }
    }

    public record Preview(
            VisibleCableRoute route,
            boolean valid,
            int requiredItems,
            boolean blocked,
            boolean unsupported,
            boolean incompatible,
            boolean full) {
    }

    private record PreviewKey(
            PortEndpoint first,
            PortEndpoint second,
            int availableItems,
            MediaSignalType signalType,
            boolean creative) {
    }
}
