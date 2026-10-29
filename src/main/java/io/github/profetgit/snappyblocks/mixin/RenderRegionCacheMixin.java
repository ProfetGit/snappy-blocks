package io.github.profetgit.snappyblocks.mixin;

import io.github.profetgit.snappyblocks.pop.Hidden;
import io.github.profetgit.snappyblocks.pop.HiddenRegion;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A new mesh region (render thread) takes the hidden positions of this moment; its compile reads only those. */
@Mixin(RenderRegionCache.class)
public abstract class RenderRegionCacheMixin {
    @Inject(method = "createRegion", at = @At("RETURN"))
    private void snappy_blocks$hidden(ClientLevel level, long sectionNode, CallbackInfoReturnable<RenderSectionRegion> cir) {
        ((HiddenRegion) cir.getReturnValue()).snappy_blocks$setHidden(Hidden.snapshot());
    }
}
