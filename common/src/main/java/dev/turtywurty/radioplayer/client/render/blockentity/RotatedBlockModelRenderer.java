package dev.turtywurty.radioplayer.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.turtywurty.radioplayer.block.HorizontalDirection8;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

public abstract class RotatedBlockModelRenderer<T extends BlockEntity>
        implements BlockEntityRenderer<T, RotatedBlockModelRenderState> {
    protected RotatedBlockModelRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public @NonNull RotatedBlockModelRenderState createRenderState() {
        return new RotatedBlockModelRenderState();
    }

    @Override
    public void extractRenderState(@NonNull T blockEntity, @NonNull RotatedBlockModelRenderState renderState,
                                   float partialTick, @NonNull Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderState.extractBase(blockEntity, renderState, crumblingOverlay);

        BlockState blockState = blockEntity.getBlockState();
        renderState.renderState = getModelState(blockState);
        renderState.facing = getFacing(blockState);

        renderState.movingBlockRenderState.randomSeedPos = blockEntity.getBlockPos();
        renderState.movingBlockRenderState.blockPos = blockEntity.getBlockPos();
        renderState.movingBlockRenderState.blockState = renderState.renderState;

        if (blockEntity.getLevel() instanceof ClientLevel level) {
            renderState.movingBlockRenderState.biome = level.getBiome(blockEntity.getBlockPos());
            renderState.movingBlockRenderState.cardinalLighting = level.cardinalLighting();
            renderState.movingBlockRenderState.lightEngine = level.getLightEngine();
        }
    }

    @Override
    public void submit(@NonNull RotatedBlockModelRenderState renderState, @NonNull PoseStack poseStack,
                       @NonNull SubmitNodeCollector submitNodeCollector,
                       @NonNull CameraRenderState cameraRenderState) {
        if (renderState.renderState == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(renderState.facing.yRotationDegrees()));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        submitNodeCollector.submitMovingBlock(poseStack, renderState.movingBlockRenderState, 0);
        poseStack.popPose();
    }

    protected abstract HorizontalDirection8 getFacing(BlockState blockState);

    protected abstract BlockState getModelState(BlockState blockState);
}
