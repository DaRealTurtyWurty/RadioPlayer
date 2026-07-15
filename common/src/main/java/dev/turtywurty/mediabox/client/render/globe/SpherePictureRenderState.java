package dev.turtywurty.mediabox.client.render.globe;

import dev.turtywurty.mediabox.api.client.GlobePoint;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record SpherePictureRenderState(
        int x0,
        int x1,
        int y0,
        int y1,
        float scale,
        int sphereSize,
        float pointSizeMultiplier,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW,
        List<GlobePoint> points,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {

    public SpherePictureRenderState(
            int x,
            int y,
            int size,
            float rotationX,
            float rotationY,
            float rotationZ,
            float rotationW,
            List<GlobePoint> points,
            @Nullable ScreenRectangle scissorArea
    ) {
        this(
                x,
                x + size,
                y,
                y + size,
                1.0F,
                size,
                1.0F,
                rotationX,
                rotationY,
                rotationZ,
                rotationW,
                List.copyOf(points),
                scissorArea,
                PictureInPictureRenderState.getBounds(x, y, x + size, y + size, scissorArea)
        );
    }

    public SpherePictureRenderState(
            int x,
            int y,
            int width,
            int height,
            int sphereSize,
            float pointSizeMultiplier,
            float rotationX,
            float rotationY,
            float rotationZ,
            float rotationW,
            List<GlobePoint> points,
            @Nullable ScreenRectangle scissorArea
    ) {
        this(
                x,
                x + width,
                y,
                y + height,
                1.0F,
                sphereSize,
                pointSizeMultiplier,
                rotationX,
                rotationY,
                rotationZ,
                rotationW,
                List.copyOf(points),
                scissorArea,
                PictureInPictureRenderState.getBounds(x, y, x + width, y + height, scissorArea)
        );
    }
}
