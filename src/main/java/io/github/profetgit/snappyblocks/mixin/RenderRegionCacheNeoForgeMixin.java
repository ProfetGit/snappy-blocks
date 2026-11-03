package io.github.profetgit.snappyblocks.mixin;

//? if fabric {
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

/** Placeholder: NeoForge's section tasks call createRegion(Level, SectionPos, boolean), which the hook below covers. */
@Mixin(Minecraft.class)
public abstract class RenderRegionCacheNeoForgeMixin {
}
//?} else {
/*import io.github.profetgit.snappyblocks.pop.Hidden;
import io.github.profetgit.snappyblocks.pop.HiddenRegion;
import net.minecraft.Util;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderRegionCache.class)
public abstract class RenderRegionCacheNeoForgeMixin {
    @Inject(method = "createRegion(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/SectionPos;Z)Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;", at = @At("RETURN"))
    private void snappy_blocks$hidden(Level level, SectionPos section, boolean flag, CallbackInfoReturnable<RenderSectionRegion> cir) {
        Hidden.compileStarted(section.asLong(), Util.getNanos());
        RenderSectionRegion region = cir.getReturnValue();
        if (region != null) ((HiddenRegion) region).snappy_blocks$setHidden(Hidden.snapshot());
    }
}
*///?}
