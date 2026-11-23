package io.github.profetgit.snappyblocks.mixin;

//? if >=26.2 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.profetgit.snappyblocks.pop.PopRenderState;
import io.github.profetgit.snappyblocks.pop.ShaderPack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.feature.MovingBlockFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Marks the tesselation of a block copy under a `separateAo` shader pack, for compat.SodiumNonTerrainAoMixin. */
@Mixin(MovingBlockFeatureRenderer.class)
public abstract class MovingBlockFeatureRendererMixin {
    @WrapOperation(method = "buildGroup", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"))
    private void snappy_blocks$copy(ModelBlockRenderer renderer, BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockPos pos,
                                    BlockState state, BlockStateModel model, long seed, Operation<Void> original) {
        boolean copy = level instanceof PopRenderState;
        ShaderPack.aoCopy = copy && ShaderPack.separateAo();
        if (copy) ShaderPack.tagging(state, pos);
        try {
            original.call(renderer, output, x, y, z, level, pos, state, model, seed);
        } finally {
            ShaderPack.aoCopy = false;
            ShaderPack.tagging(null, null);
        }
    }
}
//?} else if >=26.1 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.profetgit.snappyblocks.pop.PopRenderState;
import io.github.profetgit.snappyblocks.pop.ShaderPack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.feature.BlockFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// 26.1.2: BlockFeatureRenderer draws the moving blocks (26.2 moved that to MovingBlockFeatureRenderer.buildGroup); marks the tesselation of a block copy under a separateAo shader pack
@Mixin(BlockFeatureRenderer.class)
public abstract class MovingBlockFeatureRendererMixin {
    @WrapOperation(method = "renderMovingBlockSubmits", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"))
    private void snappy_blocks$copy(ModelBlockRenderer renderer, BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockPos pos,
                                    BlockState state, BlockStateModel model, long seed, Operation<Void> original) {
        boolean copy = level instanceof PopRenderState;
        ShaderPack.aoCopy = copy && ShaderPack.separateAo();
        if (copy) ShaderPack.tagging(state, pos);
        try {
            original.call(renderer, output, x, y, z, level, pos, state, model, seed);
        } finally {
            ShaderPack.aoCopy = false;
            ShaderPack.tagging(null, null);
        }
    }
}
*///?} else if >=1.21.2 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.profetgit.snappyblocks.pop.PopRenderState;
import io.github.profetgit.snappyblocks.pop.ShaderPack;
import java.util.List;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.feature.BlockFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Marks the tesselation of a block copy under a separateAo shader pack, for compat.SodiumNonTerrainAoMixin (1.21.11: BlockFeatureRenderer draws moving blocks).
@Mixin(BlockFeatureRenderer.class)
public abstract class MovingBlockFeatureRendererMixin {
    // Fabric API's renderer (bundled in Sodium) draws the moving blocks before vanilla's own loop runs, through Sodium's non-terrain context:
    // the flag stays up for the whole draw so that path writes the ambient occlusion as alpha too (compat.SodiumNonTerrainAoMixin)
    @Inject(method = "render", require = 0, at = @At("HEAD"))
    private void snappy_blocks$aoBegin(net.minecraft.client.renderer.SubmitNodeCollection collection, net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,
                                       net.minecraft.client.renderer.block.BlockRenderDispatcher dispatcher, net.minecraft.client.renderer.OutlineBufferSource outline, CallbackInfo ci) {
        ShaderPack.aoCopy = ShaderPack.separateAo();
    }

    @Inject(method = "render", require = 0, at = @At("RETURN"))
    private void snappy_blocks$aoEnd(net.minecraft.client.renderer.SubmitNodeCollection collection, net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,
                                     net.minecraft.client.renderer.block.BlockRenderDispatcher dispatcher, net.minecraft.client.renderer.OutlineBufferSource outline, CallbackInfo ci) {
        ShaderPack.aoCopy = false;
    }

    @WrapOperation(method = "render", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Ljava/util/List;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZI)V"))
    private void snappy_blocks$copy(ModelBlockRenderer renderer, BlockAndTintGetter level, List<BlockModelPart> parts, BlockState state, BlockPos pos, PoseStack ps,
                                    VertexConsumer out, boolean cull, int overlay, Operation<Void> original) {
        ShaderPack.aoCopy = level instanceof PopRenderState && ShaderPack.separateAo();
        try {
            original.call(renderer, level, parts, state, pos, ps, level instanceof PopRenderState ? io.github.profetgit.snappyblocks.pop.AoConsumer.wrap(out) : out, cull, overlay);
        } finally {
            ShaderPack.aoCopy = false;
        }
    }
}
*///?} else {
/*import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

// Placeholder: before 1.21.2 block copies are drawn in PopRenderState.draw (AoConsumer there).
@Mixin(Minecraft.class)
public abstract class MovingBlockFeatureRendererMixin {
}
*///?}
