package dev.turtywurty.mediabox.block;

import dev.turtywurty.mediabox.block.entity.FlatScreenBlockEntity;
import dev.turtywurty.mediabox.block.entity.GlobeBlockEntity;
import dev.turtywurty.mediabox.block.entity.RadioPlayerBlockEntity;
import net.blay09.mods.balm.world.level.block.entity.BalmBlockEntityTypeRegistrar;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlockEntities {
    public static Holder<BlockEntityType<RadioPlayerBlockEntity>> radioPlayer;
    public static Holder<BlockEntityType<GlobeBlockEntity>> globe;
    public static Holder<BlockEntityType<SpeakerBlockEntity>> speaker;
    public static Holder<BlockEntityType<CablePortBlockEntity>> cablePort;
    public static Holder<BlockEntityType<FlatScreenBlockEntity>> flatScreen;

    public static void initialize(BalmBlockEntityTypeRegistrar blockEntities) {
        radioPlayer = blockEntities.register("radio_payer", RadioPlayerBlockEntity::new, ModBlocks.radioPlayer).asHolder();

        globe = blockEntities.register("globe", GlobeBlockEntity::new, ModBlocks.globe).asHolder();

        speaker = blockEntities.register("speaker", SpeakerBlockEntity::new,
                ModBlocks.speaker,
                ModBlocks.subwoofer,
                ModBlocks.bassReflexSpeaker,
                ModBlocks.hornSpeaker,
                ModBlocks.bookshelfSpeaker,
                ModBlocks.floorStandingSpeaker).asHolder();

        cablePort = blockEntities.register("cable_port", CablePortBlockEntity::new, ModBlocks.cablePort).asHolder();

        flatScreen = blockEntities.register("flat_screen", FlatScreenBlockEntity::new, ModBlocks.flatScreen).asHolder();
    }
}
