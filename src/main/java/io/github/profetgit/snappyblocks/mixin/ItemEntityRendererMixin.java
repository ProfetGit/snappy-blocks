package io.github.profetgit.snappyblocks.mixin;

import io.github.profetgit.snappyblocks.burst.Bursts;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render-only offset of a drop springing out of its burst; the entity's position is never touched (PRD §5.4). */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("RETURN"))
    private void snappy_blocks$offset(ItemEntity entity, ItemEntityRenderState state, float partial, CallbackInfo ci) {
        Bursts.offset(entity.getId(), state);
    }
}
