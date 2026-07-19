package dev.turtywurty.mediabox.client.cable;

import dev.turtywurty.mediabox.cable.CableItemData;
import dev.turtywurty.mediabox.cable.MediaPortLookup;
import dev.turtywurty.mediabox.cable.PortEndpoint;
import dev.turtywurty.mediabox.cable.ResolvedMediaPort;
import dev.turtywurty.mediabox.cable.VisibleCableCollision;
import dev.turtywurty.mediabox.cable.VisibleCableRoute;
import dev.turtywurty.mediabox.cable.VisibleCableRouteFactory;
import dev.turtywurty.mediabox.cable.concealed.ConcealedCablePortProvider;
import dev.turtywurty.mediabox.item.AudioCableItem;
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
                creative);
        if (key.equals(cachedKey) && cachedPreview != null)
            return Optional.of(cachedPreview);

        cachedKey = key;
        cachedPreview = calculate(level, first.get(), second.get(), stack.getCount(), creative);
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
            boolean creative) {
        VisibleCableRouteFactory.PurchasedRoute route = VisibleCableRouteFactory.create(first, second);
        boolean affordable = creative || availableItems >= route.cableItems();
        boolean clear = VisibleCableCollision.isClear(
                level,
                route.route(),
                first.endpoint().pos(),
                second.endpoint().pos());
        return new Preview(route.route(), affordable && clear, route.cableItems(), !clear);
    }

    private static ItemStack heldCableStack(Minecraft minecraft) {
        ItemStack mainHand = minecraft.player.getMainHandItem();
        if (mainHand.getItem() instanceof AudioCableItem)
            return mainHand;
        ItemStack offHand = minecraft.player.getOffhandItem();
        return offHand.getItem() instanceof AudioCableItem ? offHand : ItemStack.EMPTY;
    }

    private static boolean isConcealedTerminal(ClientLevel level, ResolvedMediaPort port) {
        return level.getBlockEntity(port.endpoint().pos()) instanceof ConcealedCablePortProvider;
    }

    private static void showStatus(Minecraft minecraft, Preview preview) {
        if (preview.blocked()) {
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

    public record Preview(VisibleCableRoute route, boolean valid, int requiredItems, boolean blocked) {
    }

    private record PreviewKey(
            PortEndpoint first,
            PortEndpoint second,
            int availableItems,
            boolean creative) {
    }
}
