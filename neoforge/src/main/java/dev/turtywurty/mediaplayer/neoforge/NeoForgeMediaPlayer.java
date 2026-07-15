package dev.turtywurty.mediaplayer.neoforge;

import dev.turtywurty.mediaplayer.MediaPlayer;
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

@Mod(MediaPlayer.MOD_ID)
public class NeoForgeMediaPlayer {
    public NeoForgeMediaPlayer(ModContainer modContainer, IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeMediaPlayer::addPackFinders);

        final var context = new NeoForgeLoadContext(modContainer, modEventBus);
        Balm.initializeMod(MediaPlayer.MOD_ID, context, MediaPlayer::initialize);
    }

    private static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES)
            return;

        event.addPackFinders(
                MediaPlayer.id("resourcepacks/high_res_earth"),
                PackType.CLIENT_RESOURCES,
                Component.literal("Media Player 8k Earth Textures"),
                PackSource.BUILT_IN,
                false,
                Pack.Position.TOP);
    }
}
