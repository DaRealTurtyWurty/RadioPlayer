package dev.turtywurty.mediabox.screen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.turtywurty.mediabox.MediaBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public final class ScreenSavedData extends SavedData {
    public static final Codec<ScreenSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ScreenAssembly.CODEC.listOf().optionalFieldOf("assemblies", List.of())
                    .forGetter(data -> List.copyOf(data.assemblies.values()))
    ).apply(instance, ScreenSavedData::new));

    public static final SavedDataType<ScreenSavedData> TYPE = new SavedDataType<>(
            MediaBox.id("screen_assemblies"),
            ScreenSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, ScreenAssembly> assemblies = new HashMap<>();

    public ScreenSavedData() {
    }

    private ScreenSavedData(List<ScreenAssembly> assemblies) {
        for (ScreenAssembly assembly : assemblies) {
            this.assemblies.put(assembly.id(), assembly);
        }
    }

    public static ScreenSavedData get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Collection<ScreenAssembly> assemblies() {
        return List.copyOf(this.assemblies.values());
    }

    public Optional<ScreenAssembly> get(UUID id) {
        return Optional.ofNullable(this.assemblies.get(id));
    }

    public boolean upsert(ScreenAssembly assembly) {
        ScreenAssembly previous = this.assemblies.put(assembly.id(), assembly);
        if (assembly.equals(previous))
            return false;

        setDirty();
        return true;
    }

    public boolean remove(UUID id) {
        if (this.assemblies.remove(id) == null)
            return false;

        setDirty();
        return true;
    }

    public Optional<ScreenAssembly> findRemovedSingleton(BlockPos pos, Direction facing) {
        return this.assemblies.values().stream()
                .filter(assembly -> assembly.panelCount() == 1)
                .filter(assembly -> assembly.controllerPos().equals(pos))
                .filter(assembly -> assembly.facing() == facing)
                .findFirst();
    }
}
