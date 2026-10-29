package io.github.profetgit.snappyblocks.mixin;

import io.github.profetgit.snappyblocks.pop.Pops;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    /** The clicked face sets the pivot of the block this click places (PRD §5.1). */
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void snappy_blocks$face(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        Pops.clickedFace(hit.getDirection());
    }
}
