package io.github.profetgit.snappyblocks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.profetgit.snappyblocks.Probe;
import io.github.profetgit.snappyblocks.pop.Pops;
import io.github.profetgit.snappyblocks.pulse.Pulses;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Forge's patched renderBreakingTexture takes ModelData; this side project adds the class to the mixin config (build.gradle). */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererForgeMixin {
    @WrapOperation(method = "renderLevel", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraftforge/client/model/data/ModelData;)V"))
    private void snappy_blocks$crack(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack ps, VertexConsumer consumer,
        ModelData modelData, Operation<Void> original) {
        Probe.cracks++;
        Pops.transform(ps, pos);
        Pulses.transform(ps, pos);
        original.call(dispatcher, state, pos, level, ps, consumer, modelData);
    }
}
