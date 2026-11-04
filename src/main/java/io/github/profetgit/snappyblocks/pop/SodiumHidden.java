package io.github.profetgit.snappyblocks.pop;

import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Sodium meshes on its own worker threads from its own level slice. A meshing task takes the hidden positions when it is
 * created (with its capture time, on the render thread) and publishes them here for its worker while it runs, for the
 * face-culling hook (compat.SodiumBlockRenderContextMixin).
 */
public final class SodiumHidden {
    /** Set by the first mesh task that ran through our hooks: the hide path is wired into this Sodium. */
    public static volatile boolean wired;
    public static final ThreadLocal<LongSet> CURRENT = new ThreadLocal<>();

    private SodiumHidden() {}

    public static boolean hides(long pos) {
        LongSet h = CURRENT.get();
        return h != null && h.contains(pos);
    }
}
