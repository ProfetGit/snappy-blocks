package io.github.profetgit.snappyblocks.mixin;

import io.github.profetgit.snappyblocks.Rtt;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockStatePredictionHandler.class)
public abstract class BlockStatePredictionHandlerMixin {
    /** A predicted action starts: its sequence number goes to the server and comes back in the ack (Rtt). */
    @Inject(method = "startPredicting", at = @At("RETURN"))
    private void snappy_blocks$sent(CallbackInfoReturnable<BlockStatePredictionHandler> cir) {
        Rtt.sent(cir.getReturnValue().currentSequence());
    }
}
