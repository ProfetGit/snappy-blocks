package io.github.profetgit.snappyblocks.mixin;

//? if fabric || >=1.21.2 {
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

/** Placeholder: the crack-overlay hook below exists only on NeoForge, whose patched renderBreakingTexture takes ModelData. */
@Mixin(Minecraft.class)
public abstract class LevelRendererNeoForgeMixin {
}
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererNeoForgeMixin {
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/neoforged/neoforge/client/model/data/ModelData;)V"))
    private void snappy_blocks$crack(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack ps, VertexConsumer consumer,
        ModelData modelData, Operation<Void> original) {
        Probe.cracks++;
        Pops.transform(ps, pos);
        Pulses.transform(ps, pos);
        original.call(dispatcher, state, pos, level, ps, consumer, modelData);
    }
}
*///?}
