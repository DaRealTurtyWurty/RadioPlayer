package dev.turtywurty.mediabox.item;

import dev.turtywurty.mediabox.cable.CableItemData;
import dev.turtywurty.mediabox.cable.CableConstants;
import dev.turtywurty.mediabox.cable.CableConnectionRules;
import dev.turtywurty.mediabox.cable.CableManager;
import dev.turtywurty.mediabox.cable.CableSavedData;
import dev.turtywurty.mediabox.cable.CableSync;
import dev.turtywurty.mediabox.cable.MediaPortLookup;
import dev.turtywurty.mediabox.cable.MediaSignalType;
import dev.turtywurty.mediabox.cable.PortDirection;
import dev.turtywurty.mediabox.cable.PortEndpoint;
import dev.turtywurty.mediabox.cable.ResolvedMediaPort;
import dev.turtywurty.mediabox.cable.VisibleCableConnection;
import dev.turtywurty.mediabox.cable.VisibleCableCollision;
import dev.turtywurty.mediabox.cable.VisibleCableRouteFactory;
import dev.turtywurty.mediabox.cable.concealed.ConcealedCableInstaller;
import dev.turtywurty.mediabox.cable.concealed.ConcealedCablePortProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class AudioCableItem extends Item {
    public AudioCableItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NonNull InteractionResult useOn(UseOnContext context) {
        Optional<ResolvedMediaPort> clickedPort = MediaPortLookup.resolve(
                context.getLevel(),
                context.getClickedPos(),
                context.getClickedFace(),
                context.getClickLocation());
        if (clickedPort.isEmpty())
            return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        if (context.isSecondaryUseActive()) {
            if (!context.getLevel().isClientSide()) {
                CableItemData.clearPendingEndpoint(stack);
                notify(player, "Cable selection cleared");
            }

            return context.getLevel().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }

        if (!(context.getLevel() instanceof ServerLevel serverLevel))
            return InteractionResult.SUCCESS;

        ResolvedMediaPort clicked = clickedPort.get();
        if (!clicked.port().supports(MediaSignalType.AUDIO)) {
            notify(player, "That port does not support audio");
            return InteractionResult.FAIL;
        }

        CableSavedData savedData = CableSavedData.get(serverLevel);
        CableManager manager = savedData.manager();
        Optional<PortEndpoint> pendingEndpoint = CableItemData.getPendingEndpoint(stack);
        if (pendingEndpoint.isEmpty()) {
            CableItemData.setPendingEndpoint(stack, clicked.endpoint());
            notify(player, "First audio cable endpoint selected");
            return InteractionResult.SUCCESS_SERVER;
        }

        PortEndpoint firstEndpoint = pendingEndpoint.get();
        if (firstEndpoint.equals(clicked.endpoint())) {
            CableItemData.clearPendingEndpoint(stack);
            notify(player, "Cable selection cleared");
            return InteractionResult.SUCCESS_SERVER;
        }

        Optional<ResolvedMediaPort> firstPort = MediaPortLookup.resolve(serverLevel, firstEndpoint);
        if (firstPort.isEmpty()) {
            CableItemData.clearPendingEndpoint(stack);
            notify(player, "The first cable endpoint is no longer available");
            return InteractionResult.FAIL;
        }

        try {
            CableConnectionRules.validateDirections(firstPort.get().port(), clicked.port());
            CableConnectionRules.validateCapacity(
                    firstPort.get().port(),
                    manager.connectionCount(firstPort.get().endpoint()));
            CableConnectionRules.validateCapacity(
                    clicked.port(),
                    manager.connectionCount(clicked.endpoint()));
            boolean concealed = isConcealedTerminal(serverLevel, firstPort.get())
                    && isConcealedTerminal(serverLevel, clicked);
            boolean consumesItems = player == null || !player.isCreative();
            int requiredItems;

            if (concealed) {
                requiredItems = CableConstants.itemsForLength(concealedMinimumLength(firstPort.get(), clicked));
                requireCableItems(stack, requiredItems, consumesItems);
                validateProspectiveNetwork(serverLevel, manager, firstPort.get(), clicked);
                ConcealedCableInstaller.install(
                        serverLevel,
                        firstEndpoint,
                        clicked.endpoint(),
                        MediaSignalType.AUDIO,
                        requiredItems);
            } else {
                validateVisibleConnection(serverLevel, manager, firstPort.get(), clicked);
                VisibleCableRouteFactory.PurchasedRoute candidate =
                        VisibleCableRouteFactory.create(firstPort.get(), clicked);
                requiredItems = candidate.cableItems();
                requireCableItems(stack, requiredItems, consumesItems);
                if (!VisibleCableCollision.isClear(
                        serverLevel,
                        candidate.route(),
                        firstEndpoint.pos(),
                        clicked.endpoint().pos()))
                    throw new IllegalArgumentException("The cable path is blocked");

                savedData.addVisibleCable(new VisibleCableConnection(
                        UUID.randomUUID(),
                        firstEndpoint,
                        clicked.endpoint(),
                        MediaSignalType.AUDIO,
                        requiredItems));
                CableSync.broadcastSnapshot(serverLevel);
            }

            CableItemData.clearPendingEndpoint(stack);
            if (consumesItems)
                stack.shrink(requiredItems);

            notify(player, (concealed ? "Concealed audio cable installed using " : "Audio cable connected using ")
                    + requiredItems + (requiredItems == 1 ? " cable" : " cables"));
            return InteractionResult.SUCCESS_SERVER;
        } catch (IllegalArgumentException exception) {
            notify(player, exception.getMessage());
            return InteractionResult.FAIL;
        }
    }

    private static void requireCableItems(ItemStack stack, int requiredItems, boolean consumesItems) {
        if (consumesItems && stack.getCount() < requiredItems)
            throw new IllegalArgumentException("This connection requires " + requiredItems + " cable items");
    }

    private static void validateVisibleConnection(
            ServerLevel level,
            CableManager manager,
            ResolvedMediaPort first,
            ResolvedMediaPort second) {
        if (!first.endpoint().dimension().equals(second.endpoint().dimension()))
            throw new IllegalArgumentException("Cable endpoints must be in the same dimension");

        if (!first.port().supports(MediaSignalType.AUDIO) || !second.port().supports(MediaSignalType.AUDIO))
            throw new IllegalArgumentException("Both ports must support audio");

        CableConnectionRules.validateDirections(first.port(), second.port());

        validateProspectiveNetwork(level, manager, first, second);
    }

    private static void validateProspectiveNetwork(
            ServerLevel level,
            CableManager manager,
            ResolvedMediaPort first,
            ResolvedMediaPort second) {
        if (!first.port().supports(MediaSignalType.AUDIO) || !second.port().supports(MediaSignalType.AUDIO))
            throw new IllegalArgumentException("Both ports must support audio");

        Set<PortEndpoint> prospectiveNetwork = new HashSet<>();
        prospectiveNetwork.add(first.endpoint());
        prospectiveNetwork.add(second.endpoint());
        manager.networkAt(first.endpoint()).ifPresent(network -> prospectiveNetwork.addAll(network.ports()));
        manager.networkAt(second.endpoint()).ifPresent(network -> prospectiveNetwork.addAll(network.ports()));

        long outputCount = prospectiveNetwork.stream()
                .map(endpoint -> MediaPortLookup.resolve(level, endpoint))
                .flatMap(Optional::stream)
                .filter(port -> port.port().supports(MediaSignalType.AUDIO))
                .filter(port -> port.port().direction() == PortDirection.OUTPUT)
                .count();
        if (outputCount > 1)
            throw new IllegalArgumentException("An audio network can only contain one output");
    }

    private static boolean isConcealedTerminal(ServerLevel level, ResolvedMediaPort port) {
        BlockEntity blockEntity = level.getBlockEntity(port.endpoint().pos());
        return blockEntity instanceof ConcealedCablePortProvider;
    }

    private static int concealedMinimumLength(ResolvedMediaPort first, ResolvedMediaPort second) {
        var firstInsideWall = first.endpoint().pos().relative(first.port().face().getOpposite());
        var secondInsideWall = second.endpoint().pos().relative(second.port().face().getOpposite());
        return Math.abs(firstInsideWall.getX() - secondInsideWall.getX())
                + Math.abs(firstInsideWall.getY() - secondInsideWall.getY())
                + Math.abs(firstInsideWall.getZ() - secondInsideWall.getZ());
    }

    private static void notify(Player player, String message) {
        if (player != null && message != null && !message.isBlank())
            player.sendOverlayMessage(Component.literal(message));
    }
}
