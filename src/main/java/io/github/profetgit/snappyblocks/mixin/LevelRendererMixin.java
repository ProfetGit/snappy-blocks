package io.github.profetgit.snappyblocks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.snappyblocks.RenderPass;
import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.item.Ghosts;
import io.github.profetgit.snappyblocks.pop.Pops;
import io.github.profetgit.snappyblocks.pulse.Pulses;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.2 {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
//?} else {
/*import io.github.profetgit.snappyblocks.Probe;
import io.github.profetgit.snappyblocks.pop.Hidden;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
*///?}

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
//? if >=1.21.2 {
    /** The vanilla hand-off signal: a section mesh whose compile started at this time is now live. */
    @Inject(method = "removeTransientBlocksInSection", at = @At("HEAD"))
    private void snappy_blocks$meshLive(long sectionNode, long compileTaskStartTimeNs, CallbackInfo ci) {
        Pops.meshLive(sectionNode, compileTaskStartTimeNs);
        Bursts.meshLive(sectionNode, compileTaskStartTimeNs);
    }

    @Inject(method = "submitTransientBlocks", at = @At("TAIL"))
    private void snappy_blocks$submit(PoseStack ps, SubmitNodeCollector collector, LevelRenderState state, CallbackInfo ci) {
        RenderPass pass = new RenderPass(ps, collector, state);
        Pops.submit(pass);
        Pulses.submit(pass);
        Bursts.submit(pass);
        long t0 = System.nanoTime();
        Ghosts.submit(pass);
        Ghosts.spentNanos += System.nanoTime() - t0;
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
//?} else {
/*    // no transient blocks before 1.21.2: a section mesh is live when its upload is done (setCompiled -> here)
    @Inject(method = "addRecentlyCompiledSection", at = @At("HEAD"))
    private void snappy_blocks$meshLive(SectionRenderDispatcher.RenderSection section, CallbackInfo ci) {
        long node = SectionPos.asLong(section.getOrigin());
        long start = Hidden.compileStart(node);
        Pops.meshLive(node, start);
        Bursts.meshLive(node, start);
    }

    @Inject(method = "levelEvent", at = @At("HEAD"))
    private void snappy_blocks$event(int type, BlockPos pos, int data, CallbackInfo ci) {
        Probe.levelEvent(type, pos, data);
        Bursts.levelEvent(type, pos, data);
    }

    // after the entities, before their buffers are flushed: the copies, items in flight and the block-entity pops share vanilla's batches
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", ordinal = 0,
        target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V"))
    private void snappy_blocks$submit(DeltaTracker delta, boolean outline, Camera camera, GameRenderer renderer, LightTexture light,
        Matrix4f view, Matrix4f projection, CallbackInfo ci, @Local PoseStack ps, @Local MultiBufferSource.BufferSource buffers) {
        RenderPass pass = new RenderPass(ps, buffers, camera.getPosition(), projection, view);
        Pops.submit(pass);
        Pulses.submit(pass);
        Bursts.submit(pass);
        long t0 = System.nanoTime();
        Ghosts.submit(pass);
        Ghosts.spentNanos += System.nanoTime() - t0;
        pass.flush();
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"))
    private void snappy_blocks$blockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntity entity, float partial, PoseStack ps, MultiBufferSource buffers,
        Operation<Void> original) {
        Pops.transform(ps, entity.getBlockPos());
        original.call(dispatcher, entity, partial, ps, buffers);
    }

    @WrapOperation(method = "renderLevel", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void snappy_blocks$crack(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack ps, VertexConsumer consumer,
        Operation<Void> original) {
        Pops.transform(ps, pos);
        Pulses.transform(ps, pos);
        original.call(dispatcher, state, pos, level, ps, consumer);
    }

    // renderHitOutline takes the block's offset from the camera as arguments, not from the pose: scale about the block there
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;renderHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void snappy_blocks$outline(LevelRenderer self, PoseStack ps, VertexConsumer consumer, Entity entity, double x, double y, double z, BlockPos pos, BlockState state,
        Operation<Void> original) {
        double dx = pos.getX() - x, dy = pos.getY() - y, dz = pos.getZ() - z;
        ps.pushPose();
        ps.translate(dx, dy, dz);
        Pops.transform(ps, pos);
        Pulses.transform(ps, pos);
        ps.translate(-dx, -dy, -dz);
        original.call(self, ps, consumer, entity, x, y, z, pos, state);
        ps.popPose();
    }
*///?}
}
