package io.github.profetgit.snappyblocks.mixin;

import io.github.profetgit.snappyblocks.pop.HiddenRegion;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
//? if >=1.21 {
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
//?} else {
/*import net.minecraft.client.renderer.chunk.RenderChunkRegion;
*///?}

/**
 * The hidden positions a mesh region was created with. The region itself still reads every block as it is, so the
 * neighbours' ambient occlusion already includes a hidden block (its contact shadow appears when it is placed, not at
 * the hand-off); SectionCompilerMixin and ModelBlockRendererMixin leave out only its own geometry and the culling
 * against it.
 */
//? if >=1.21 {
@Mixin(RenderSectionRegion.class)
//?} else {
/*@Mixin(RenderChunkRegion.class)
*///?}
public abstract class RenderSectionRegionMixin implements HiddenRegion {
    @Unique
    private LongSet snappy_blocks$hidden;

    @Override
    public void snappy_blocks$setHidden(LongSet hidden) {
        snappy_blocks$hidden = hidden;
    }

    @Override
    public boolean snappy_blocks$hides(long pos) {
        LongSet h = snappy_blocks$hidden;
        return h != null && h.contains(pos);
    }
}
