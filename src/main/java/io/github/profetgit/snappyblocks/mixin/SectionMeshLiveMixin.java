package io.github.profetgit.snappyblocks.mixin;

//? if >=26.3 || <1.21.2 {
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

/** Placeholder: 26.3 signals mesh-live through LevelRenderer.removeTransientBlocksInSection (LevelRendererMixin), before 1.21.2 through addRecentlyCompiled*. */
@Mixin(Minecraft.class)
public abstract class SectionMeshLiveMixin {
}
//?} else {
/*import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.pop.Hidden;
import io.github.profetgit.snappyblocks.pop.Pops;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.chunk.SectionMesh;

// 26.2 has no transient blocks: a section mesh is live when it is swapped in (RenderSection.setSectionMesh), compile start from the region snapshot.
@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class SectionMeshLiveMixin {
    @Inject(method = "setSectionMesh", at = @At("TAIL"))
    private void snappy_blocks$meshLive(SectionMesh mesh, CallbackInfoReturnable<SectionMesh> cir) {
        SectionRenderDispatcher.RenderSection self = (SectionRenderDispatcher.RenderSection) (Object) this;
        long node = SectionPos.asLong(self.getRenderOrigin());
        long start = Hidden.compileStart(node);
        Pops.meshLive(node, start);
        Bursts.meshLive(node, start);
    }
}
*///?}
