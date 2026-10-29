package io.github.profetgit.snappyblocks.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.snappyblocks.pop.Pops;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    /** The vanilla hand-off signal: a section mesh whose compile started at this time is now live. */
    @Inject(method = "removeTransientBlocksInSection", at = @At("HEAD"))
    private void snappy_blocks$meshLive(long sectionNode, long compileTaskStartTimeNs, CallbackInfo ci) {
        Pops.meshLive(sectionNode, compileTaskStartTimeNs);
    }

    @Inject(method = "submitTransientBlocks", at = @At("TAIL"))
    private void snappy_blocks$submit(PoseStack ps, SubmitNodeCollector collector, LevelRenderState state, CallbackInfo ci) {
        Pops.submit(ps, collector, state);
    }
}
