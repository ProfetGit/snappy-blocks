package io.github.profetgit.snappyblocks.pop;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.snappyblocks.Clock;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.Probe;
import io.github.profetgit.snappyblocks.SnappyBlocks;
import io.github.profetgit.snappyblocks.Tune;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * Place pop (PRD §5.1). A placed block is left out of the chunk mesh ({@link Hidden}) and drawn instead as a vanilla
 * moving block (the piston and falling-block path) that grows about its support face. When the curve ends the block is
 * meshed again, and the copy stays at full size until a mesh whose compile started after that is live: the rule
 * vanilla's own transient blocks use (LevelRenderer.removeTransientBlocksInSection), so the swap has no gap.
 */
public final class Pops {
    /** Frame-time watchdogs (PRD pillar 4): a hidden block is meshed again after this long, a released copy dropped. */
    static final long HIDE_LIMIT_NS = 1_000_000_000L, HANDOFF_LIMIT_NS = 1_000_000_000L;

    static final class Pop {
        final BlockPos pos;
        BlockState state;
        final double t0;
        final long startNs = System.nanoTime();
        final float px, py, pz;
        final PopRenderState render = new PopRenderState();
        /** Util.getNanos() when meshed again (the compile-start clock); 0 while hidden. */
        long releaseNs;
        long releaseWallNs;

        Pop(BlockPos pos, BlockState state, double t0, float px, float py, float pz) {
            this.pos = pos;
            this.state = state;
            this.t0 = t0;
            this.px = px;
            this.py = py;
            this.pz = pz;
        }
    }

    private static final Long2ObjectLinkedOpenHashMap<Pop> POPS = new Long2ObjectLinkedOpenHashMap<>();
    /** (sectionNode, compileTaskStartTimeNs) of meshes that went live; filled from mesh threads too. */
    private static final ConcurrentLinkedQueue<long[]> LIVE = new ConcurrentLinkedQueue<>();
    private static Direction lastFace;
    private static long lastFaceTick = -1;

    public static int started, handedOff, watchdogs;
    public static long spentNanos;

    private Pops() {}

    public static void clear() {
        POPS.clear();
        LIVE.clear();
        Hidden.clear();
    }

    public static int active() {
        return POPS.size();
    }

    public static boolean popping(BlockPos pos) {
        return POPS.containsKey(pos.asLong());
    }

    /** MultiPlayerGameMode.useItemOn: the face the player clicked, for the pivot of what it places. */
    public static void clickedFace(Direction face) {
        lastFace = face;
        lastFaceTick = Clock.ticks();
    }

    /** Every client block change (ClientLevelMixin). */
    public static void blockChanged(BlockPos pos, BlockState old, BlockState now, boolean predicted) {
        Pop p = POPS.get(pos.asLong());
        if (p != null) {
            if (Probe.empty(now)) {
                cancel(p);
            } else {
                p.state = now;
                p.render.blockState = now;
            }
            return;
        }
        if (!predicted || !Config.get().enabled || !Config.get().pop) return;
        if (!(Probe.empty(old) || old.canBeReplaced()) || Probe.empty(now) || now.canBeReplaced()) return;
        if (!eligible(now)) return;
        start(pos.immutable(), now);
    }

    /**
     * Model blocks without a block entity. Under a shader pack the copy is drawn with the moving-block program, so blocks
     * a pack treats per material (waving leaves and plants, emissive, translucent) would change look at the hand-off:
     * those are placed without a pop there (M1 measurement 2026-10-29, Complementary: leaves 41, glowstone 21, glass 18
     * against 2-9 for opaque blocks).
     */
    public static boolean eligible(BlockState s) {
        if (s.getRenderShape() != RenderShape.MODEL || s.hasBlockEntity()) return false;
        return !ShaderPack.active() || s.canOcclude() && s.getLightEmission() == 0;
    }

    private static void start(BlockPos pos, BlockState state) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Direction support = lastFaceTick == Clock.ticks() && lastFace != null ? lastFace.getOpposite() : Direction.DOWN;
        float px = 0.5f + 0.5f * support.getStepX(), py = 0.5f + 0.5f * support.getStepY(), pz = 0.5f + 0.5f * support.getStepZ();
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            // both halves grow from the lower half's floor
            px = 0.5f;
            pz = 0.5f;
            py = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? -1f : 0f;
        }
        Pop p = new Pop(pos, state, Clock.now(), px, py, pz);
        p.render.blockPos = pos;
        p.render.randomSeedPos = pos;
        p.render.blockState = state;
        p.render.biome = level.getBiome(pos);
        p.render.cardinalLighting = level.cardinalLighting();
        p.render.lightEngine = level.getLightEngine();
        p.render.level = level;
        POPS.put(pos.asLong(), p);
        Hidden.add(pos.asLong());
        started++;
    }

    private static void release(Pop p) {
        if (p.releaseNs != 0) return;
        Hidden.remove(p.pos.asLong());
        p.releaseNs = Util.getNanos();
        p.releaseWallNs = System.nanoTime();
        BlockPos b = p.pos;
        Minecraft.getInstance().levelExtractor.setBlocksDirty(b.getX(), b.getY(), b.getZ(), b.getX(), b.getY(), b.getZ());
    }

    private static void cancel(Pop p) {
        release(p);
        POPS.remove(p.pos.asLong());
    }

    /** A section mesh went live (any thread). */
    public static void meshLive(long sectionNode, long compileStartNs) {
        if (!POPS.isEmpty()) LIVE.add(new long[] {sectionNode, compileStartNs});
    }

    /** Render thread, every frame before drawing: curve ends, hand-offs, watchdogs. */
    static void update() {
        long[] live;
        while ((live = LIVE.poll()) != null) {
            for (Iterator<Pop> it = POPS.values().iterator(); it.hasNext(); ) {
                Pop p = it.next();
                if (p.releaseNs != 0 && live[1] > p.releaseNs && SectionPos.blockToSection(p.pos.asLong()) == live[0]) {
                    it.remove();
                    handedOff++;
                }
            }
        }
        if (POPS.isEmpty()) return;
        double now = Clock.now();
        double ticks = Tune.get(Tune.POP_TICKS);
        long wall = System.nanoTime();
        for (Iterator<Pop> it = POPS.values().iterator(); it.hasNext(); ) {
            Pop p = it.next();
            if (p.releaseNs == 0 && (now - p.t0 >= ticks || wall - p.startNs > HIDE_LIMIT_NS)) {
                if (now - p.t0 < ticks) watchdogs++;
                release(p);
            } else if (p.releaseNs != 0 && wall - p.releaseWallNs > HANDOFF_LIMIT_NS) {
                it.remove();
                watchdogs++;
                SnappyBlocks.LOG.warn("Snappy Blocks: no mesh took over the pop at {} within 1 s", p.pos);
            }
        }
    }

    /** Scale at {@code t} ticks into the pop: grows from pop.start past pop.peak, then settles to 1. */
    static double scale(double t) {
        double ticks = Tune.get(Tune.POP_TICKS), peakAt = Math.max(0.05, Tune.get(Tune.POP_PEAK_AT)) * ticks;
        double start = 1 - (1 - Tune.get(Tune.POP_START)) * Config.get().amplitude();
        double peak = 1 + (Tune.get(Tune.POP_PEAK) - 1) * Config.get().amplitude();
        if (t <= 0) return start;
        if (t < peakAt) {
            double u = 1 - t / peakAt;
            return start + (peak - start) * (1 - u * u * u);
        }
        if (t < ticks) {
            double u = (t - peakAt) / (ticks - peakAt);
            return peak + (1 - peak) * (u * u * (3 - 2 * u));
        }
        return 1;
    }

    /** LevelRenderer.submitTransientBlocks TAIL: the copies, in the same pass as vanilla's own transient blocks. */
    public static void submit(PoseStack ps, SubmitNodeCollector collector, LevelRenderState state) {
        long t0 = System.nanoTime();
        try {
            update();
            if (POPS.isEmpty()) return;
            Vec3 cam = state.cameraRenderState.pos;
            double now = Clock.now();
            for (Pop p : POPS.values()) {
                double s = p.releaseNs != 0 ? 1 : scale(now - p.t0);
                ps.pushPose();
                ps.translate(p.pos.getX() - cam.x, p.pos.getY() - cam.y, p.pos.getZ() - cam.z);
                if (s != 1) {
                    ps.translate(p.px, p.py, p.pz);
                    ps.scale((float) s, (float) s, (float) s);
                    ps.translate(-p.px, -p.py, -p.pz);
                }
                collector.submitMovingBlock(ps, p.render, 0);
                ps.popPose();
            }
        } finally {
            spentNanos += System.nanoTime() - t0;
        }
    }
}
