package io.github.profetgit.snappyblocks.mixin.compat;

import io.github.profetgit.snappyblocks.pop.Hidden;
import io.github.profetgit.snappyblocks.pop.HiddenHolder;
import io.github.profetgit.snappyblocks.pop.SodiumHidden;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Embeddium (Sodium 0.5 fork, package org.embeddedt.embeddium, 1.21.1 NeoForge): the meshing task takes the hidden positions when it is made and publishes
 * them to its worker while it runs (EmbeddiumOcclusionMixin's face culling reads them). The mesher's own read of each block
 * gets the stand-in, so a hidden block is left out; light and AO reads keep the real block, so the ground next to it keeps
 * its shading. No Sodium types in the handlers, so no stubs.
 */
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderMeshingTask", remap = false)
public abstract class EmbeddiumMeshingTaskMixin implements HiddenHolder {
    @Unique
    private LongSet snappy_blocks$hidden;

    @Override
    public void snappy_blocks$setHidden(LongSet hidden) {
        snappy_blocks$hidden = hidden;
        SodiumHidden.wired = true;
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void snappy_blocks$capture(CallbackInfo ci) {
        snappy_blocks$setHidden(Hidden.snapshot());
    }

    @Inject(method = MESH, at = @At("HEAD"), remap = false)
    private void snappy_blocks$begin(CallbackInfoReturnable<?> cir) {
        SodiumHidden.CURRENT.set(snappy_blocks$hidden);
    }

    @Inject(method = MESH, at = @At("RETURN"), remap = false)
    private void snappy_blocks$end(CallbackInfoReturnable<?> cir) {
        SodiumHidden.CURRENT.remove();
    }

    // the block the mesher is about to draw (local `blockState` after slice.getBlockState(x, y, z); ints in scope: minX..maxZ, y, z, x)
    @ModifyVariable(method = MESH, at = @At("STORE"), ordinal = 0, remap = false, require = 0)
    private BlockState snappy_blocks$skipHidden(BlockState s, @Local(ordinal = 6) int y, @Local(ordinal = 7) int z, @Local(ordinal = 8) int x) {
        LongSet h = snappy_blocks$hidden;
        return h != null && h.contains(BlockPos.asLong(x, y, z)) ? Hidden.standIn(s) : s;
    }

    @Unique
    private static final String MESH = "execute(Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildContext;"
        + "Lorg/embeddedt/embeddium/impl/util/task/CancellationToken;)Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildOutput;";
}
