package io.github.profetgit.snappyblocks.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.profetgit.snappyblocks.pop.ShaderPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sodium draws moving blocks (our copies on 26.x, through the Fabric renderer API) with the ambient occlusion multiplied
 * into the colour. Under a shader pack with `separateAo` the chunk mesh has it in the vertex alpha instead and the pack
 * applies it its own (softer) way, so the copy's AO-darkened faces were darker than the mesh's: the copy gets the alpha too.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.frapi.render.NonTerrainBlockRenderContext", remap = false)
public abstract class SodiumNonTerrainAoMixin {
    @WrapOperation(method = "shadeQuad", remap = false, require = 0, at = @At(value = "INVOKE", remap = false,
        target = "Lnet/caffeinemc/mods/sodium/api/util/ColorARGB;mulRGB(IF)I"))
    private int snappy_blocks$aoAlpha(int color, float brightness, Operation<Integer> original) {
        if (!ShaderPack.aoCopy) return original.call(color, brightness);
        int a = Math.round(Math.max(0f, Math.min(1f, brightness)) * ((color >>> 24) & 0xFF));
        return (a << 24) | (color & 0xFFFFFF);
    }
}
