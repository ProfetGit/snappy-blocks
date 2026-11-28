package io.github.profetgit.snappyblocks.mixin.compat;

import io.github.profetgit.snappyblocks.pop.Hidden;
import io.github.profetgit.snappyblocks.pop.SodiumHidden;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * More Culling decides Sodium's face culling itself (it returns from shouldDrawSide before Sodium reads the neighbour),
 * so SodiumBlockRenderContextMixin never sees the read: a face against a hidden block is drawn here too, or the faces
 * round a growing block are culled and the world shows through.
 */
@Pseudo
@Mixin(targets = "ca.fxco.moreculling.utils.CullingUtils", remap = false)
public abstract class MoreCullingMixin {
    @Inject(method = "shouldDrawSideCulling", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void snappy_blocks$hidden(BlockState thisState, BlockState sideState, BlockGetter world, BlockPos thisPos, Direction side,
        BlockPos sidePos, CallbackInfoReturnable<Boolean> cir) {
        if (SodiumHidden.hides(sidePos.asLong())) cir.setReturnValue(!thisState.skipRendering(Hidden.standIn(sideState), side));
    }
}
