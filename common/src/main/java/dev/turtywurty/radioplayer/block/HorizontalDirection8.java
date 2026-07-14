package dev.turtywurty.radioplayer.block;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction8;
import net.minecraft.util.StringRepresentable;

public enum HorizontalDirection8 implements StringRepresentable {
    NORTH("north", Direction8.NORTH, Direction.NORTH),
    NORTH_EAST("north_east", Direction8.NORTH_EAST, Direction.NORTH),
    EAST("east", Direction8.EAST, Direction.EAST),
    SOUTH_EAST("south_east", Direction8.SOUTH_EAST, Direction.SOUTH),
    SOUTH("south", Direction8.SOUTH, Direction.SOUTH),
    SOUTH_WEST("south_west", Direction8.SOUTH_WEST, Direction.SOUTH),
    WEST("west", Direction8.WEST, Direction.WEST),
    NORTH_WEST("north_west", Direction8.NORTH_WEST, Direction.NORTH);

    private final String serializedName;
    private final Direction8 direction8;
    private final Direction nearestCardinal;

    HorizontalDirection8(String serializedName, Direction8 direction8, Direction nearestCardinal) {
        this.serializedName = serializedName;
        this.direction8 = direction8;
        this.nearestCardinal = nearestCardinal;
    }

    public static HorizontalDirection8 fromDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    public static HorizontalDirection8 fromRotation(float rotation) {
        int index = Math.floorMod(Math.round(rotation / 45.0F), values().length);
        return values()[index];
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    public Direction8 asDirection8() {
        return this.direction8;
    }

    public Direction nearestCardinal() {
        return this.nearestCardinal;
    }

    public float yRotationDegrees() {
        return -ordinal() * 45.0F;
    }

    public HorizontalDirection8 rotateClockwise() {
        return values()[(ordinal() + 2) % values().length];
    }

    public HorizontalDirection8 rotateCounterClockwise() {
        return values()[(ordinal() + values().length - 2) % values().length];
    }

    public HorizontalDirection8 opposite() {
        return values()[(ordinal() + 4) % values().length];
    }
}
