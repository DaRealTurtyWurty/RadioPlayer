package dev.turtywurty.mediabox.neoforge;

import dev.turtywurty.mediabox.MediaBox;
import net.blay09.mods.balm.Balm;
import net.blay09.mods.balm.neoforge.platform.runtime.NeoForgeLoadContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddPackFindersEvent;

@Mod(MediaBox.MOD_ID)
public class NeoForgeMediaBox {
    public NeoForgeMediaBox(ModContainer modContainer, IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeMediaBox::addPackFinders);

        final var context = new NeoForgeLoadContext(modContainer, modEventBus);
        Balm.initializeMod(MediaBox.MOD_ID, context, MediaBox::initialize);
    }

    private static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES)
            return;

        event.addPackFinders(
                MediaBox.id("resourcepacks/high_res_earth"),
                PackType.CLIENT_RESOURCES,
                Component.literal("Media Player 8k Earth Textures"),
                PackSource.BUILT_IN,
                false,
                Pack.Position.TOP);
    }
}
