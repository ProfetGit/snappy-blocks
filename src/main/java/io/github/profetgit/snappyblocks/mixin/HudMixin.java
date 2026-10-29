package io.github.profetgit.snappyblocks.mixin;

import io.github.profetgit.snappyblocks.item.Ghosts;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A hotbar slot that was empty shows its new stack when the pickup flight reaches it (PRD §5.5). */
@Mixin(Hud.class)
public abstract class HudMixin {
    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void snappy_blocks$hideSlot(GuiGraphicsExtractor graphics, int x, int y, DeltaTracker delta, Player player, ItemStack stack, int seed, CallbackInfo ci) {
        if (Ghosts.hiddenSlot(stack)) ci.cancel();
    }
}
