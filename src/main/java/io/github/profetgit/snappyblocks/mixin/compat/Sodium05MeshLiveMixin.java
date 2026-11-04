package io.github.profetgit.snappyblocks.mixin.compat;

import io.github.profetgit.snappyblocks.SnappyBlocks;
import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.pop.Hidden;
import io.github.profetgit.snappyblocks.pop.Pops;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import net.minecraft.util.Util;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium 0.5 (1.20.1) replaces vanilla's "section mesh is live" path: without this a break burst waits out its mesh limit
 * (the block blinks out first) and a place pop never hands its block back. Same signal as SodiumMeshLiveMixin (0.8), read
 * through method handles so the handlers name no Sodium type.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class Sodium05MeshLiveMixin {
    @Unique
    private static MethodHandle snappy_blocks$x, snappy_blocks$y, snappy_blocks$z, snappy_blocks$render;

    @Unique
    private static long snappy_blocks$node(Object section) throws Throwable {
        if (snappy_blocks$x == null) {
            Class<?> c = Class.forName("me.jellysquid.mods.sodium.client.render.chunk.RenderSection");
            MethodType i = MethodType.methodType(int.class);
            snappy_blocks$x = MethodHandles.publicLookup().findVirtual(c, "getChunkX", i).asType(MethodType.methodType(int.class, Object.class));
            snappy_blocks$y = MethodHandles.publicLookup().findVirtual(c, "getChunkY", i).asType(MethodType.methodType(int.class, Object.class));
            snappy_blocks$z = MethodHandles.publicLookup().findVirtual(c, "getChunkZ", i).asType(MethodType.methodType(int.class, Object.class));
            Class<?> out = Class.forName("me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput");
            snappy_blocks$render = MethodHandles.publicLookup().findGetter(out, "render", c).asType(MethodType.methodType(Object.class, Object.class));
        }
        return SectionPos.asLong((int) snappy_blocks$x.invokeExact(section), (int) snappy_blocks$y.invokeExact(section), (int) snappy_blocks$z.invokeExact(section));
    }

    @Inject(method = "createRebuildTask", at = @At("HEAD"), remap = false)
    private void snappy_blocks$compileStart(@Coerce Object section, int frame, CallbackInfoReturnable<?> cir) {
        try {
            Hidden.compileStarted(snappy_blocks$node(section), Util.getNanos());
        } catch (Throwable t) {
            SnappyBlocks.LOG.warn("Snappy Blocks: can't read a Sodium section ({})", t.toString());
        }
    }

    @Inject(method = "processChunkBuildResults", at = @At("RETURN"), remap = false)
    private void snappy_blocks$live(ArrayList<?> results, CallbackInfo ci) {
        try {
            for (Object o : results) {
                long node = snappy_blocks$node((Object) snappy_blocks$render.invokeExact(o));
                long start = Hidden.compileStart(node);
                Pops.meshLive(node, start);
                Bursts.meshLive(node, start);
            }
        } catch (Throwable t) {
            SnappyBlocks.LOG.warn("Snappy Blocks: can't read Sodium's mesh results ({})", t.toString());
        }
    }
}
