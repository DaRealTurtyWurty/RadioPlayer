package dev.turtywurty.mediabox;

import net.blay09.mods.balm.platform.config.reflection.Comment;
import net.blay09.mods.balm.platform.config.reflection.Config;

@Config(MediaBox.MOD_ID)
public class MediaBoxConfig {
    @Comment("Optional absolute path to an FFmpeg executable. It is used only when Lavaplayer cannot play a stream directly.")
    public String ffmpegExecutablePath = "";
}
