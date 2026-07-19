package dev.turtywurty.mediabox.screen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;

import java.util.Objects;
import java.util.UUID;

public record ScreenAssembly(
        UUID id,
        Direction facing,
        BlockPos origin,
        BlockPos controllerPos,
        int width,
        int height,
        int panelCount,
        boolean rectangular
) {
    public static final Codec<ScreenAssembly> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(ScreenAssembly::id),
            Direction.CODEC.fieldOf("facing").forGetter(ScreenAssembly::facing),
            BlockPos.CODEC.fieldOf("origin").forGetter(ScreenAssembly::origin),
            BlockPos.CODEC.fieldOf("controller_pos").forGetter(ScreenAssembly::controllerPos),
            Codec.INT.fieldOf("width").forGetter(ScreenAssembly::width),
            Codec.INT.fieldOf("height").forGetter(ScreenAssembly::height),
            Codec.INT.fieldOf("panel_count").forGetter(ScreenAssembly::panelCount),
            Codec.BOOL.fieldOf("rectangular").forGetter(ScreenAssembly::rectangular)
    ).apply(instance, ScreenAssembly::new));

    public ScreenAssembly {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(facing, "facing");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(controllerPos, "controllerPos");
        if (!facing.getAxis().isHorizontal())
            throw new IllegalArgumentException("A flat screen must face horizontally");
        if (width < 1 || height < 1 || panelCount < 1)
            throw new IllegalArgumentException("A screen assembly must contain at least one panel");
        if ((long) panelCount > (long) width * height)
            throw new IllegalArgumentException("A screen cannot contain more panels than its bounds");
        if (rectangular != (panelCount == (long) width * height))
            throw new IllegalArgumentException("The rectangular flag does not match the screen dimensions");

        origin = origin.immutable();
        controllerPos = controllerPos.immutable();
    }

    public Direction right() {
        return facing.getCounterClockWise();
    }
}
