package dev.turtywurty.mediabox.cable;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/** The server-authored render curve followed by a visible cable. */
public record VisibleCableRoute(List<Vec3> points) {
    public VisibleCableRoute {
        points = List.copyOf(points);
        if (points.size() < 2)
            throw new IllegalArgumentException("A visible cable route needs at least two points");

        for (Vec3 point : points) {
            if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z))
                throw new IllegalArgumentException("Visible cable route points must be finite");
        }
    }
}
