package dev.turtywurty.mediabox.cable;

import dev.turtywurty.mediabox.block.SpeakerBlockEntity;
import dev.turtywurty.mediabox.sound.AudioSourceProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;

public final class CableRouting {
    private CableRouting() {
    }

    public static void updateSpeaker(ServerLevel level, SpeakerBlockEntity speaker) {
        Optional<ResolvedMediaPort> inputPort = speaker.getMediaPorts().stream()
                .filter(port -> port.direction() == PortDirection.INPUT && port.supports(MediaSignalType.AUDIO))
                .findFirst()
                .map(port -> new ResolvedMediaPort(PortEndpoint.of(level, speaker.getBlockPos(), port), port));
        if (inputPort.isEmpty()) {
            speaker.setLinkedSourcePos(null);
            return;
        }

        CableManager manager = CableSavedData.get(level).manager();
        CableNetwork network = manager.networkAt(inputPort.get().endpoint(), MediaSignalType.AUDIO).orElse(null);
        if (network == null) {
            speaker.setLinkedSourcePos(null);
            return;
        }

        BlockPos sourcePos = null;
        for (PortEndpoint endpoint : network.ports()) {
            Optional<ResolvedMediaPort> resolved = MediaPortLookup.resolve(level, endpoint);
            if (resolved.isEmpty()
                    || resolved.get().port().direction() != PortDirection.OUTPUT
                    || !resolved.get().port().supports(MediaSignalType.AUDIO))
                continue;

            BlockEntity blockEntity = level.getBlockEntity(endpoint.pos());
            if (!(blockEntity instanceof AudioSourceProvider))
                continue;

            if (sourcePos != null && !sourcePos.equals(endpoint.pos())) {
                sourcePos = null;
                break;
            }

            sourcePos = endpoint.pos();
        }

        speaker.setLinkedSourcePos(sourcePos);
    }
}
