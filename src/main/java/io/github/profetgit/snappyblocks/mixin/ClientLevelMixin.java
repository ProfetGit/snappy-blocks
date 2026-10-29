package io.github.profetgit.snappyblocks.mixin;

import io.github.profetgit.snappyblocks.Probe;
import io.github.profetgit.snappyblocks.Rtt;
import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.pop.Pops;
import io.github.profetgit.snappyblocks.pulse.Pulses;
import java.util.ArrayDeque;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
            Bursts.blockChanged(pos, old, state);
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
            Bursts.blockChanged(pos, old, now);
        }
    }

    /** The server's correction of a prediction: a block change inside it is a denied placement or break. */
    @Inject(method = "syncBlockState", at = @At("HEAD"))
    private void snappy_blocks$syncBefore(BlockPos pos, BlockState state, Vec3 playerPos, CallbackInfo ci) {
        Probe.syncing = true;
    }

    @Inject(method = "syncBlockState", at = @At("RETURN"))
    private void snappy_blocks$syncAfter(BlockPos pos, BlockState state, Vec3 playerPos, CallbackInfo ci) {
        Probe.syncing = false;
    }

    /** Positional sounds from the server (and the client's own): another player's place sound starts a remote pop. */
    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"))
    private void snappy_blocks$sound(Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch,
        long seed, CallbackInfo ci) {
        Pops.sound(x, y, z, sound.value());
    }

    /** A mining hit with its sound: the miner's own first hit, or the server's level event 2020 (PRD §5.2). */
    @Inject(method = "addBreakingBlockEffects", at = @At("HEAD"))
    private void snappy_blocks$hit(BlockPos pos, Direction direction, boolean withSound, CallbackInfo ci) {
        if (withSound) Pulses.hit(pos, direction);
    }

    @Inject(method = "handleBlockChangedAck", at = @At("HEAD"))
    private void snappy_blocks$ack(int sequence, CallbackInfo ci) {
        Rtt.acked(sequence);
    }
}
