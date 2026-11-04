package io.github.profetgit.snappyblocks.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.profetgit.snappyblocks.pop.Hidden;
import io.github.profetgit.snappyblocks.pop.HiddenHolder;
import io.github.profetgit.snappyblocks.pop.SodiumHidden;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sodium's chunk mesher: the hide path (PRD §5.1) the same way as vanilla's SectionCompiler. Only with Sodium installed. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask", remap = false)
public abstract class SodiumMeshingTaskMixin implements HiddenHolder {
    @Unique
    private LongSet snappy_blocks$hidden;

    @Override
    public void snappy_blocks$setHidden(LongSet hidden) {
        snappy_blocks$hidden = hidden;
        SodiumHidden.wired = true;
    }

    @Inject(method = "execute", at = @At("HEAD"), remap = false)
    private void snappy_blocks$begin(ChunkBuildContext context, CancellationToken token, CallbackInfoReturnable<ChunkBuildOutput> cir) {
        SodiumHidden.CURRENT.set(snappy_blocks$hidden);
    }

    @Inject(method = "execute", at = @At("RETURN"), remap = false)
    private void snappy_blocks$end(ChunkBuildContext context, CancellationToken token, CallbackInfoReturnable<ChunkBuildOutput> cir) {
        SodiumHidden.CURRENT.remove();
    }

    @WrapOperation(method = "execute", remap = false, at = @At(value = "INVOKE", remap = false,
        target = "Lnet/caffeinemc/mods/sodium/client/world/LevelSlice;getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState snappy_blocks$skipHidden(LevelSlice slice, int x, int y, int z, Operation<BlockState> original) {
        BlockState s = original.call(slice, x, y, z);
        LongSet h = snappy_blocks$hidden;
        return h != null && h.contains(BlockPos.asLong(x, y, z)) ? Hidden.standIn(s) : s;
    }
}
