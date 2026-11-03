package io.github.profetgit.snappyblocks.pop;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.snappyblocks.Clock;
import io.github.profetgit.snappyblocks.Compat;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.Probe;
import io.github.profetgit.snappyblocks.RenderPass;
import io.github.profetgit.snappyblocks.SnappyBlocks;
import io.github.profetgit.snappyblocks.Tune;
import io.github.profetgit.snappyblocks.burst.Bursts;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
//? if >=1.21.2 {
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
//?} else {
/*import net.minecraft.client.resources.model.BakedModel;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.entity.BlockEntity;
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
    /**
     * Watchdogs (PRD pillar 4). A hidden block is meshed again after this many ticks, or at once while the clock stands
     * still (pause, /tick freeze). Ticks, not wall time: an offline render (a Flashback export) runs the clock slower
     * than real time, and a wall-time limit would cut its pops short. A released copy waiting for its mesh is dropped
     * after a second of wall time (meshing doesn't follow the clock).
     */
    static final long HIDE_LIMIT_TICKS = 20, HANDOFF_LIMIT_NS = 1_000_000_000L;
    /** Live pops at most; further placements stay vanilla (a placing mod at any rate stays cheap). */
    static final int CAP = 64;

    static final class Pop {
        final BlockPos pos;
        BlockState state;
        final double t0;
        final long startTick = Clock.ticks();
        final float px, py, pz;
        final PopRenderState render = new PopRenderState();
        /** Drawn by its block-entity renderer only: nothing is hidden, the renderer's pose is scaled (no hand-off). */
        boolean entity;
        /** A denied break growing back from {@code from} (PRD §5.3), instead of the place curve. */
        boolean regrow;
        double from;
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
    /** Place sounds of the last ticks (x, y, z, tick, sound): another player's placement plays one at the block. */
    private static final List<Object[]> SOUNDS = new ArrayList<>();
    /**
     * Another player's block that came before its place sound (a Flashback replay delivers the recorded player's own
     * placement as the block update, then the sound): it pops if the sound follows in the same tick before a frame has
     * shown the block. Cleared every frame.
     */
    private record Unsounded(BlockState state, long tick) {}
    private static final Long2ObjectOpenHashMap<Unsounded> UNSOUNDED = new Long2ObjectOpenHashMap<>();
    private static final Reference2BooleanOpenHashMap<BlockState> QUADS = new Reference2BooleanOpenHashMap<>();
    private static final double REMOTE_RANGE = 16;
    static final String REGROW_TICKS = Tune.def("deny.regrow", 4);
    private static Direction lastFace;
    private static long lastFaceTick = -1;

    public static int started, handedOff, watchdogs, remote, lateSounds, entityPops, regrows, denied, overCap, stopReleases;
    /** Longest time from a pop's start to its hand-off, in ticks (test hook; reset by the tests). */
    public static double maxLife;
    public static long spentNanos;

    private Pops() {}

    public static void clear() {
        POPS.clear();
        LIVE.clear();
        SOUNDS.clear();
        UNSOUNDED.clear();
        QUADS.clear();
        Hidden.clear();
    }

    /** End of every frame: a block the player has seen whole no longer pops when its sound comes late. */
    public static void frame() {
        UNSOUNDED.clear();
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

    /** ClientLevel.playSeededSound: a sound at a block centre may be another player's placement. */
    public static void sound(double x, double y, double z, SoundEvent sound) {
        SOUNDS.removeIf(e -> Clock.ticks() - (long) e[3] > 2);
        SOUNDS.add(new Object[] {x, y, z, Clock.ticks(), sound});
        if (UNSOUNDED.isEmpty()) return;
        BlockPos pos = BlockPos.containing(x, y, z);
        Unsounded u = UNSOUNDED.get(pos.asLong());
        Minecraft mc = Minecraft.getInstance();
        if (u == null || u.tick != Clock.ticks() || mc.level == null || mc.level.getBlockState(pos) != u.state || !placeSound(pos, u.state)) return;
        UNSOUNDED.remove(pos.asLong());
        if (!start(pos, u.state, Clock.now(), null, REMOTE)) return;
        remote++;
        lateSounds++;
        // the other half of a door or bed came before the sound too
        Pop first = POPS.get(pos.asLong());
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            Unsounded h = UNSOUNDED.get(n.asLong());
            if (h != null && h.state.getBlock() == u.state.getBlock() && mc.level.getBlockState(n) == h.state) {
                UNSOUNDED.remove(n.asLong());
                start(n, h.state, first.t0, first, REMOTE);
            }
        }
    }

    private static boolean placeSound(BlockPos pos, BlockState state) {
        SoundEvent want = state.getSoundType().getPlaceSound();
        for (Object[] e : SOUNDS) {
            if (e[4] != want || Clock.ticks() - (long) e[3] > 2) continue;
            double dx = (double) e[0] - pos.getX() - 0.5, dy = (double) e[1] - pos.getY() - 0.5, dz = (double) e[2] - pos.getZ() - 0.5;
            if (dx * dx + dy * dy + dz * dz < 0.3 * 0.3) return true;
        }
        return false;
    }

    /**
     * Every client block change (ClientLevelMixin). Pops: the local player's predicted placement; another player's
     * placement (a block update next to its place sound at that block: the sound up to 2 ticks before, or right after
     * it in a Flashback replay; within 16 blocks of the camera); the second half of a popping bed or door arriving from
     * the server.
     */
    public static void blockChanged(BlockPos pos, BlockState old, BlockState now, boolean predicted) {
        Pop p = POPS.get(pos.asLong());
        if (p != null) {
            if (Probe.empty(now)) {
                if (Probe.syncing && !p.regrow) {
                    // the server refused the placement: the copy shrinks out where it was
                    denied++;
                    double t = Clock.now() - p.t0;
                    Bursts.fade(p.pos, p.state, p.releaseNs != 0 ? 1 : scale(p, t), p.px, p.py, p.pz);
                }
                cancel(p);
            } else {
                p.state = now;
                p.render.state(now);
            }
            return;
        }
        if (!Config.get().enabled || !Config.get().pop || Compat.placeAside) return;
        if (!(Probe.empty(old) || old.canBeReplaced()) || Probe.empty(now) || now.canBeReplaced()) return;
        if (predicted) {
            start(pos.immutable(), now, Clock.now(), null, LOCAL);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // the camera, not the player: in a replay the two can be far apart
        if (mc.player == null || mc.gameRenderer.mainCamera().position().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
            > REMOTE_RANGE * REMOTE_RANGE) return;
        Pop half = null;
        for (Direction d : Direction.values()) {
            Pop q = POPS.get(pos.relative(d).asLong());
            if (q != null && !q.regrow && q.releaseNs == 0 && q.state.getBlock() == now.getBlock()) half = q;
        }
        if (half != null) {
            start(pos.immutable(), now, half.t0, half, REMOTE);
        } else if (placeSound(pos, now)) {
            if (start(pos.immutable(), now, Clock.now(), null, REMOTE)) remote++;
        } else {
            UNSOUNDED.put(pos.asLong(), new Unsounded(now, Clock.ticks()));
        }
    }

    /** A denied break (PRD §5.3): the block the server kept grows back from where the shrink was. */
    public static void regrow(BlockPos pos, BlockState state, double from) {
        if (POPS.containsKey(pos.asLong()) || kind(pos, state) == Kind.NONE) return;
        if (start(pos, state, Clock.now(), null, REGROW)) {
            Pop p = POPS.get(pos.asLong());
            p.from = Math.max(0, Math.min(1, from));
            regrows++;
        }
    }

    enum Kind {
        /** Left out of the chunk mesh while a copy is drawn (the hide path). */
        MESH,
        /** Drawn only by its block-entity renderer, whose pose is scaled. */
        ENTITY,
        NONE
    }

    /**
     * The render path decides (PRD §5.1). A block entity with a renderer and an empty block model (chest, sign,
     * banner, skull, shulker box) is all renderer: it pops through the renderer's pose. One without a renderer
     * (furnace, barrel, hopper) is all mesh: the hide path. One with both (lectern, enchanting table, campfire) can't
     * pop: the mesher drops a hidden block's block entity from the section, so the renderer part would vanish.
     */
    static Kind kind(BlockPos pos, BlockState s) {
        if (!drawnLikeModel(s)) return Kind.NONE;
        if (!s.hasBlockEntity()) return eligible(s) ? Kind.MESH : Kind.NONE;
        Minecraft mc = Minecraft.getInstance();
        BlockEntity be = mc.level == null ? null : mc.level.getBlockEntity(pos);
        boolean renderer = be != null && mc.getBlockEntityRenderDispatcher().getRenderer(be) != null;
        boolean quads = hasQuads(s);
        if (!renderer) return quads && (!ShaderPack.active() || s.canOcclude() && s.getLightEmission() == 0) ? Kind.MESH : Kind.NONE;
        return quads ? Kind.NONE : Kind.ENTITY;
    }

    /** Chests, banners and the like: MODEL (with an empty model) in 26.x, ENTITYBLOCK_ANIMATED or INVISIBLE (banner) before 1.21.2. */
    public static boolean drawnLikeModel(BlockState s) {
//? if >=1.21.2 {
        return s.getRenderShape() == RenderShape.MODEL;
//?} else {
/*        RenderShape shape = s.getRenderShape();
        return shape == RenderShape.MODEL || (shape == RenderShape.ENTITYBLOCK_ANIMATED || shape == RenderShape.INVISIBLE) && s.hasBlockEntity();
*///?}
    }

    /** Whether a placement of {@code s} at {@code pos} pops (test hook). */
    public static boolean wouldPop(BlockPos pos, BlockState s) {
        return kind(pos, s) != Kind.NONE;
    }

    /** Whether the block's own model draws anything (a chest's is only a particle texture). */
    public static boolean hasQuads(BlockState s) {
        if (QUADS.containsKey(s)) return QUADS.getBoolean(s);
        boolean any = false;
//? if >=1.21.2 {
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(s);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42), parts);
        for (BlockStateModelPart part : parts) {
            if (!part.getQuads(null).isEmpty()) any = true;
            for (Direction d : Direction.values()) if (!part.getQuads(d).isEmpty()) any = true;
        }
//?} else {
/*        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(s);
        RandomSource random = RandomSource.create(42);
        if (!model.getQuads(s, null, random).isEmpty()) any = true;
        for (Direction d : Direction.values()) if (!model.getQuads(s, d, random).isEmpty()) any = true;
*///?}
        QUADS.put(s, any);
        return any;
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

    private static final int LOCAL = 0, REMOTE = 1, REGROW = 2;

    private static boolean start(BlockPos pos, BlockState state, double t0, Pop half, int mode) {
        Kind kind = kind(pos, state);
        if (kind == Kind.NONE) return false;
        if (POPS.size() >= CAP) {
            overCap++;
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        // another player's placement: the face they clicked isn't known, so it grows from the floor
        Direction support = mode == LOCAL && lastFaceTick == Clock.ticks() && lastFace != null ? lastFace.getOpposite() : Direction.DOWN;
        float px = 0.5f + 0.5f * support.getStepX(), py = 0.5f + 0.5f * support.getStepY(), pz = 0.5f + 0.5f * support.getStepZ();
        if (mode == REGROW) {
            py = 0.5f;
        } else if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            // both halves grow from the lower half's floor
            px = 0.5f;
            pz = 0.5f;
            py = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? -1f : 0f;
        } else if (half != null) {
            // the other half of a bed grows from the first half's pivot
            px = half.px + half.pos.getX() - pos.getX();
            py = half.py + half.pos.getY() - pos.getY();
            pz = half.pz + half.pos.getZ() - pos.getZ();
        }
        Pop p = new Pop(pos, state, t0, px, py, pz);
        p.entity = kind == Kind.ENTITY;
        p.regrow = mode == REGROW;
        p.render.init(level, pos, state);
        POPS.put(pos.asLong(), p);
        if (p.entity) entityPops++;
        else Hidden.add(pos.asLong());
        started++;
        return true;
    }

    private static void release(Pop p) {
        if (p.releaseNs != 0) return;
        if (p.entity) {
            p.releaseNs = Util.getNanos();
            p.releaseWallNs = System.nanoTime();
            return;
        }
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

    /** Render thread, every frame before drawing and every client tick: curve ends, hand-offs, watchdogs. */
    public static void update() {
        long[] live;
        while ((live = LIVE.poll()) != null) {
            for (Iterator<Pop> it = POPS.values().iterator(); it.hasNext(); ) {
                Pop p = it.next();
                if (p.releaseNs != 0 && live[1] > p.releaseNs && SectionPos.blockToSection(p.pos.asLong()) == live[0]) {
                    it.remove();
                    handedOff++;
                    maxLife = Math.max(maxLife, Clock.now() - p.t0);
                }
            }
        }
        if (POPS.isEmpty()) return;
        double now = Clock.now();
        long wall = System.nanoTime();
        boolean stopped = Clock.stopped();
        for (Iterator<Pop> it = POPS.values().iterator(); it.hasNext(); ) {
            Pop p = it.next();
            double ticks = ticks(p);
            boolean late = Clock.ticks() - p.startTick > HIDE_LIMIT_TICKS;
            if (p.entity) {
                if (now - p.t0 >= ticks || late) it.remove();
                continue;
            }
            if (p.releaseNs == 0 && (now - p.t0 >= ticks || late || stopped)) {
                if (stopped && now - p.t0 < ticks) stopReleases++;
                else if (now - p.t0 < ticks) watchdogs++;
                release(p);
            } else if (p.releaseNs != 0 && wall - p.releaseWallNs > HANDOFF_LIMIT_NS) {
                it.remove();
                watchdogs++;
                SnappyBlocks.LOG.warn("Snappy Blocks: no mesh took over the pop at {} within 1 s", p.pos);
            }
        }
    }

    static double ticks(Pop p) {
        return p.regrow ? Math.max(1, Tune.get(REGROW_TICKS)) : Tune.get(Tune.POP_TICKS);
    }

    static double scale(Pop p, double t) {
        if (!p.regrow) return scale(t);
        double u = Math.min(1, Math.max(0, t / ticks(p)));
        // ease out with a small overshoot (back curve)
        double c = 1.2, v = u - 1;
        return p.from + (1 - p.from) * (1 + (c + 1) * v * v * v + c * v * v);
    }

    /**
     * For the outline, the crack overlay and a block-entity renderer at {@code pos}, with the pose at the block's
     * corner: the pop's current scale about its pivot, or nothing.
     */
    public static void transform(PoseStack ps, BlockPos pos) {
        if (POPS.isEmpty()) return;
        Pop p = POPS.get(pos.asLong());
        if (p == null || p.releaseNs != 0) return;
        double s = scale(p, Clock.now() - p.t0);
        if (s == 1) return;
        float f = (float) s;
        ps.translate(p.px, p.py, p.pz);
        ps.scale(f, f, f);
        ps.translate(-p.px, -p.py, -p.pz);
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

    /** The copies, drawn after the terrain and the entities (26.3: in the same pass as vanilla's own transient blocks). */
    public static void submit(RenderPass pass) {
        long t0 = System.nanoTime();
        try {
            update();
            if (POPS.isEmpty()) return;
            PoseStack ps = pass.ps;
            Vec3 cam = pass.cam;
            double now = Clock.now();
            for (Pop p : POPS.values()) {
                if (p.entity) continue;
                double s = p.releaseNs != 0 ? 1 : scale(p, now - p.t0);
                ps.pushPose();
                ps.translate(p.pos.getX() - cam.x, p.pos.getY() - cam.y, p.pos.getZ() - cam.z);
                if (s != 1) {
                    ps.translate(p.px, p.py, p.pz);
                    ps.scale((float) s, (float) s, (float) s);
                    ps.translate(-p.px, -p.py, -p.pz);
                }
                p.render.draw(pass);
                ps.popPose();
            }
        } finally {
            spentNanos += System.nanoTime() - t0;
        }
    }
}
