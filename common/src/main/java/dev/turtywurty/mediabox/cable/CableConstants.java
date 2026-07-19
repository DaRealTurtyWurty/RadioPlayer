package dev.turtywurty.mediabox.cable;

public final class CableConstants {
    public static final int LENGTH_PER_ITEM = 5;

    private CableConstants() {
    }

    public static int itemsForLength(double length) {
        if (!Double.isFinite(length) || length < 0.0)
            throw new IllegalArgumentException("Cable length must be finite and non-negative");
        return Math.max(1, (int) Math.ceil(length / LENGTH_PER_ITEM));
    }

    public static int capacityForItems(int cableItems) {
        if (cableItems < 1)
            throw new IllegalArgumentException("A cable connection needs at least one cable item");
        return Math.multiplyExact(cableItems, LENGTH_PER_ITEM);
    }
}
