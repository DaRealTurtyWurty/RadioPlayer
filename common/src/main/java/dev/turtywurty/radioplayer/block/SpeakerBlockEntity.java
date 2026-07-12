package dev.turtywurty.radioplayer.block;

import dev.turtywurty.radioplayer.block.entity.RadioPlayerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class SpeakerBlockEntity extends BlockEntity {
    private static final int RADIO_SEARCH_RADIUS = 8;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.speaker.value(), pos, state);
    }

    public @Nullable RadioPlayerBlockEntity findSourceRadio() {
        Level level = getLevel();
        if (level == null)
            return null;

        RadioPlayerBlockEntity closestRadio = null;
        double closestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                this.worldPosition.offset(-RADIO_SEARCH_RADIUS, -RADIO_SEARCH_RADIUS, -RADIO_SEARCH_RADIUS),
                this.worldPosition.offset(RADIO_SEARCH_RADIUS, RADIO_SEARCH_RADIUS, RADIO_SEARCH_RADIUS))) {
            if (!(level.getBlockEntity(pos) instanceof RadioPlayerBlockEntity radio) ||
                    !radio.isPlaying() ||
                    radio.getUrl().isBlank()) {
                continue;
            }

            double distance = pos.distSqr(this.worldPosition);
            if (distance < closestDistance) {
                closestRadio = radio;
                closestDistance = distance;
            }
        }

        return closestRadio;
    }
}
