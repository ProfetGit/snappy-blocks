package io.github.profetgit.snappyblocks.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.profetgit.snappyblocks.pop.Hidden;
import io.github.profetgit.snappyblocks.pop.SodiumHidden;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Embeddium's face culling (Sodium 0.5 fork): a face against a hidden block is drawn, as SodiumOcclusionCacheMixin does for 0.8. */
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.BlockOcclusionCache", remap = false)
public abstract class EmbeddiumOcclusionMixin {
    @WrapOperation(method = "shouldDrawSide", remap = false, require = 0, at = @At(value = "INVOKE", remap = true,
        target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState snappy_blocks$neighbour(BlockGetter level, BlockPos pos, Operation<BlockState> original) {
        BlockState s = original.call(level, pos);
        return SodiumHidden.hides(pos.asLong()) ? Hidden.standIn(s) : s;
    }
}
