package dev.turtywurty.mediabox.block;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.cable.MediaPort;
import dev.turtywurty.mediabox.cable.MediaSignalType;
import dev.turtywurty.mediabox.cable.PortDirection;
import dev.turtywurty.mediabox.cable.concealed.ConcealedCablePortProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Set;

public class CablePortBlockEntity extends BlockEntity implements ConcealedCablePortProvider {
    public static final Identifier AUDIO_PORT_ID = MediaBox.id("cable_port_audio");

    public CablePortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.cablePort.value(), pos, state);
    }

    @Override
    public List<MediaPort> getMediaPorts() {
        return List.of(new MediaPort(
                AUDIO_PORT_ID,
                getBlockState().getValue(CablePortBlock.FACING),
                CablePortBlock.attachmentPoint(getBlockState()),
                PortDirection.BIDIRECTIONAL,
                MediaPort.UNLIMITED_CONNECTIONS,
                Set.of(MediaSignalType.AUDIO)));
    }
}
