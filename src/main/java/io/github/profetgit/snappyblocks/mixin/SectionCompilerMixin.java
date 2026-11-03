package io.github.profetgit.snappyblocks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.profetgit.snappyblocks.pop.Hidden;
import io.github.profetgit.snappyblocks.pop.HiddenRegion;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The mesher's own read of each block: a hidden block is meshed as air, or as its fluid when waterlogged. */
@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
    @WrapOperation(method = "compile", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState snappy_blocks$skipHidden(RenderSectionRegion region, BlockPos pos, Operation<BlockState> original) {
        BlockState s = original.call(region, pos);
        return ((HiddenRegion) region).snappy_blocks$hides(pos.asLong()) ? Hidden.standIn(s) : s;
    }
}
