package dev.turtywurty.mediabox.item;

import dev.turtywurty.mediabox.cable.CableItemData;
import dev.turtywurty.mediabox.cable.CableConstants;
import dev.turtywurty.mediabox.cable.CableManager;
import dev.turtywurty.mediabox.cable.CableSavedData;
import dev.turtywurty.mediabox.cable.CableSync;
import dev.turtywurty.mediabox.cable.MediaPort;
import dev.turtywurty.mediabox.cable.MediaPortLookup;
import dev.turtywurty.mediabox.cable.MediaSignalType;
import dev.turtywurty.mediabox.cable.PortDirection;
import dev.turtywurty.mediabox.cable.PortEndpoint;
import dev.turtywurty.mediabox.cable.ResolvedMediaPort;
import dev.turtywurty.mediabox.cable.VisibleCableConnection;
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
    private static final float DEFAULT_SLACK = 0.25F;

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
            boolean concealed = isConcealedTerminal(serverLevel, firstPort.get())
                    && isConcealedTerminal(serverLevel, clicked);
            if (concealed) {
                validateProspectiveNetwork(serverLevel, manager, firstPort.get(), clicked);
                ConcealedCableInstaller.install(
                        serverLevel,
                        firstEndpoint,
                        clicked.endpoint(),
                        MediaSignalType.AUDIO,
                        CableConstants.MAX_CABLE_LENGTH);
            } else {
                validateVisibleConnection(serverLevel, manager, firstPort.get(), clicked);
                savedData.addVisibleCable(new VisibleCableConnection(
                        UUID.randomUUID(),
                        firstEndpoint,
                        clicked.endpoint(),
                        MediaSignalType.AUDIO,
                        DEFAULT_SLACK));
                CableSync.broadcastSnapshot(serverLevel);
            }

            CableItemData.clearPendingEndpoint(stack);
            if (player == null || !player.isCreative())
                stack.shrink(1);

            notify(player, concealed ? "Concealed audio cable installed" : "Audio cable connected");
            return InteractionResult.SUCCESS_SERVER;
        } catch (IllegalArgumentException exception) {
            notify(player, exception.getMessage());
            return InteractionResult.FAIL;
        }
    }

    private static void validateVisibleConnection(
            ServerLevel level,
            CableManager manager,
            ResolvedMediaPort first,
            ResolvedMediaPort second) {
        if (!first.endpoint().dimension().equals(second.endpoint().dimension()))
            throw new IllegalArgumentException("Cable endpoints must be in the same dimension");

        if (first.endpoint().pos().distSqr(second.endpoint().pos())
                > CableConstants.MAX_CABLE_LENGTH * CableConstants.MAX_CABLE_LENGTH)
            throw new IllegalArgumentException(
                    "The visible cable is too long (maximum " + CableConstants.MAX_CABLE_LENGTH + " blocks)");

        if (!first.port().supports(MediaSignalType.AUDIO) || !second.port().supports(MediaSignalType.AUDIO))
            throw new IllegalArgumentException("Both ports must support audio");

        if (!directionsAreCompatible(first.port(), second.port()))
            throw new IllegalArgumentException("Those port directions are incompatible");

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

    private static boolean directionsAreCompatible(MediaPort first, MediaPort second) {
        PortDirection firstDirection = first.direction();
        PortDirection secondDirection = second.direction();
        return firstDirection == PortDirection.BIDIRECTIONAL
                || secondDirection == PortDirection.BIDIRECTIONAL
                || firstDirection != secondDirection;
    }

    private static void notify(Player player, String message) {
        if (player != null && message != null && !message.isBlank())
            player.sendOverlayMessage(Component.literal(message));
    }
}
