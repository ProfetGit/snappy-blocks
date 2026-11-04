package io.github.profetgit.snappyblocks.mixin.compat;

import io.github.profetgit.snappyblocks.pop.Hidden;
import io.github.profetgit.snappyblocks.pop.HiddenHolder;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The meshing task's constructor as Sodium 0.9 on 26.2 has it (seven arguments): takes the hidden snapshot on the render thread. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask", remap = false)
public abstract class SodiumMeshingTaskInit7Mixin {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void snappy_blocks$capture(RenderSection render, int buildTime, Vector3dc camera, ChunkRenderContext context, SortBehavior sort,
                                       boolean forceSort, boolean blocking, CallbackInfo ci) {
        ((HiddenHolder) this).snappy_blocks$setHidden(Hidden.snapshot());
    }
}
