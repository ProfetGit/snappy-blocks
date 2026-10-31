package io.github.profetgit.snappyblocks.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.item.ItemFx;
import io.github.profetgit.snappyblocks.item.ItemFxState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render-only offsets and the landing squash of an item; the entity's position is never touched (PRD §5.4). */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("RETURN"))
    private void snappy_blocks$offset(ItemEntity entity, ItemEntityRenderState state, float partial, CallbackInfo ci) {
        long t0 = System.nanoTime();
        Bursts.offset(entity.getId(), state);
        ItemFx.extract(entity, state);
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
}
