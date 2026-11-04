package io.github.profetgit.snappyblocks.mixin.compat;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.pop.Hidden;
import io.github.profetgit.snappyblocks.pop.Pops;
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
 * SodiumMeshLiveMixin for Sodium 0.9 (26.2): 26.2 has no transient blocks, and Sodium bypasses vanilla's
 * RenderSection.setSectionMesh (SectionMeshLiveMixin), so this is the only mesh-live signal there. 0.9 changed both
 * signatures and renamed BuilderTaskOutput.render to section. Only on 26.2 (MixinPlugin).
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class SodiumMeshLive09Mixin {
    @Inject(method = "createRebuildTask", at = @At("HEAD"), remap = false)
    private void snappy_blocks$compileStart(CallbackInfoReturnable<?> cir, @Local(argsOnly = true) RenderSection section) {
        Hidden.compileStarted(SectionPos.asLong(section.getChunkX(), section.getChunkY(), section.getChunkZ()), Util.getNanos());
    }

    @Inject(method = "processChunkBuildResults", at = @At("RETURN"), remap = false)
    private void snappy_blocks$live(CallbackInfoReturnable<?> cir, @Local(argsOnly = true) ArrayList<BuilderTaskOutput> results) {
        for (BuilderTaskOutput o : results) {
            RenderSection s = o.section;
            long node = SectionPos.asLong(s.getChunkX(), s.getChunkY(), s.getChunkZ());
            long start = Hidden.compileStart(node);
            Pops.meshLive(node, start);
            Bursts.meshLive(node, start);
        }
    }
}
