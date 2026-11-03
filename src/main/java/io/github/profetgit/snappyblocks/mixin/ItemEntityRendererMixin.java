package io.github.profetgit.snappyblocks.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.item.ItemFx;
import io.github.profetgit.snappyblocks.item.ItemPose;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.2 {
import io.github.profetgit.snappyblocks.item.ItemFxState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
*///?}

/** Render-only offsets and the landing squash of an item; the entity's position is never touched (PRD §5.4). */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
//? if >=1.21.2 {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("RETURN"))
    private void snappy_blocks$offset(ItemEntity entity, ItemEntityRenderState state, float partial, CallbackInfo ci) {
        long t0 = System.nanoTime();
        ItemPose pose = ItemPose.SHARED;
        pose.x = state.x;
        pose.y = state.y;
        pose.z = state.z;
        Bursts.offset(entity.getId(), pose);
        ItemFx.extract(entity, pose);
        state.x = pose.x;
        state.y = pose.y;
        state.z = pose.z;
        ((ItemFxState) state).snappy_blocks$squash(pose.sxz, pose.sy);
        ItemFx.spentNanos += System.nanoTime() - t0;
    }

    /** The squash scales about the item's feet, which sit on the ground. */
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
    private void snappy_blocks$squash(ItemEntityRenderState state, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        ItemFxState fx = (ItemFxState) state;
        ps.pushPose();
        if (fx.snappy_blocks$sxz() != 1 || fx.snappy_blocks$sy() != 1) ps.scale(fx.snappy_blocks$sxz(), fx.snappy_blocks$sy(), fx.snappy_blocks$sxz());
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("RETURN"))
    private void snappy_blocks$unsquash(ItemEntityRenderState state, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        ps.popPose();
    }
//?} else {
/*    // no render states before 1.21.2: the offset is a translation of the pose, worked out where the draw starts
    @Inject(method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void snappy_blocks$squash(ItemEntity entity, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light, CallbackInfo ci) {
        long t0 = System.nanoTime();
        ItemPose pose = ItemPose.SHARED;
        double x = Mth.lerp(partial, entity.xo, entity.getX()), y = Mth.lerp(partial, entity.yo, entity.getY()), z = Mth.lerp(partial, entity.zo, entity.getZ());
        pose.x = x;
        pose.y = y;
        pose.z = z;
        Bursts.offset(entity.getId(), pose);
        ItemFx.extract(entity, pose);
        ItemFx.spentNanos += System.nanoTime() - t0;
        ps.pushPose();
        ps.translate(pose.x - x, pose.y - y, pose.z - z);
        if (pose.sxz != 1 || pose.sy != 1) ps.scale(pose.sxz, pose.sy, pose.sxz);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"))
    private void snappy_blocks$unsquash(ItemEntity entity, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light, CallbackInfo ci) {
        ps.popPose();
    }
*///?}
}
