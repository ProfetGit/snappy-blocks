package io.github.profetgit.snappyblocks.mixin;

import io.github.profetgit.snappyblocks.Probe;
import io.github.profetgit.snappyblocks.pop.Pops;
import java.util.ArrayDeque;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every client block change, with the state it replaced and whether it is the local player's prediction. Server updates
 * come through setServerVerifiedBlockState, which calls Level.setBlock directly (not this override); while a spot is
 * predicted the server state is only stored, so the change shows up later through syncBlockState -> setBlock.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Shadow
    @Final
    private BlockStatePredictionHandler blockStatePredictionHandler;

    /** A stack: a change can set more blocks inside it (door halves, neighbour updates). */
    @Unique
    private final ArrayDeque<BlockState> snappy_blocks$old = new ArrayDeque<>();

    @Inject(method = "setBlock", at = @At("HEAD"))
    private void snappy_blocks$before(BlockPos pos, BlockState state, int flags, int limit, CallbackInfoReturnable<Boolean> cir) {
        snappy_blocks$old.push(((ClientLevel) (Object) this).getBlockState(pos));
    }

    @Inject(method = "setBlock", at = @At("RETURN"))
    private void snappy_blocks$after(BlockPos pos, BlockState state, int flags, int limit, CallbackInfoReturnable<Boolean> cir) {
        BlockState old = snappy_blocks$old.poll();
        if (old != null && cir.getReturnValueZ() && old != state) {
            boolean predicted = blockStatePredictionHandler.isPredicting();
            Probe.blockChanged(pos, old, state, predicted);
            Pops.blockChanged(pos, old, state, predicted);
        }
    }

    @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"))
    private void snappy_blocks$serverBefore(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        snappy_blocks$old.push(((ClientLevel) (Object) this).getBlockState(pos));
    }

    @Inject(method = "setServerVerifiedBlockState", at = @At("TAIL"))
    private void snappy_blocks$serverAfter(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        BlockState old = snappy_blocks$old.poll();
        BlockState now = ((ClientLevel) (Object) this).getBlockState(pos);
        if (old != null && old != now) {
            Probe.blockChanged(pos, old, now, false);
            Pops.blockChanged(pos, old, now, false);
        }
    }
}
