package dev.turtywurty.mediabox.client.render.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.turtywurty.mediabox.MediaBox;
import dev.turtywurty.mediabox.client.screen.ClientScreenState;
import dev.turtywurty.mediabox.client.video.ClientScreenPlaybackState;
import dev.turtywurty.mediabox.client.video.ClientVideoManager;
import dev.turtywurty.mediabox.client.video.ClientVideoSession;
import dev.turtywurty.mediabox.screen.ScreenAssembly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ScreenWorldRenderer {
    private ScreenWorldRenderer() {
    }

    private static final RenderType CALIBRATION_CARD = RenderTypes.entityCutout(
            MediaBox.id("textures/screen/test_card.png"),
            false
    );

    public static void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null)
            return;

        ClientScreenState.Snapshot snapshot = ClientScreenState.snapshot();
        if (snapshot.dimension() == null || !snapshot.dimension().equals(level.dimension()))
            return;

        Vec3 cameraPos = minecraft.gameRenderer.mainCamera().position();
        Set<UUID> visibleSessions = new HashSet<>();
        for (ScreenAssembly screenAssembly : snapshot.assemblies().values()) {
            if (!isVisible(minecraft, screenAssembly))
                continue;
            ClientScreenPlaybackState.get(screenAssembly.id())
                    .ifPresent(state -> visibleSessions.add(state.sessionId()));
        }
        ClientVideoManager.uploadLatestFrames(visibleSessions);

        for (ScreenAssembly screenAssembly : snapshot.assemblies().values()) {
            submitScreenAssembly(level, cameraPos, poseStack, submitNodeCollector, screenAssembly);
        }
    }

    private static boolean isVisible(Minecraft minecraft, ScreenAssembly assembly) {
        BlockPos opposite = assembly.origin()
                .relative(assembly.right(), assembly.width() - 1)
                .above(assembly.height() - 1);
        AABB bounds = AABB.encapsulatingFullBlocks(assembly.origin(), opposite).inflate(0.05);
        double maximumDistance = minecraft.options.getEffectiveRenderDistance() * 16.0
                + Math.max(assembly.width(), assembly.height());
        return bounds.distanceToSqr(minecraft.gameRenderer.mainCamera().position())
                <= maximumDistance * maximumDistance
                && minecraft.gameRenderer.mainCamera().getCullFrustum().isVisible(bounds);
    }

    private static void submitScreenAssembly(ClientLevel level, Vec3 cameraPos, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, ScreenAssembly screenAssembly) {
        if (!screenAssembly.rectangular())
            return;

        Direction facing = screenAssembly.facing();
        Direction right = screenAssembly.right();

        double frontOffset = -(0.5D - (2.0D / 16.0D)) + 0.001D;
        Vec3 bottomLeft = Vec3.atCenterOf(screenAssembly.origin()).add(
                facing.getStepX() * frontOffset - right.getStepX() * 0.5D,
                -0.5D,
                facing.getStepZ() * frontOffset - right.getStepZ() * 0.5D
        );

        float rightX = right.getStepX() * screenAssembly.width();
        float rightZ = right.getStepZ() * screenAssembly.width();
        float height = screenAssembly.height();

        poseStack.pushPose();
        poseStack.translate(
                bottomLeft.x - cameraPos.x,
                bottomLeft.y - cameraPos.y,
                bottomLeft.z - cameraPos.z
        );

        submitNodeCollector.submitCustomGeometry(
                poseStack,
                screenRenderType(screenAssembly),
                (pose, consumer) -> {
                    // Top-left
                    consumer.addVertex(pose.pose(), 0.0F, height, 0.0F)
                            .setColor(0xFFFFFFFF)
                            .setUv(0.0F, 0.0F)
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(LightCoordsUtil.FULL_BRIGHT)
                            .setNormal(
                                    pose,
                                    facing.getStepX(),
                                    facing.getStepY(),
                                    facing.getStepZ()
                            );

                    // Bottom-left
                    consumer.addVertex(pose.pose(), 0.0F, 0.0F, 0.0F)
                            .setColor(0xFFFFFFFF)
                            .setUv(0.0F, 1.0F)
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(LightCoordsUtil.FULL_BRIGHT)
                            .setNormal(
                                    pose,
                                    facing.getStepX(),
                                    facing.getStepY(),
                                    facing.getStepZ()
                            );

                    // Bottom-right
                    consumer.addVertex(pose.pose(), rightX, 0.0F, rightZ)
                            .setColor(0xFFFFFFFF)
                            .setUv(1.0F, 1.0F)
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(LightCoordsUtil.FULL_BRIGHT)
                            .setNormal(
                                    pose,
                                    facing.getStepX(),
                                    facing.getStepY(),
                                    facing.getStepZ()
                            );

                    // Top-right
                    consumer.addVertex(pose.pose(), rightX, height, rightZ)
                            .setColor(0xFFFFFFFF)
                            .setUv(1.0F, 0.0F)
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(LightCoordsUtil.FULL_BRIGHT)
                            .setNormal(
                                    pose,
                                    facing.getStepX(),
                                    facing.getStepY(),
                                    facing.getStepZ()
                            );
                }
        );

        poseStack.popPose();
    }

    private static RenderType screenRenderType(ScreenAssembly assembly) {
        return ClientScreenPlaybackState.get(assembly.id())
                .flatMap(assignment -> ClientVideoManager.get(assignment.sessionId()))
                .filter(ClientVideoSession::isReady)
                .map(ClientVideoSession::renderType)
                .orElse(CALIBRATION_CARD);
    }
}
