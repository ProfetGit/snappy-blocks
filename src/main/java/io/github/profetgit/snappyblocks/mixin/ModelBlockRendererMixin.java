package io.github.profetgit.snappyblocks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.profetgit.snappyblocks.pop.Hidden;
import io.github.profetgit.snappyblocks.pop.HiddenRegion;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Face culling in a mesh region: a face against a hidden block is drawn, so nothing behind a growing block is missing. */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    @WrapOperation(method = "shouldRenderFace", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/BlockAndTintGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState snappy_blocks$neighbour(BlockAndTintGetter level, BlockPos pos, Operation<BlockState> original) {
        BlockState s = original.call(level, pos);
        return level instanceof HiddenRegion h && h.snappy_blocks$hides(pos.asLong()) ? Hidden.standIn(s) : s;
    }
}
