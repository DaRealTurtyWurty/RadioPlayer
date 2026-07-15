package dev.turtywurty.mediabox.client.render.cable;

import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.mixin.RenderTypeAccessor;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class CableRenderTypes {
    private static final RenderType XRAY_LINES = RenderTypeAccessor.mediabox$create(
            MediaBox.MOD_ID + "_cable_xray_lines",
            RenderSetup.builder(CableRenderPipelines.XRAY_LINES)
                    .createRenderSetup());

    private CableRenderTypes() {
    }

    public static RenderType xrayLines() {
        return XRAY_LINES;
    }
}
