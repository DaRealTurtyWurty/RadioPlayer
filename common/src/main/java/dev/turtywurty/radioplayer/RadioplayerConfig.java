package dev.turtywurty.radioplayer;

import net.blay09.mods.balm.platform.config.reflection.Comment;
import net.blay09.mods.balm.platform.config.reflection.Config;

@Config(Radioplayer.MOD_ID)
public class RadioplayerConfig {
    @Comment("Optional absolute path to an FFmpeg executable. It is used only when Lavaplayer cannot play a stream directly.")
    public String ffmpegExecutablePath = "";
}
