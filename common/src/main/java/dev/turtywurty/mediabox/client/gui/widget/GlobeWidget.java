package dev.turtywurty.mediabox.client.gui.widget;

import dev.turtywurty.mediabox.api.client.GlobePoint;
import dev.turtywurty.mediabox.client.render.globe.SpherePictureRenderState;
import dev.turtywurty.mediabox.mixin.GuiGraphicsExtractorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class GlobeWidget extends AbstractWidget {
    private final List<GlobePoint> points;
    private final float minZoom;
    private final float maxZoom;
    private final double zoomStep;
    private final float maxPitch;
    private final long autoRotationPeriodMs;
    private final boolean panningEnabled;
    private final boolean zoomEnabled;
    private final boolean clipToBounds;
    private final float initialYaw;
    private final float initialPitch;
    private final float initialZoom;
    private final boolean initiallyUserControlled;
    private final Consumer<GlobePoint> pointClickHandler;
    private final Consumer<PointCollisionResult> pointCollisionHandler;

    private int sphereX;
    private int sphereY;
    private int sphereSize;
    private List<GlobePoint> renderablePoints = List.of();
    private float yaw;
    private float pitch;
    private float zoom;
    private float pointSizeMultiplier = 1.0F;
    private boolean draggingSphere;
    private boolean userControlled;
    private @Nullable GlobePoint hoveredPoint;

    private GlobeWidget(Builder builder) {
        super(builder.x, builder.y, builder.width, builder.height, builder.message);
        this.points = new ArrayList<>(builder.points);
        this.minZoom = builder.minZoom;
        this.maxZoom = builder.maxZoom;
        this.zoomStep = builder.zoomStep;
        this.maxPitch = (float) Math.toRadians(builder.maxPitchDegrees);
        this.autoRotationPeriodMs = builder.autoRotationPeriodMs;
        this.panningEnabled = builder.panningEnabled;
        this.zoomEnabled = builder.zoomEnabled;
        this.clipToBounds = builder.clipToBounds;
        this.initialZoom = builder.initialZoom;
        this.initialYaw = wrapRadians((float) Math.toRadians(builder.initialYawDegrees));
        this.initialPitch = (float) Math.toRadians(builder.initialPitchDegrees);
        this.initiallyUserControlled = builder.initialRotationSet;
        this.pointClickHandler = builder.pointClickHandler;
        this.pointCollisionHandler = builder.pointCollisionHandler;
        resetView();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static @Nullable ScreenPoint findOverlappingPoint(List<ScreenPoint> occupiedPoints, double x, double y, double radius) {
        for (ScreenPoint occupiedPoint : occupiedPoints) {
            double dx = x - occupiedPoint.x();
            double dy = y - occupiedPoint.y();
            double minDistance = radius + occupiedPoint.radius();
            if (dx * dx + dy * dy < minDistance * minDistance)
                return occupiedPoint;
        }

        return null;
    }

    private static Vector3f pointPosition(GlobePoint point) {
        double latitude = Math.toRadians(point.getLatitude());
        double longitude = Math.toRadians(point.getLongitude() + 180.0D);
        float cosLatitude = (float) Math.cos(latitude);
        return new Vector3f(
                cosLatitude * (float) Math.cos(longitude),
                -(float) Math.sin(latitude),
                cosLatitude * (float) Math.sin(longitude)
        );
    }

    private static float wrapRadians(float angle) {
        angle %= Mth.TWO_PI;
        if (angle > Math.PI) {
            angle -= Mth.TWO_PI;
        } else if (angle < -Math.PI) {
            angle += Mth.TWO_PI;
        }

        return angle;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        int viewportSize = Math.clamp(getHeight(), 1, getWidth());
        this.sphereSize = Math.max(1, Math.round(viewportSize * this.zoom));
        this.sphereX = getX() + (getWidth() - this.sphereSize) / 2;
        this.sphereY = getY() + (getHeight() - this.sphereSize) / 2;

        Quaternionf renderRotation = currentRenderRotation();
        PointCollisionResult pointCollisionResult = visiblePoints(renderRotation);
        this.renderablePoints = pointCollisionResult.visiblePoints();
        this.pointCollisionHandler.accept(pointCollisionResult);
        var renderState = getSpherePictureRenderState(renderRotation);
        ((GuiGraphicsExtractorAccessor) graphics)
                .mediabox$getGuiRenderState()
                .addPicturesInPictureState(renderState);

        this.hoveredPoint = isMouseOver(mouseX, mouseY)
                ? findPointAt(mouseX, mouseY, renderRotation)
                : null;
        if (this.hoveredPoint != null && this.hoveredPoint.getTooltip() != null)
            graphics.setTooltipForNextFrame(Minecraft.getInstance().font, this.hoveredPoint.getTooltip(), mouseX, mouseY);
    }

    private @NonNull SpherePictureRenderState getSpherePictureRenderState(Quaternionf renderRotation) {
        ScreenRectangle scissorArea = this.clipToBounds
                ? new ScreenRectangle(getX(), getY(), getWidth(), getHeight())
                : null;
        return new SpherePictureRenderState(
                getX(),
                getY(),
                getWidth(),
                getHeight(),
                this.sphereSize,
                this.pointSizeMultiplier,
                renderRotation.x,
                renderRotation.y,
                renderRotation.z,
                renderRotation.w,
                this.renderablePoints,
                scissorArea
        );
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        GlobePoint clickedPoint = findPointAt(event.x(), event.y(), currentRenderRotation());
        if (clickedPoint != null) {
            playButtonClickSound(Minecraft.getInstance().getSoundManager());
            this.pointClickHandler.accept(clickedPoint);
            return;
        }

        if (this.panningEnabled) {
            this.draggingSphere = true;
            beginUserInteraction();
        }
    }

    @Override
    public void onRelease(@NonNull MouseButtonEvent event) {
        this.draggingSphere = false;
    }

    @Override
    protected void onDrag(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (!this.draggingSphere)
            return;

        float radiansPerPixel = 1.0F / sphereRadius();
        this.yaw = wrapRadians(this.yaw - (float) dx * radiansPerPixel);
        this.pitch = Mth.clamp(this.pitch - (float) dy * radiansPerPixel, -this.maxPitch, this.maxPitch);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.zoomEnabled || scrollY == 0.0D || !isMouseOver(mouseX, mouseY))
            return false;

        beginUserInteraction();
        setZoom((float) (this.zoom * Math.pow(this.zoomStep, scrollY)));
        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!super.isMouseOver(mouseX, mouseY))
            return false;

        double dx = mouseX - sphereCenterX();
        double dy = mouseY - sphereCenterY();
        double radius = sphereRadius();
        return dx * dx + dy * dy <= radius * radius;
    }

    @Override
    public void playDownSound(@NonNull SoundManager soundManager) {
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    public List<GlobePoint> getPoints() {
        return List.copyOf(this.points);
    }

    public void setPoints(Collection<GlobePoint> points) {
        this.points.clear();
        this.points.addAll(points);
    }

    public void addPoint(GlobePoint point) {
        this.points.add(point);
    }

    public void clearPoints() {
        this.points.clear();
    }

    public @Nullable GlobePoint getHoveredPoint() {
        return this.hoveredPoint;
    }

    public float getZoom() {
        return this.zoom;
    }

    public void setZoom(float zoom) {
        if (!Float.isFinite(zoom))
            throw new IllegalArgumentException("Zoom must be finite");

        this.zoom = Mth.clamp(zoom, this.minZoom, this.maxZoom);
    }

    public float getYawDegrees() {
        return (float) Math.toDegrees(this.yaw);
    }

    public float getPitchDegrees() {
        return (float) Math.toDegrees(this.pitch);
    }

    public boolean isUserControlled() {
        return this.userControlled;
    }

    public float getPointSizeMultiplier() {
        return this.pointSizeMultiplier;
    }

    public void setPointSizeMultiplier(float pointSizeMultiplier) {
        if (!Float.isFinite(pointSizeMultiplier) || pointSizeMultiplier <= 0.0F)
            throw new IllegalArgumentException("Point size multiplier must be finite and greater than zero");

        this.pointSizeMultiplier = pointSizeMultiplier;
    }

    public void setRotation(float yawDegrees, float pitchDegrees) {
        if (!Float.isFinite(yawDegrees) || !Float.isFinite(pitchDegrees))
            throw new IllegalArgumentException("Rotation must be finite");

        this.yaw = wrapRadians((float) Math.toRadians(yawDegrees));
        this.pitch = Mth.clamp((float) Math.toRadians(pitchDegrees), -this.maxPitch, this.maxPitch);
        this.userControlled = true;
    }

    public void resetView() {
        this.yaw = this.initialYaw;
        this.pitch = this.initialPitch;
        this.zoom = this.initialZoom;
        this.userControlled = this.initiallyUserControlled;
    }

    private void beginUserInteraction() {
        if (!this.userControlled) {
            this.yaw = automaticYaw();
            this.userControlled = true;
        }
    }

    private Quaternionf currentRenderRotation() {
        float renderYaw = this.userControlled ? this.yaw : automaticYaw();
        return new Quaternionf()
                .rotateX(this.pitch)
                .rotateY(renderYaw);
    }

    private float automaticYaw() {
        if (this.autoRotationPeriodMs <= 0L)
            return this.yaw;

        return Mth.TWO_PI * (System.currentTimeMillis() % this.autoRotationPeriodMs) / this.autoRotationPeriodMs;
    }

    private @Nullable GlobePoint findPointAt(double mouseX, double mouseY, Quaternionf rotation) {
        GlobePoint closestPoint = null;
        float closestDepth = 0.0F;
        float globeRadius = sphereRadius();
        for (GlobePoint point : this.renderablePoints) {
            Vector3f position = pointPosition(point);
            rotation.transform(position);
            if (position.z <= 0.0F)
                continue;

            float pointSize = point.getSize() * this.pointSizeMultiplier;
            float distanceFromCenter = globeRadius + pointSize * 0.04F;
            double pointX = sphereCenterX() - position.x * distanceFromCenter;
            double pointY = sphereCenterY() + position.y * distanceFromCenter;
            double dx = mouseX - pointX;
            double dy = mouseY - pointY;
            double hitRadius = Math.max(4.0D, pointSize * 0.5D);
            if (dx * dx + dy * dy <= hitRadius * hitRadius && position.z > closestDepth) {
                closestPoint = point;
                closestDepth = position.z;
            }
        }

        return closestPoint;
    }

    private PointCollisionResult visiblePoints(Quaternionf rotation) {
        List<GlobePoint> visiblePoints = new ArrayList<>();
        List<HiddenPointGroup> hiddenPointGroups = new ArrayList<>();
        List<ScreenPoint> occupiedPoints = new ArrayList<>();
        float globeRadius = sphereRadius();
        double centerX = sphereCenterX();
        double centerY = sphereCenterY();
        double left = getX();
        double right = getX() + getWidth();
        double top = getY();
        double bottom = getY() + getHeight();

        for (GlobePoint point : this.points) {
            Vector3f position = pointPosition(point);
            rotation.transform(position);
            if (position.z <= 0.0F)
                continue;

            float pointSize = point.getSize() * this.pointSizeMultiplier;
            float distanceFromCenter = globeRadius + pointSize * 0.04F;
            double pointX = centerX - position.x * distanceFromCenter;
            double pointY = centerY + position.y * distanceFromCenter;
            double pointRadius = pointSize * 0.5D;
            if (pointX + pointRadius < left || pointX - pointRadius > right ||
                    pointY + pointRadius < top || pointY - pointRadius > bottom) {
                continue;
            }

            ScreenPoint overlappingPoint = findOverlappingPoint(occupiedPoints, pointX, pointY, pointRadius);
            if (overlappingPoint == null) {
                visiblePoints.add(point);
                occupiedPoints.add(new ScreenPoint(point, pointX, pointY, pointRadius));
            } else {
                hiddenPointGroups.add(new HiddenPointGroup(overlappingPoint.point(), point));
            }
        }

        return new PointCollisionResult(visiblePoints, hiddenPointGroups);
    }

    private double sphereCenterX() {
        return this.sphereX + this.sphereSize / 2.0D;
    }

    private double sphereCenterY() {
        return this.sphereY + this.sphereSize / 2.0D;
    }

    private float sphereRadius() {
        int currentSize = this.sphereSize > 0
                ? this.sphereSize
                : Math.max(1, Math.round(Math.min(getWidth(), getHeight()) * this.zoom));
        return currentSize * 0.45F;
    }

    public static final class Builder {
        private final List<GlobePoint> points = new ArrayList<>();
        private int x;
        private int y;
        private int width = 100;
        private int height = 100;
        private Component message = Component.empty();
        private float minZoom = 1.0F;
        private float maxZoom = 5.0F;
        private float initialZoom = 1.0F;
        private double zoomStep = 1.15D;
        private float maxPitchDegrees = 85.0F;
        private float initialYawDegrees;
        private float initialPitchDegrees;
        private boolean initialRotationSet;
        private long autoRotationPeriodMs = 360_000L;
        private boolean panningEnabled = true;
        private boolean zoomEnabled = true;
        private boolean clipToBounds = true;
        private Consumer<GlobePoint> pointClickHandler = GlobePoint::click;
        private Consumer<PointCollisionResult> pointCollisionHandler = _ -> {
        };

        private Builder() {
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder message(Component message) {
            this.message = message;
            return this;
        }

        public Builder points(Collection<GlobePoint> points) {
            this.points.clear();
            this.points.addAll(points);
            return this;
        }

        public Builder addPoint(GlobePoint point) {
            this.points.add(point);
            return this;
        }

        public Builder zoom(float initialZoom, float minZoom, float maxZoom) {
            this.initialZoom = initialZoom;
            this.minZoom = minZoom;
            this.maxZoom = maxZoom;
            return this;
        }

        public Builder zoomStep(double zoomStep) {
            this.zoomStep = zoomStep;
            return this;
        }

        public Builder maxPitch(float maxPitchDegrees) {
            this.maxPitchDegrees = maxPitchDegrees;
            return this;
        }

        public Builder initialRotation(float yawDegrees, float pitchDegrees) {
            this.initialYawDegrees = yawDegrees;
            this.initialPitchDegrees = pitchDegrees;
            this.initialRotationSet = true;
            return this;
        }

        public Builder autoRotationPeriod(long periodMs) {
            this.autoRotationPeriodMs = periodMs;
            return this;
        }

        public Builder panning(boolean enabled) {
            this.panningEnabled = enabled;
            return this;
        }

        public Builder zooming(boolean enabled) {
            this.zoomEnabled = enabled;
            return this;
        }

        public Builder clipToBounds(boolean clipToBounds) {
            this.clipToBounds = clipToBounds;
            return this;
        }

        public Builder onPointClick(Consumer<GlobePoint> pointClickHandler) {
            this.pointClickHandler = pointClickHandler;
            return this;
        }

        public Builder onPointCollisions(Consumer<PointCollisionResult> pointCollisionHandler) {
            this.pointCollisionHandler = pointCollisionHandler;
            return this;
        }

        public GlobeWidget build() {
            if (this.width <= 0 || this.height <= 0)
                throw new IllegalStateException("Globe widget dimensions must be greater than zero");
            if (!Float.isFinite(this.minZoom) || !Float.isFinite(this.maxZoom) || this.minZoom <= 0.0F || this.maxZoom < this.minZoom)
                throw new IllegalStateException("Globe widget zoom range is invalid");
            if (!Float.isFinite(this.initialZoom) || this.initialZoom < this.minZoom || this.initialZoom > this.maxZoom)
                throw new IllegalStateException("Initial zoom must be within the zoom range");
            if (!Double.isFinite(this.zoomStep) || this.zoomStep <= 1.0D)
                throw new IllegalStateException("Zoom step must be greater than one");
            if (!Float.isFinite(this.maxPitchDegrees) || this.maxPitchDegrees <= 0.0F || this.maxPitchDegrees > 90.0F)
                throw new IllegalStateException("Maximum pitch must be between zero and 90 degrees");
            if (!Float.isFinite(this.initialYawDegrees) || !Float.isFinite(this.initialPitchDegrees) || Math.abs(this.initialPitchDegrees) > this.maxPitchDegrees)
                throw new IllegalStateException("Initial rotation is invalid");
            if (this.autoRotationPeriodMs < 0L)
                throw new IllegalStateException("Auto-rotation period cannot be negative");
            if (this.pointClickHandler == null)
                throw new IllegalStateException("Point click handler cannot be null");
            if (this.pointCollisionHandler == null)
                throw new IllegalStateException("Point collision handler cannot be null");

            return new GlobeWidget(this);
        }
    }

    private record ScreenPoint(GlobePoint point, double x, double y, double radius) {
    }

    public record HiddenPointGroup(GlobePoint visiblePoint, GlobePoint hiddenPoint) {
    }

    public record PointCollisionResult(List<GlobePoint> visiblePoints, List<HiddenPointGroup> hiddenPointGroups) {
        public PointCollisionResult {
            visiblePoints = List.copyOf(visiblePoints);
            hiddenPointGroups = List.copyOf(hiddenPointGroups);
        }
    }
}
