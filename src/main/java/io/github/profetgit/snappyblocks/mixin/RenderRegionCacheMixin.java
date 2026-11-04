package io.github.profetgit.snappyblocks.mixin;

import io.github.profetgit.snappyblocks.pop.Hidden;
import io.github.profetgit.snappyblocks.pop.HiddenRegion;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=1.21.2 {
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
//?} else if >=1.21 {
/*import net.minecraft.Util;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
*///?} else {
/*import net.minecraft.Util;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
*///?}

/** A new mesh region (render thread) takes the hidden positions of this moment; its compile reads only those. */
@Mixin(RenderRegionCache.class)
public abstract class RenderRegionCacheMixin {
//? if >=1.21.2 {
    @Inject(method = "createRegion", at = @At("RETURN"))
    private void snappy_blocks$hidden(ClientLevel level, long sectionNode, CallbackInfoReturnable<RenderSectionRegion> cir) {
        ((HiddenRegion) cir.getReturnValue()).snappy_blocks$setHidden(Hidden.snapshot());
    }
//?} else if >=1.21 {
/*    // the compile-start clock vanilla's transient blocks have in 26.x: the moment this region (and its hidden snapshot) was made
    @Inject(method = "createRegion", at = @At("RETURN"))
    private void snappy_blocks$hidden(Level level, SectionPos section, CallbackInfoReturnable<RenderSectionRegion> cir) {
        Hidden.compileStarted(section.asLong(), Util.getNanos());
        RenderSectionRegion region = cir.getReturnValue();
        if (region != null) ((HiddenRegion) region).snappy_blocks$setHidden(Hidden.snapshot());
    }
*///?} else {
/*    // the compile-start clock vanilla's transient blocks have in 26.x: the moment this region (and its hidden snapshot) was made
    @Inject(method = "createRegion", at = @At("RETURN"))
    private void snappy_blocks$hidden(Level level, BlockPos start, BlockPos end, int padding, CallbackInfoReturnable<RenderChunkRegion> cir) {
        Hidden.compileStarted(SectionPos.of(start.offset(padding, padding, padding)).asLong(), Util.getNanos());
        RenderChunkRegion region = cir.getReturnValue();
        if (region != null) ((HiddenRegion) region).snappy_blocks$setHidden(Hidden.snapshot());
    }
*///?}
}
