package dev.turtywurty.mediabox.client.render.cable;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.turtywurty.mediabox.block.CablePortBlock;
import dev.turtywurty.mediabox.cable.*;
import dev.turtywurty.mediabox.client.cable.ClientCableState;
import dev.turtywurty.mediabox.client.cable.ClientConcealedCableRouteCache;
import dev.turtywurty.mediabox.client.cable.ClientConcealedCableSegment;
import dev.turtywurty.mediabox.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class CableWorldRenderer {
    private static final double VISIBLE_RENDER_DISTANCE_SQUARED = 160.0 * 160.0;
    private static final double CONCEALED_RENDER_DISTANCE_SQUARED = 96.0 * 96.0;
    private static final int CONCEALED_COLOR = 0xE6FF9D24;
    private static final float CONCEALED_LINE_WIDTH = 3.0F;

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
            for (var run : snapshot.concealedRuns()) {
                for (ClientConcealedCableSegment segment : ClientConcealedCableRouteCache.route(level, run)) {
                    submitConcealedSegment(cameraPos, poseStack, submitNodeCollector, segment);
                }
            }
        }
    }

    private static void submitVisibleCable(
            ClientLevel level,
            Vec3 cameraPos,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            VisibleCableConnection connection) {
        Vec3 first = portPosition(level, connection.first());
        Vec3 second = portPosition(level, connection.second());
        Vec3 midpoint = first.add(second).scale(0.5);
        if (midpoint.distanceToSqr(cameraPos) > VISIBLE_RENDER_DISTANCE_SQUARED)
            return;

        EntityRenderState.LeashState leashState = new EntityRenderState.LeashState();
        leashState.start = first;
        leashState.end = second;
        leashState.offset = Vec3.ZERO;
        leashState.startBlockLight = level.getBrightness(LightLayer.BLOCK, connection.first().pos());
        leashState.endBlockLight = level.getBrightness(LightLayer.BLOCK, connection.second().pos());
        leashState.startSkyLight = level.getBrightness(LightLayer.SKY, connection.first().pos());
        leashState.endSkyLight = level.getBrightness(LightLayer.SKY, connection.second().pos());
        leashState.slack = connection.slack() > 0.0F;

        poseStack.pushPose();
        poseStack.translate(first.x - cameraPos.x, first.y - cameraPos.y, first.z - cameraPos.z);
        submitNodeCollector.submitLeash(poseStack, leashState);
        poseStack.popPose();
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

    private static Vec3 portPosition(ClientLevel level, PortEndpoint endpoint) {
        Vec3 center = Vec3.atCenterOf(endpoint.pos());
        Optional<ResolvedMediaPort> resolved = MediaPortLookup.resolve(level, endpoint);
        if (resolved.isEmpty())
            return center;

        var state = level.getBlockState(endpoint.pos());
        if (state.getBlock() instanceof CablePortBlock)
            return CablePortBlock.portPosition(endpoint.pos(), state);

        Direction face = resolved.get().port().face();
        return center.add(
                face.getStepX() * 0.51,
                face.getStepY() * 0.51,
                face.getStepZ() * 0.51);
    }

    private static boolean isCableItemHeld(Minecraft minecraft) {
        return minecraft.player.getMainHandItem().getItem() == ModItems.audioCable.asItem()
                || minecraft.player.getOffhandItem().getItem() == ModItems.audioCable.asItem();
    }
}
