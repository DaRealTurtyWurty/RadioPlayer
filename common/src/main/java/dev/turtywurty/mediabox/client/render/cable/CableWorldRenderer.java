package dev.turtywurty.mediabox.client.render.cable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.turtywurty.mediabox.cable.*;
import dev.turtywurty.mediabox.client.cable.ClientCableState;
import dev.turtywurty.mediabox.client.cable.ClientConcealedCableRouteCache;
import dev.turtywurty.mediabox.client.cable.ClientConcealedCableSegment;
import dev.turtywurty.mediabox.client.cable.ClientVisibleCablePreview;
import dev.turtywurty.mediabox.client.cable.ClientVisibleCableRouteCache;
import dev.turtywurty.mediabox.item.CableItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class CableWorldRenderer {
    private static final double VISIBLE_RENDER_DISTANCE_SQUARED = 160.0 * 160.0;
    private static final double CONCEALED_RENDER_DISTANCE_SQUARED = 96.0 * 96.0;
    private static final int CONCEALED_COLOR = 0xE6FF9D24;
    private static final int VALID_PREVIEW_COLOR = 0xE640FF63;
    private static final int INVALID_PREVIEW_COLOR = 0xE6FF4040;
    private static final int VISIBLE_CABLE_STEPS = 24;
    private static final float VISIBLE_CABLE_WIDTH = 0.05F;
    private static final float CONCEALED_LINE_WIDTH = 3.0F;
    private static final float PREVIEW_LINE_WIDTH = 4.0F;

    private CableWorldRenderer() {
    }

    public static void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null)
            return;

        ClientCableState.Snapshot snapshot = ClientCableState.snapshot();
        if (snapshot.dimension() == null || !snapshot.dimension().equals(level.dimension()))
            return;

        Vec3 cameraPos = minecraft.gameRenderer.mainCamera().position();
        for (VisibleCableConnection connection : snapshot.visibleConnections()) {
            submitVisibleCable(level, cameraPos, poseStack, submitNodeCollector, connection);
        }

        Optional<MediaSignalType> heldSignalType = heldCableSignalType(minecraft);
        if (heldSignalType.isPresent()) {
            ClientVisibleCablePreview.get(minecraft).ifPresent(preview ->
                    submitPreview(cameraPos, poseStack, submitNodeCollector, preview));
            for (var run : snapshot.concealedRuns()) {
                if (run.signalType() != heldSignalType.get())
                    continue;

                for (ClientConcealedCableSegment segment : ClientConcealedCableRouteCache.route(level, run)) {
                    submitConcealedSegment(cameraPos, poseStack, submitNodeCollector, segment);
                }
            }
        }
    }

    private static void submitPreview(
            Vec3 cameraPos,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            ClientVisibleCablePreview.Preview preview) {
        int color = preview.valid() ? VALID_PREVIEW_COLOR : INVALID_PREVIEW_COLOR;
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                CableRenderTypes.previewLines(),
                (pose, buffer) -> {
                    var points = preview.route().points();
                    for (int index = 1; index < points.size(); index++) {
                        Vec3 first = points.get(index - 1);
                        Vec3 second = points.get(index);
                        Vec3 normal = second.subtract(first).normalize();
                        buffer.addVertex(pose, (float) first.x, (float) first.y, (float) first.z)
                                .setColor(color)
                                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z)
                                .setLineWidth(PREVIEW_LINE_WIDTH);
                        buffer.addVertex(pose, (float) second.x, (float) second.y, (float) second.z)
                                .setColor(color)
                                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z)
                                .setLineWidth(PREVIEW_LINE_WIDTH);
                    }
                });
        poseStack.popPose();
    }

    private static void submitVisibleCable(
            ClientLevel level,
            Vec3 cameraPos,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            VisibleCableConnection connection) {
        var route = ClientVisibleCableRouteCache.route(level, connection);
        if (route.isEmpty())
            return;

        var points = route.get().points();
        Vec3 midpoint = points.get(points.size() / 2);
        if (midpoint.distanceToSqr(cameraPos) > VISIBLE_RENDER_DISTANCE_SQUARED)
            return;

        VisibleCablePalette palette = VisibleCablePalette.forSignalType(connection.signalType());
        for (int index = 1; index < points.size(); index++) {
            Vec3 first = points.get(index - 1);
            Vec3 second = points.get(index);
            BlockPos firstLightPos = BlockPos.containing(first.x, first.y, first.z);
            BlockPos secondLightPos = BlockPos.containing(second.x, second.y, second.z);
            int startBlockLight = level.getBrightness(LightLayer.BLOCK, firstLightPos);
            int endBlockLight = level.getBrightness(LightLayer.BLOCK, secondLightPos);
            int startSkyLight = level.getBrightness(LightLayer.SKY, firstLightPos);
            int endSkyLight = level.getBrightness(LightLayer.SKY, secondLightPos);
            int color = palette.stripe(index - 1);
            Vec3 offset = second.subtract(first);

            poseStack.pushPose();
            poseStack.translate(first.x - cameraPos.x, first.y - cameraPos.y, first.z - cameraPos.z);
            submitNodeCollector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.leash(),
                    (pose, buffer) -> renderVisibleCableSegment(
                            pose,
                            buffer,
                            offset,
                            startBlockLight,
                            endBlockLight,
                            startSkyLight,
                            endSkyLight,
                            color));
            poseStack.popPose();
        }
    }

    private static void renderVisibleCableSegment(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            Vec3 offset,
            int startBlockLight,
            int endBlockLight,
            int startSkyLight,
            int endSkyLight,
            int color) {
        float x = (float) offset.x;
        float y = (float) offset.y;
        float z = (float) offset.z;
        float horizontalLengthSquared = x * x + z * z;
        float perpendicularScale = horizontalLengthSquared > 0.0F
                ? Mth.invSqrt(horizontalLengthSquared) * VISIBLE_CABLE_WIDTH * 0.5F
                : VISIBLE_CABLE_WIDTH * 0.5F;
        float perpendicularX = horizontalLengthSquared > 0.0F ? z * perpendicularScale : perpendicularScale;
        float perpendicularZ = horizontalLengthSquared > 0.0F ? x * perpendicularScale : 0.0F;

        for (int step = 0; step <= VISIBLE_CABLE_STEPS; step++) {
            addVisibleCableVertexPair(
                    pose,
                    buffer,
                    x,
                    y,
                    z,
                    VISIBLE_CABLE_WIDTH,
                    perpendicularX,
                    perpendicularZ,
                    step,
                    startBlockLight,
                    endBlockLight,
                    startSkyLight,
                    endSkyLight,
                    color);
        }
        for (int step = VISIBLE_CABLE_STEPS; step >= 0; step--) {
            addVisibleCableVertexPair(
                    pose,
                    buffer,
                    x,
                    y,
                    z,
                    0.0F,
                    perpendicularX,
                    perpendicularZ,
                    step,
                    startBlockLight,
                    endBlockLight,
                    startSkyLight,
                    endSkyLight,
                    color);
        }
    }

    private static void addVisibleCableVertexPair(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float x,
            float y,
            float z,
            float yOffset,
            float perpendicularX,
            float perpendicularZ,
            int step,
            int startBlockLight,
            int endBlockLight,
            int startSkyLight,
            int endSkyLight,
            int color) {
        float progress = step / (float) VISIBLE_CABLE_STEPS;
        int blockLight = (int) Mth.lerp(progress, startBlockLight, endBlockLight);
        int skyLight = (int) Mth.lerp(progress, startSkyLight, endSkyLight);
        int light = LightCoordsUtil.pack(blockLight, skyLight);
        float pointX = x * progress;
        float pointY = y * progress;
        float pointZ = z * progress;

        buffer.addVertex(
                        pose,
                        pointX - perpendicularX,
                        pointY + yOffset,
                        pointZ + perpendicularZ)
                .setColor(color)
                .setLight(light);
        buffer.addVertex(
                        pose,
                        pointX + perpendicularX,
                        pointY + VISIBLE_CABLE_WIDTH - yOffset,
                        pointZ - perpendicularZ)
                .setColor(color)
                .setLight(light);
    }

    private static void submitConcealedSegment(
            Vec3 cameraPos,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            ClientConcealedCableSegment segment) {
        Vec3 center = Vec3.atCenterOf(segment.pos());
        if (center.distanceToSqr(cameraPos) > CONCEALED_RENDER_DISTANCE_SQUARED)
            return;

        poseStack.pushPose();
        poseStack.translate(center.x - cameraPos.x, center.y - cameraPos.y, center.z - cameraPos.z);
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                CableRenderTypes.xrayLines(),
                (pose, buffer) -> {
                    for (Direction direction : segment.connections()) {
                        float x = direction.getStepX() * 0.5F;
                        float y = direction.getStepY() * 0.5F;
                        float z = direction.getStepZ() * 0.5F;
                        buffer.addVertex(pose, 0.0F, 0.0F, 0.0F)
                                .setColor(CONCEALED_COLOR)
                                .setNormal(pose, direction.getStepX(), direction.getStepY(), direction.getStepZ())
                                .setLineWidth(CONCEALED_LINE_WIDTH);
                        buffer.addVertex(pose, x, y, z)
                                .setColor(CONCEALED_COLOR)
                                .setNormal(pose, direction.getStepX(), direction.getStepY(), direction.getStepZ())
                                .setLineWidth(CONCEALED_LINE_WIDTH);
                    }
                });
        poseStack.popPose();
    }

    private static Optional<MediaSignalType> heldCableSignalType(Minecraft minecraft) {
        ItemStack mainHand = minecraft.player.getMainHandItem();
        if (mainHand.getItem() instanceof CableItem cableItem)
            return Optional.of(cableItem.signalType());

        ItemStack offHand = minecraft.player.getOffhandItem();
        if (offHand.getItem() instanceof CableItem cableItem)
            return Optional.of(cableItem.signalType());

        return Optional.empty();
    }
}
