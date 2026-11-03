package io.github.profetgit.snappyblocks.mixin;

import io.github.profetgit.snappyblocks.burst.Bursts;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A claimed drop isn't drawn until its burst opens (pattern from Having a Blast's Drops). */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
//? if >=1.21.2 {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void snappy_blocks$hide(E entity, Frustum frustum, double x, double y, double z, float partial, CallbackInfoReturnable<Boolean> cir) {
        if (Bursts.hidden(entity)) cir.setReturnValue(false);
    }
//?} else {
/*    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void snappy_blocks$hide(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (Bursts.hidden(entity)) cir.setReturnValue(false);
    }
*///?}
}
