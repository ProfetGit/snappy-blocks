package io.github.profetgit.snappyblocks.pop;

import it.unimi.dsi.fastutil.longs.LongSet;

/** Implemented by Sodium's meshing task through compat.SodiumMeshingTaskMixin: the hidden positions taken when the task was made. */
public interface HiddenHolder {
    void snappy_blocks$setHidden(LongSet hidden);
}
