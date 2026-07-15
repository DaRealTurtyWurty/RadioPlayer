package dev.turtywurty.mediabox.cable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** The server-authored physical path followed by a visible cable. */
public record VisibleCableRoute(List<Vec3> points) {
    private static final Codec<Vec3> POINT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("x").forGetter(Vec3::x),
            Codec.DOUBLE.fieldOf("y").forGetter(Vec3::y),
            Codec.DOUBLE.fieldOf("z").forGetter(Vec3::z)
    ).apply(instance, Vec3::new));

    public static final Codec<VisibleCableRoute> CODEC = POINT_CODEC.listOf()
            .fieldOf("points")
            .xmap(VisibleCableRoute::new, VisibleCableRoute::points)
            .codec();

    public VisibleCableRoute {
        points = List.copyOf(points);
        if (points.size() < 2)
            throw new IllegalArgumentException("A visible cable route needs at least two points");

        for (Vec3 point : points) {
            if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z))
                throw new IllegalArgumentException("Visible cable route points must be finite");
        }
    }

    public double length() {
        double length = 0.0;
        for (int index = 1; index < points.size(); index++) {
            length += points.get(index - 1).distanceTo(points.get(index));
        }
        return length;
    }
}
