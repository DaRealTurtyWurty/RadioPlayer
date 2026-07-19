package dev.turtywurty.mediabox.client.render.cable;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.turtywurty.mediabox.cable.*;
import dev.turtywurty.mediabox.client.cable.ClientCableState;
import dev.turtywurty.mediabox.client.cable.ClientConcealedCableRouteCache;
import dev.turtywurty.mediabox.client.cable.ClientConcealedCableSegment;
import dev.turtywurty.mediabox.client.cable.ClientVisibleCablePreview;
import dev.turtywurty.mediabox.client.cable.ClientVisibleCableRouteCache;
import dev.turtywurty.mediabox.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

public final class CableWorldRenderer {
    private static final double VISIBLE_RENDER_DISTANCE_SQUARED = 160.0 * 160.0;
    private static final double CONCEALED_RENDER_DISTANCE_SQUARED = 96.0 * 96.0;
    private static final int CONCEALED_COLOR = 0xE6FF9D24;
    private static final int VALID_PREVIEW_COLOR = 0xE640FF63;
    private static final int INVALID_PREVIEW_COLOR = 0xE6FF4040;
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

        if (isCableItemHeld(minecraft)) {
            ClientVisibleCablePreview.get(minecraft).ifPresent(preview ->
                    submitPreview(cameraPos, poseStack, submitNodeCollector, preview));
            for (var run : snapshot.concealedRuns()) {
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

        for (int index = 1; index < points.size(); index++) {
            Vec3 first = points.get(index - 1);
            Vec3 second = points.get(index);
            BlockPos firstLightPos = BlockPos.containing(first.x, first.y, first.z);
            BlockPos secondLightPos = BlockPos.containing(second.x, second.y, second.z);
            EntityRenderState.LeashState leashState = new EntityRenderState.LeashState();
            leashState.start = first;
            leashState.end = second;
            leashState.offset = Vec3.ZERO;
            leashState.startBlockLight = level.getBrightness(LightLayer.BLOCK, firstLightPos);
            leashState.endBlockLight = level.getBrightness(LightLayer.BLOCK, secondLightPos);
            leashState.startSkyLight = level.getBrightness(LightLayer.SKY, firstLightPos);
            leashState.endSkyLight = level.getBrightness(LightLayer.SKY, secondLightPos);
            leashState.slack = false;

            poseStack.pushPose();
            poseStack.translate(first.x - cameraPos.x, first.y - cameraPos.y, first.z - cameraPos.z);
            submitNodeCollector.submitLeash(poseStack, leashState);
            poseStack.popPose();
        }
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

    private static boolean isCableItemHeld(Minecraft minecraft) {
        return minecraft.player.getMainHandItem().getItem() == ModItems.audioCable.asItem()
                || minecraft.player.getOffhandItem().getItem() == ModItems.audioCable.asItem();
    }
}
