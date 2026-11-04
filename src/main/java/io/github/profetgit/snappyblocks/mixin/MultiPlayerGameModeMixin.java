package io.github.profetgit.snappyblocks.mixin;

import io.github.profetgit.snappyblocks.item.ItemFx;
import io.github.profetgit.snappyblocks.pop.Pops;
import io.github.profetgit.snappyblocks.pulse.Pulses;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    /** The clicked face sets the pivot of the block this click places (PRD §5.1). */
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void snappy_blocks$face(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        Pops.clickedFace(hit.getDirection());
    }

//? if <1.21.2 {
    /*// no level events for other miners' hits before 1.21.2: the player's own hit, once per four ticks, as the hit sound plays
    @Inject(method = "continueDestroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V"))
    private void snappy_blocks$hit(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        Pulses.hit(pos, direction);
    }

    *///?}
//? if >=26.2 && <26.3 {
    /*// 26.2 sends no level events for other miners' hits either: the player's own hit, as the hit sound plays
    @Inject(method = "continueDestroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;"))
    private void snappy_blocks$hit262(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        Pulses.hit(pos, direction);
    }

    *///?}
//? if >=26.3 {
    /** A Q press with something in the hand: the item it throws starts at the hand (PRD §5.4). */
    @Inject(method = "dropItem", at = @At("HEAD"))
    private void snappy_blocks$drop(LocalPlayer player, boolean all, CallbackInfo ci) {
        if (!player.getInventory().getSelectedItem().isEmpty()) ItemFx.dropPressed(Minecraft.getInstance());
    }
//?}
}
