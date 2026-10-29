package io.github.profetgit.snappyblocks.pop;

import it.unimi.dsi.fastutil.longs.LongSet;

/** Added to the vanilla mesh region (RenderSectionRegion) by mixin: the hidden positions when it was created. */
public interface HiddenRegion {
    void snappy_blocks$setHidden(LongSet hidden);

    boolean snappy_blocks$hides(long pos);
}
