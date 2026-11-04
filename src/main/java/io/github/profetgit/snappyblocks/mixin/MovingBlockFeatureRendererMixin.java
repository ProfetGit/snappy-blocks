package io.github.profetgit.snappyblocks.mixin;

//? if >=1.21.2 {
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
        ShaderPack.aoCopy = level instanceof PopRenderState && ShaderPack.separateAo();
        try {
            original.call(renderer, output, x, y, z, level, pos, state, model, seed);
        } finally {
            ShaderPack.aoCopy = false;
        }
    }
}
//?} else {
/*import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

// Placeholder: before 1.21.2 block copies are drawn in PopRenderState.draw (AoConsumer there).
@Mixin(Minecraft.class)
public abstract class MovingBlockFeatureRendererMixin {
}
*///?}
