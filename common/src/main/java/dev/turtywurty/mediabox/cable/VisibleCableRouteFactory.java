package dev.turtywurty.mediabox.cable;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Creates a simple hanging curve between two visible cable ports. */
public final class VisibleCableRouteFactory {
    private static final double POINT_SPACING = 0.2;
    private static final double BASE_SAG = 0.12;
    private static final double SAG_PER_BLOCK = 0.08;
    private static final double MAX_SAG = 0.8;
    private static final int MIN_SEGMENTS = 8;
    private static final int MAX_SEGMENTS = 128;
    private static final int SAG_SEARCH_STEPS = 20;

    private VisibleCableRouteFactory() {
    }

    public static PurchasedRoute create(ResolvedMediaPort firstPort, ResolvedMediaPort secondPort) {
        int cableItems = requiredItems(firstPort, secondPort);
        return new PurchasedRoute(create(firstPort, secondPort, cableItems), cableItems);
    }

    public static VisibleCableRoute create(
            ResolvedMediaPort firstPort,
            ResolvedMediaPort secondPort,
            int cableItems) {
        if (PortEndpoint.CANONICAL_ORDER.compare(firstPort.endpoint(), secondPort.endpoint()) > 0) {
            ResolvedMediaPort previousFirst = firstPort;
            firstPort = secondPort;
            secondPort = previousFirst;
        }
        return createCurve(
                portPosition(firstPort),
                portPosition(secondPort),
                CableConstants.capacityForItems(cableItems));
    }

    public static int requiredItems(ResolvedMediaPort firstPort, ResolvedMediaPort secondPort) {
        return CableConstants.itemsForLength(portPosition(firstPort).distanceTo(portPosition(secondPort)));
    }

    public static Vec3 portPosition(ResolvedMediaPort port) {
        return port.port().worldPosition(port.endpoint().pos());
    }

    private static VisibleCableRoute createCurve(Vec3 first, Vec3 second, double capacity) {
        double directLength = first.distanceTo(second);
        int segments = Math.clamp(
                (int) Math.ceil(directLength / POINT_SPACING),
                MIN_SEGMENTS,
                MAX_SEGMENTS);
        double preferredSag = Math.min(MAX_SAG, BASE_SAG + directLength * SAG_PER_BLOCK);

        double low = 0.0;
        double high = preferredSag;
        List<Vec3> best = sampleCurve(first, second, segments, 0.0);
        for (int iteration = 0; iteration < SAG_SEARCH_STEPS; iteration++) {
            double sag = (low + high) * 0.5;
            List<Vec3> candidate = sampleCurve(first, second, segments, sag);
            if (polylineLength(candidate) <= capacity) {
                low = sag;
                best = candidate;
            } else {
                high = sag;
            }
        }
        return new VisibleCableRoute(best);
    }

    private static List<Vec3> sampleCurve(Vec3 first, Vec3 second, int segments, double sag) {
        List<Vec3> points = new ArrayList<>(segments + 1);
        for (int index = 0; index <= segments; index++) {
            double progress = index / (double) segments;
            double verticalOffset = sag * 4.0 * progress * (1.0 - progress);
            points.add(first.lerp(second, progress).add(0.0, -verticalOffset, 0.0));
        }
        return List.copyOf(points);
    }

    private static double polylineLength(List<Vec3> points) {
        double length = 0.0;
        for (int index = 1; index < points.size(); index++) {
            length += points.get(index - 1).distanceTo(points.get(index));
        }
        return length;
    }

    public record PurchasedRoute(VisibleCableRoute route, int cableItems) {
    }
}
