package io.github.profetgit.snappyblocks.mixin;

import io.github.profetgit.snappyblocks.SnappyBlocks;
import io.github.profetgit.snappyblocks.selftest.SelfTest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void snappy_blocks$tick(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        SnappyBlocks.onClientTick(mc);
        if (SelfTest.active()) SelfTest.onClientTick(mc);
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    private void snappy_blocks$frame(boolean advance, CallbackInfo ci) {
        io.github.profetgit.snappyblocks.burst.Bursts.frame();
        io.github.profetgit.snappyblocks.pop.Pops.frame();
        if (SelfTest.active()) SelfTest.onFrame((Minecraft) (Object) this);
    }

    @Inject(method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;Z)V", at = @At("HEAD"))
    private void snappy_blocks$levelChange(ClientLevel level, boolean stopSound, CallbackInfo ci) {
        SnappyBlocks.onLevelChange();
    }

    /** Test driver only: hold the attack button the way a player does, so vanilla mines (and never aborts) the target. */
    @ModifyArg(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;continueAttack(Z)V"))
    private boolean snappy_blocks$testAttack(boolean down) {
        return down || SelfTest.attackHeld();
    }
}
