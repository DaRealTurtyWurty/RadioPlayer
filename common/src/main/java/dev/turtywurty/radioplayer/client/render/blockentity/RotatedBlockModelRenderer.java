package dev.turtywurty.radioplayer.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.turtywurty.radioplayer.block.HorizontalDirection8;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

public abstract class RotatedBlockModelRenderer<T extends BlockEntity>
        implements BlockEntityRenderer<T, RotatedBlockModelRenderState> {
    private static final BlockDisplayContext DISPLAY_CONTEXT = BlockDisplayContext.create();

    private final BlockModelResolver blockModelResolver;

    protected RotatedBlockModelRenderer(BlockEntityRendererProvider.Context context) {
        this.blockModelResolver = context.blockModelResolver();
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
        renderState.facing = getFacing(blockState);
        this.blockModelResolver.update(renderState.blockModel, getModelState(blockState), DISPLAY_CONTEXT);
    }

    @Override
    public void submit(@NonNull RotatedBlockModelRenderState renderState, @NonNull PoseStack poseStack,
                       @NonNull SubmitNodeCollector submitNodeCollector,
                       @NonNull CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(renderState.facing.yRotationDegrees()));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        renderState.blockModel.submit(
                poseStack,
                submitNodeCollector,
                renderState.lightCoords,
                OverlayTexture.NO_OVERLAY,
                0
        );
        poseStack.popPose();
    }

    protected abstract HorizontalDirection8 getFacing(BlockState blockState);

    protected abstract BlockState getModelState(BlockState blockState);
}
