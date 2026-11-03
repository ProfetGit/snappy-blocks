package io.github.profetgit.snappyblocks.pop;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block positions left out of the chunk mesh while their animated copy is drawn (PRD §5.1 hide path). The mesher sees
 * a hidden block as air (or as its own fluid, for a waterlogged block), so the faces around it are meshed too and the
 * ground under a growing block shows.
 *
 * <p>Changed on the render thread only. A mesh region takes a snapshot when it is created (render thread), and its
 * compile (a worker thread) reads only that snapshot: a mesh is "hidden" exactly when its compile task started while the
 * position was hidden, which is what the hand-off in {@link Pops} relies on.
 */
public final class Hidden {
    private static final LongOpenHashSet SET = new LongOpenHashSet();
    private static LongSet snapshot;
    /** Before 1.21.2: Util.getNanos() when a mesh region of a section was made (render thread), read when that section's mesh is live. */
    private static final ConcurrentHashMap<Long, Long> COMPILE_STARTS = new ConcurrentHashMap<>();

    private Hidden() {}

    static void add(long pos) {
        if (SET.add(pos)) snapshot = null;
    }

    static void remove(long pos) {
        if (SET.remove(pos)) snapshot = null;
    }

    static void clear() {
        SET.clear();
        snapshot = null;
        COMPILE_STARTS.clear();
    }

    public static void compileStarted(long sectionNode, long ns) {
        COMPILE_STARTS.put(sectionNode, ns);
    }

    public static long compileStart(long sectionNode) {
        Long ns = COMPILE_STARTS.get(sectionNode);
        return ns == null ? 0 : ns;
    }

    public static boolean isEmpty() {
        return SET.isEmpty();
    }

    /** For a new mesh region: an immutable copy, shared until the set changes; null when nothing is hidden. */
    public static LongSet snapshot() {
        if (SET.isEmpty()) return null;
        LongSet s = snapshot;
        if (s == null) snapshot = s = LongSets.unmodifiable(new LongOpenHashSet(SET));
        return s;
    }

    /** What the mesher sees at a hidden position. */
    public static BlockState standIn(BlockState real) {
        return real.getFluidState().isEmpty() ? Blocks.AIR.defaultBlockState() : real.getFluidState().createLegacyBlock();
    }
}
