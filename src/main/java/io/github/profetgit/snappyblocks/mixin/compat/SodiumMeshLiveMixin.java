package io.github.profetgit.snappyblocks.mixin.compat;

import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.pop.Hidden;
import java.util.ArrayList;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Before 1.21.2 vanilla tells us when a section mesh is live (LevelRendererMixin); Sodium replaces that path and never
 * does, so a break burst would wait out its whole mesh limit and the block would blink out first. This is the same
 * signal from Sodium's side: the compile start is noted when a rebuild task is made, and a section is live once
 * its results are uploaded. Only on versions before 1.21.2 (MixinPlugin).
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class SodiumMeshLiveMixin {
    @Inject(method = "createRebuildTask", at = @At("HEAD"), remap = false)
    private void snappy_blocks$compileStart(RenderSection section, int frame, CallbackInfoReturnable<?> cir) {
        Hidden.compileStarted(SectionPos.asLong(section.getChunkX(), section.getChunkY(), section.getChunkZ()), Util.getNanos());
    }

    @Inject(method = "processChunkBuildResults", at = @At("RETURN"), remap = false)
    private void snappy_blocks$live(ArrayList<BuilderTaskOutput> results, CallbackInfoReturnable<Boolean> cir) {
        for (BuilderTaskOutput o : results) {
            RenderSection s = o.render;
            long node = SectionPos.asLong(s.getChunkX(), s.getChunkY(), s.getChunkZ());
            Bursts.meshLive(node, Hidden.compileStart(node));
        }
    }
}
