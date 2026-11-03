package io.github.profetgit.snappyblocks.mixin;

import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.2 {
import io.github.profetgit.snappyblocks.Probe;
import io.github.profetgit.snappyblocks.burst.Bursts;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Level events as the client plays them: 2001 (block destroyed) and 2020 (mining hit) matter here (PRD §5.2, §5.3). */
@Mixin(LevelEventHandler.class)
public abstract class LevelEventHandlerMixin {
    @Inject(method = "levelEvent", at = @At("HEAD"))
    private void snappy_blocks$event(int type, BlockPos pos, int data, CallbackInfo ci) {
        Probe.levelEvent(type, pos, data);
        Bursts.levelEvent(type, pos, data);
    }
}
//?} else {
/*// level events reach LevelRenderer.levelEvent before 1.21.2 (LevelRendererMixin)
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class LevelEventHandlerMixin {
}
*///?}
