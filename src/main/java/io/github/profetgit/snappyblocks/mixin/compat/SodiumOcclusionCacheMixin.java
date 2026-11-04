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

/** Face culling in the Sodium builds that decide it in BlockOcclusionCache (1.21.1): a face against a hidden block is drawn. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache", remap = false)
public abstract class SodiumOcclusionCacheMixin {
    @WrapOperation(method = "shouldDrawSide", remap = false, at = @At(value = "INVOKE", remap = false,
        target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState snappy_blocks$neighbour(BlockGetter level, BlockPos pos, Operation<BlockState> original) {
        BlockState s = original.call(level, pos);
        return SodiumHidden.hides(pos.asLong()) ? Hidden.standIn(s) : s;
    }
}
