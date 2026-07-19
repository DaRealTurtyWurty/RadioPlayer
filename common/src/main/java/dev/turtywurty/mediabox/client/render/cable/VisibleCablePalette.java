package dev.turtywurty.mediabox.client.render.cable;

import dev.turtywurty.mediabox.cable.MediaSignalType;

/** The alternating stripe colours used to distinguish visible cable signal types. */
public record VisibleCablePalette(int firstStripe, int secondStripe) {
    private static final VisibleCablePalette AUDIO = new VisibleCablePalette(0xFFFF8A00, 0xFFD92929);
    private static final VisibleCablePalette VIDEO = new VisibleCablePalette(0xFF00A6FB, 0xFF4747D1);

    public static VisibleCablePalette forSignalType(MediaSignalType signalType) {
        // Keep this exhaustive so adding a signal type also requires choosing its cable colours.
        return switch (signalType) {
            case AUDIO -> AUDIO;
            case VIDEO -> VIDEO;
        };
    }

    public int stripe(int index) {
        return (index & 1) == 0 ? this.firstStripe : this.secondStripe;
    }
}
