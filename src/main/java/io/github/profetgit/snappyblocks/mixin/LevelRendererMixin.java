package io.github.profetgit.snappyblocks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.pop.Pops;
import io.github.profetgit.snappyblocks.pulse.Pulses;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    /** The vanilla hand-off signal: a section mesh whose compile started at this time is now live. */
    @Inject(method = "removeTransientBlocksInSection", at = @At("HEAD"))
    private void snappy_blocks$meshLive(long sectionNode, long compileTaskStartTimeNs, CallbackInfo ci) {
        Pops.meshLive(sectionNode, compileTaskStartTimeNs);
        Bursts.meshLive(sectionNode, compileTaskStartTimeNs);
    }

    @Inject(method = "submitTransientBlocks", at = @At("TAIL"))
    private void snappy_blocks$submit(PoseStack ps, SubmitNodeCollector collector, LevelRenderState state, CallbackInfo ci) {
        Pops.submit(ps, collector, state);
        Pulses.submit(ps, collector, state);
        Bursts.submit(ps, collector, state);
    }

    /** A block entity drawn only by its renderer (chest, sign, banner) pops through the renderer's pose. */
    @WrapOperation(method = "submitBlockEntities", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private void snappy_blocks$blockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntityRenderState state, PoseStack ps, SubmitNodeCollector collector,
        CameraRenderState camera, Operation<Void> original) {
        Pops.transform(ps, state.blockPos);
        original.call(dispatcher, state, ps, collector, camera);
    }

    /** The crack overlay follows the block: popping, or pulsing under the pickaxe. */
    @WrapOperation(method = "submitBlockDestroyAnimation", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(Lnet/minecraft/world/phys/Vec3;)V"))
    private void snappy_blocks$crack(PoseStack ps, Vec3 offset, Operation<Void> original, @Local BlockPos pos) {
        Pops.transform(ps, pos);
        Pulses.transform(ps, pos);
        original.call(ps, offset);
    }

    /** The selection outline follows the block too. */
    @WrapOperation(method = "submitBlockOutline", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private void snappy_blocks$outline(PoseStack ps, double x, double y, double z, Operation<Void> original, @Local BlockPos pos) {
        original.call(ps, x, y, z);
        Pops.transform(ps, pos);
        Pulses.transform(ps, pos);
    }
}
