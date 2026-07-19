package dev.turtywurty.mediabox.video;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.turtywurty.mediabox.MediaBox;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public final class ScreenPlaybackSavedData extends SavedData {
    public static final Codec<ScreenPlaybackSavedData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ScreenPlaybackAssignment.CODEC
                            .listOf()
                            .optionalFieldOf("assignments", List.of())
                            .forGetter(data -> List.copyOf(data.assignments.values()))
            ).apply(instance, ScreenPlaybackSavedData::new));

    public static final SavedDataType<ScreenPlaybackSavedData> TYPE = new SavedDataType<>(
            MediaBox.id("screen_playback"),
            ScreenPlaybackSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<UUID, ScreenPlaybackAssignment> assignments = new HashMap<>();

    public ScreenPlaybackSavedData() {
    }

    private ScreenPlaybackSavedData(List<ScreenPlaybackAssignment> assignments) {
        for (ScreenPlaybackAssignment assignment : assignments) {
            this.assignments.put(assignment.screenId(), assignment);
        }
    }

    public static ScreenPlaybackSavedData get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Collection<ScreenPlaybackAssignment> assignments() {
        return List.copyOf(this.assignments.values());
    }

    public Optional<ScreenPlaybackAssignment> get(UUID screenId) {
        return Optional.ofNullable(this.assignments.get(screenId));
    }

    public boolean upsert(ScreenPlaybackAssignment assignment) {
        ScreenPlaybackAssignment previous = this.assignments.put(assignment.screenId(), assignment);

        if (assignment.equals(previous))
            return false;

        setDirty();
        return true;
    }

    public Optional<ScreenPlaybackAssignment> remove(UUID screenId) {
        ScreenPlaybackAssignment removed = this.assignments.remove(screenId);

        if (removed != null)
            setDirty();

        return Optional.ofNullable(removed);
    }
}
