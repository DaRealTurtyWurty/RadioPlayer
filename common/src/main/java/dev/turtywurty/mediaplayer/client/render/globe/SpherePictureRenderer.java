package dev.turtywurty.mediaplayer.client.render.globe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.turtywurty.mediaplayer.MediaPlayer;
import dev.turtywurty.mediaplayer.api.client.GlobePoint;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;

public class SpherePictureRenderer extends PictureInPictureRenderer<SpherePictureRenderState> {
    private static final int POINT_SEGMENTS = 32;

    private static void renderUvSphere(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            float radius,
            int stacks,
            int slices,
            int color,
            int packedLight
    ) {
        RenderType renderType = SphereRenderTypes.earthNormalMapped();
        submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> {
            for (int stack = 0; stack < stacks; stack++) {
                float v0 = (float) stack / stacks;
                float v1 = (float) (stack + 1) / stacks;
                float phi0 = (float) Math.PI * v0;
                float phi1 = (float) Math.PI * v1;

                for (int slice = 0; slice < slices; slice++) {
                    float u0 = (float) slice / slices;
                    float u1 = (float) (slice + 1) / slices;
                    float theta0 = Mth.TWO_PI * u0;
                    float theta1 = Mth.TWO_PI * u1;

                    SphereVertex topLeft = sphereVertex(radius, phi0, theta0, u0, v0);
                    SphereVertex bottomLeft = sphereVertex(radius, phi1, theta0, u0, v1);
                    SphereVertex bottomRight = sphereVertex(radius, phi1, theta1, u1, v1);
                    SphereVertex topRight = sphereVertex(radius, phi0, theta1, u1, v0);

                    emitVertex(pose, vertexConsumer, topLeft, color, packedLight);
                    emitVertex(pose, vertexConsumer, bottomLeft, color, packedLight);
                    emitVertex(pose, vertexConsumer, bottomRight, color, packedLight);
                    emitVertex(pose, vertexConsumer, topRight, color, packedLight);
                }
            }
        });
    }

    public static void renderPoint(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            float globeRadius,
            GlobePoint point,
            float pointSizeMultiplier
    ) {
        double latitude = Math.toRadians(point.getLatitude());
        double longitude = Math.toRadians(point.getLongitude() + 180.0D);
        float cosLatitude = (float) Math.cos(latitude);
        float cosLongitude = (float) Math.cos(longitude);
        float sinLongitude = (float) Math.sin(longitude);
        float nx = cosLatitude * cosLongitude;
        float ny = -(float) Math.sin(latitude);
        float nz = cosLatitude * sinLongitude;

        float markerRadius = point.getSize() * pointSizeMultiplier * 0.5F;
        float distance = globeRadius + markerRadius * 0.08F;
        float centerX = nx * distance;
        float centerY = ny * distance;
        float centerZ = nz * distance;

        float tangentX = -sinLongitude;
        float tangentZ = cosLongitude;
        float bitangentX = ny * cosLongitude;
        float bitangentY = -cosLatitude;
        float bitangentZ = ny * sinLongitude;

        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.debugTriangleFan(), (pose, consumer) -> {
            consumer.addVertex(pose.pose(), centerX, centerY, centerZ).setColor(point.getColor());
            for (int segment = 0; segment <= POINT_SEGMENTS; segment++) {
                float angle = Mth.TWO_PI * segment / POINT_SEGMENTS;
                float tangentScale = Mth.cos(angle) * markerRadius;
                float bitangentScale = Mth.sin(angle) * markerRadius;
                consumer.addVertex(
                                pose.pose(),
                                centerX + tangentX * tangentScale + bitangentX * bitangentScale,
                                centerY + bitangentY * bitangentScale,
                                centerZ + tangentZ * tangentScale + bitangentZ * bitangentScale
                        )
                        .setColor(point.getColor());
            }
        });
    }

    private static SphereVertex sphereVertex(float radius, float phi, float theta, float u, float v) {
        float sinPhi = Mth.sin(phi);
        float nx = sinPhi * Mth.cos(theta);
        float ny = Mth.cos(phi);
        float nz = sinPhi * Mth.sin(theta);
        return new SphereVertex(
                nx * radius,
                ny * radius,
                nz * radius,
                nx,
                ny,
                nz,
                u,
                1.0F - v
        );
    }

    private static void emitVertex(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            SphereVertex vertex,
            int color,
            int packedLight
    ) {
        consumer.addVertex(pose.pose(), vertex.x(), vertex.y(), vertex.z())
                .setColor(color)
                .setUv(vertex.u(), vertex.v())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, vertex.nx(), vertex.ny(), vertex.nz());
    }

    @Override
    public @NonNull Class<SpherePictureRenderState> getRenderStateClass() {
        return SpherePictureRenderState.class;
    }

    @Override
    protected void renderToTexture(
            @NonNull SpherePictureRenderState renderState,
            @NonNull PoseStack poseStack,
            @NonNull SubmitNodeCollector submitNodeCollector
    ) {
        poseStack.pushPose();
        float viewportSize = Math.min(renderState.x1() - renderState.x0(), renderState.y1() - renderState.y0());
        if (viewportSize <= 0.0F) {
            poseStack.popPose();
            return;
        }

        float zoomScale = renderState.sphereSize() / viewportSize;
        poseStack.scale(zoomScale, zoomScale, 1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(new Quaternionf(
                renderState.rotationX(),
                renderState.rotationY(),
                renderState.rotationZ(),
                renderState.rotationW()
        ));

        float radius = viewportSize * 0.45F;
        renderUvSphere(poseStack, submitNodeCollector, radius, 48, 32, 0xFFFFFFFF, LightCoordsUtil.FULL_BRIGHT);
        for (GlobePoint point : renderState.points()) {
            renderPoint(poseStack, submitNodeCollector, radius, point, renderState.pointSizeMultiplier());
        }

        poseStack.popPose();
    }

    @Override
    protected @NonNull String getTextureLabel() {
        return MediaPlayer.id("sphere_preview").toString();
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    private record SphereVertex(
            float x,
            float y,
            float z,
            float nx,
            float ny,
            float nz,
            float u,
            float v
    ) {
    }
}
