package io.github.profetgit.snappyblocks.mixin.compat;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.snappyblocks.Probe;
import io.github.profetgit.snappyblocks.pop.Pops;
import io.github.profetgit.snappyblocks.pulse.Pulses;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flerovium draws the crack overlay itself (it empties the list vanilla's loop walks, so our hook there never runs):
 * without this the crack stays block-sized and a growing or moving copy covers it. Its pose is at the block's corner,
 * pushed for this one crack.
 */
@Pseudo
@Mixin(targets = "com.moepus.flerovium.functions.BlockBreaking.BlockBreakingRenderer", remap = false)
public abstract class FleroviumCrackMixin {
    @Inject(method = "renderBreakingTexture", at = @At("HEAD"), remap = false, require = 0)
    private static void snappy_blocks$crack(CallbackInfo ci, @Local(argsOnly = true) PoseStack ps, @Local(argsOnly = true) BlockPos pos) {
        Probe.cracks++;
        Pops.transform(ps, pos);
        Pulses.transformCrack(ps, pos);
    }
}
