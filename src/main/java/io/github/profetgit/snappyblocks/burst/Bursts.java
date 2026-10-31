package io.github.profetgit.snappyblocks.burst;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.snappyblocks.Clock;
import io.github.profetgit.snappyblocks.Compat;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.Probe;
import io.github.profetgit.snappyblocks.Rtt;
import io.github.profetgit.snappyblocks.Tune;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import io.github.profetgit.snappyblocks.pop.PopRenderState;
import io.github.profetgit.snappyblocks.pop.Pops;
import io.github.profetgit.snappyblocks.pulse.Pulses;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Break burst (PRD §5.3, M1 core). A break is level event 2001 together with the spot turning to air or fluid within a
 * tick, in either order. The block holds for a beat with a short squash (the hit-stop), then shrinks away about its
 * centre while chunky chips cut from its own texture spray out (the user's pick 2026-10-29, after rejecting mini copies
 * of the block: "spawns many times the same block just smaller"). Item entities that spawn at its centre right after (the drops, one round trip later on a server) are
 * claimed: hidden until the burst opens, then drawn springing out of the centre to where they really are.
 */
public final class Bursts {
    static final String HOLD = Tune.def("burst.hold", 1);
    /** Squash at the end of the hold: height shrinks by this share, width grows by half of it. */
    static final String SQUASH = Tune.def("burst.squash", 0.12);
    static final String SHRINK = Tune.def("burst.shrink", 4);
    static final String CHIPS = Tune.def("burst.chips", 10);
    static final String CHIP_SIZE = Tune.def("burst.chipSize", 2.2);
    static final String CHIP_SPEED = Tune.def("burst.chipSpeed", 1.6);
    static final String DROP_SPRING = Tune.def("drop.spring", 6);
    /**
     * Watchdog (PRD pillar 4): a claimed drop is shown after this many ticks whatever happens, and at once while the
     * clock stands still. Ticks, not wall time, so an offline render (a Flashback export) keeps its drops in the burst.
     */
    static final long HIDE_LIMIT_TICKS = 6;
    /** Bursts per tick across the world (PRD §5.3 budget): full ones, then lite ones (no chips), then vanilla only. */
    static final int FULL_BUDGET = 8, LITE_BUDGET = 32;
    /** Full effect within this distance, lite to {@link #RANGE}, nothing beyond. */
    static final double FULL_RANGE = 32, RANGE = 64;
    /** A denied placement's copy shrinks out over this many ticks (PRD §5.1). */
    static final double FADE_TICKS = 3;
    /**
     * Chips made per frame at most. With many mods installed a chip costs ~55 µs to make (measured 2026-10-31 in the
     * user's 42-mod profile: 80 chips, 4.4 ms in one frame), so a mass break's chips spread over a few frames.
     */
    static final int CHIPS_PER_FRAME = 16;
    private static int frameChips;
    /** Several drops leave the burst one after another, this many ticks apart (the fan). */
    static final double FAN_STEP = 0.5;

    static final class Burst {
        final BlockPos pos;
        final BlockState state;
        /** When the burst's timeline starts: once the chunk mesh without the block is live (see {@link #live}). */
        double t0;
        final double born;
        /** Util.getNanos() at the break: a mesh compiled after this no longer has the block. */
        final long breakNs = Util.getNanos();
        final long breakWallNs = System.nanoTime();
        /**
         * The chunk mesh still shows the broken block for a frame or more after the break; drawing the squash over it
         * shows both. The timeline waits until a mesh compiled after the break is live (the place pop's hand-off rule),
         * or {@link #MESH_LIMIT_NS}.
         */
        boolean live;
        final double cx, cy, cz;
        /** The block as it stood, lit by the real level around it (same look as the terrain block it replaces). */
        final PopRenderState whole = new PopRenderState();
        boolean chipped;
        int first = -1;
        /** No chips (the budget's lite tier, or a fade). */
        boolean lite;
        /** The block model is empty (a chest: all block-entity renderer): nothing to shrink, no hold. */
        boolean bare;
        /** A denied placement shrinking out from {@code from} about a pivot: no hold, no squash, no claims. */
        boolean fade;
        double from = 1;
        float px = 0.5f, py = 0.5f, pz = 0.5f;
        final double claimUntil;
        int claims;

        Burst(BlockPos pos, BlockState state, double t0, double claimUntil) {
            this.pos = pos;
            this.state = state;
            this.t0 = t0;
            this.born = t0;
            this.cx = pos.getX() + 0.5;
            this.cy = pos.getY() + 0.5;
            this.cz = pos.getZ() + 0.5;
            this.claimUntil = claimUntil;
        }
    }

    /** A claimed drop: hidden until {@code revealAt}, then sprung from the burst centre. */
    static final class Claim {
        final double ox, oy, oz;
        final Burst burst;
        /** Ticks after the burst's timeline starts. */
        final double revealAfter;
        /** Lift of the arc out of the burst, for one of several drops. */
        double arc;
        final long hiddenTick = Clock.ticks();
        double shownAt = Double.NaN;

        Claim(Burst burst, double revealAfter) {
            this.burst = burst;
            this.ox = burst.cx;
            this.oy = burst.cy;
            this.oz = burst.cz;
            this.revealAfter = revealAfter;
        }
    }

    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final Long2ObjectOpenHashMap<double[]> EVENTS = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<Object[]> REMOVED = new Long2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<Claim> CLAIMS = new Int2ObjectOpenHashMap<>();
    /** Items that arrived in the last ticks: a server sends a break's drops before its block update. */
    private static final List<double[]> RECENT = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    /** (sectionNode, compileTaskStartTimeNs) of meshes that went live; filled from mesh threads too. */
    private static final ConcurrentLinkedQueue<long[]> LIVE = new ConcurrentLinkedQueue<>();
    static final long MESH_LIMIT_NS = 150_000_000L;
    private static long budgetTick = -1;
    private static int budgetUsed;

    public static int started, claimed, revealed, watchdogs, overBudget, lite, fades, denied, meshWaits, stopReveals;
    public static Chip.Material lastMaterial;
    /** Ticks from a burst's start to the arrival of a drop it claimed: the round trip as the player sees it. */
    public static double maxClaimDelay;
    public static long spentNanos;
    /** The part of spentNanos spent making chips (test hook). */
    public static long chipNanos;

    private Bursts() {}

    public static void clear() {
        BURSTS.clear();
        EVENTS.clear();
        REMOVED.clear();
        CLAIMS.clear();
        RECENT.clear();
    }

    public static int active() {
        return BURSTS.size();
    }

    public static int hiddenDrops() {
        int n = 0;
        for (Claim c : CLAIMS.values()) if (Double.isNaN(c.shownAt)) n++;
        return n;
    }

    /** LevelEventHandler.levelEvent. */
    public static void levelEvent(int type, BlockPos pos, int data) {
        if (type != LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK || !on()) return;
        long key = pos.asLong();
        Object[] removed = REMOVED.remove(key);
        BlockState state = Block.stateById(data);
        if (removed != null && Clock.ticks() - (long) removed[0] <= 1) {
            start(pos.immutable(), state.isAir() ? (BlockState) removed[1] : state);
        } else {
            EVENTS.put(key, new double[] {Clock.ticks(), data});
        }
    }

    /** Every client block change. */
    public static void blockChanged(BlockPos pos, BlockState old, BlockState now) {
        Pulses.blockChanged(pos);
        if (Probe.syncing && Probe.empty(old) && !Probe.empty(now)) {
            deny(pos, now);
            return;
        }
        if (!on() || Probe.empty(old) || !Probe.empty(now)) return;
        long key = pos.asLong();
        double[] ev = EVENTS.remove(key);
        if (ev != null && Clock.ticks() - (long) ev[0] <= 1) {
            BlockState state = Block.stateById((int) ev[1]);
            start(pos.immutable(), state.isAir() ? old : state);
        } else {
            REMOVED.put(key, new Object[] {Clock.ticks(), old});
        }
    }

    private static boolean on() {
        return Config.get().enabled && Config.get().burst;
    }

    private static void start(BlockPos pos, BlockState state) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || state.getRenderShape() != RenderShape.MODEL) return;
        if (Compat.harvestSweep && state.is(BlockTags.CROPS)) return;
        double d2 = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (d2 > RANGE * RANGE) return;
        if (budgetTick != Clock.ticks()) {
            budgetTick = Clock.ticks();
            budgetUsed = 0;
        }
        boolean full = ++budgetUsed <= FULL_BUDGET && d2 <= FULL_RANGE * FULL_RANGE;
        if (!full && budgetUsed > FULL_BUDGET + LITE_BUDGET) {
            overBudget++;
            return;
        }
        double now = Clock.now();
        Burst b = new Burst(pos, state, now, now + Rtt.ticks() + 4);
        b.lite = !full;
        b.bare = !Pops.hasQuads(state);

        setup(b.whole, level, pos, state);
        BURSTS.add(b);
        started++;
        if (b.lite) lite++;
        for (Iterator<double[]> it = RECENT.iterator(); it.hasNext(); ) {
            double[] r = it.next();
            if (tryClaim(b, (int) r[0], r[1], r[2], r[3])) it.remove();
        }
    }

    /** A denied placement (PRD §5.1): the copy, at its current scale, shrinks out about its pivot. */
    public static void fade(BlockPos pos, BlockState state, double from, float px, float py, float pz) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        double now = Clock.now();
        Burst b = new Burst(pos, state, now, now);
        b.fade = b.lite = b.chipped = b.live = true;
        b.from = from;
        b.px = px;
        b.py = py;
        b.pz = pz;
        setup(b.whole, level, pos, state);
        BURSTS.add(b);
        fades++;
    }

    /**
     * A denied break (PRD §5.3): the server put the block back while it was bursting. The shrink stops, nothing more is
     * claimed, and the block grows back from where the shrink was.
     */
    private static void deny(BlockPos pos, BlockState now) {
        double t = Clock.now();
        for (Iterator<Burst> it = BURSTS.iterator(); it.hasNext(); ) {
            Burst b = it.next();
            if (b.fade || !b.pos.equals(pos)) continue;
            it.remove();
            denied++;
            Pops.regrow(pos.immutable(), now, b.live ? shape(b, t - b.t0)[2] : 1);
            return;
        }
    }

    private static void setup(PopRenderState r, ClientLevel level, BlockPos pos, BlockState state) {
        r.level = level;
        r.blockPos = pos;
        r.randomSeedPos = pos;
        r.blockState = state;
        r.biome = level.getBiome(pos);
        r.cardinalLighting = level.cardinalLighting();
        r.lightEngine = level.getLightEngine();
    }

    /** ClientPacketListener.handleAddEntity: an item at a fresh burst's centre is its drop. */
    public static void entityAdded(int id, EntityType<?> type, double x, double y, double z) {
        if (type != EntityTypes.ITEM || !Config.get().items) return;
        double now = Clock.now();
        for (Burst b : BURSTS) {
            if (now <= b.claimUntil && tryClaim(b, id, x, y, z)) return;
        }
        RECENT.add(new double[] {id, x, y, z, Clock.ticks()});
    }

    /**
     * Items that spawn inside the broken block's cube are its drops (a block's drops spawn within 0.25 of its centre, a
     * container's contents anywhere in the cube). At most 8 per burst; they leave one after another (the fan), and
     * the rest of a big container's contents stay vanilla.
     */
    private static boolean tryClaim(Burst b, int id, double x, double y, double z) {
        if (b.fade || b.claims >= 8) return false;
        double dx = x - b.cx, dy = y - b.cy, dz = z - b.cz;
        if (Math.abs(dx) > 0.55 || Math.abs(dy) > 0.6 || Math.abs(dz) > 0.55) return false;
        int i = b.claims++;
        maxClaimDelay = Math.max(maxClaimDelay, Clock.now() - b.born);
        double open = b.bare ? 0 : Tune.get(HOLD) + 1;
        Claim c = new Claim(b, open + i * FAN_STEP);
        if (i > 0) {
            c.arc = 0.35;
            Claim first = CLAIMS.get(b.first);
            if (first != null) first.arc = 0.35;
            lastFanSpread = Math.max(lastFanSpread, i * FAN_STEP);
        } else {
            b.first = id;
        }
        CLAIMS.put(id, c);
        claimed++;
        return true;
    }

    /** The widest fan of one burst so far: ticks between its first and last reveal (test hook). */
    public static double lastFanSpread;

    /** EntityRenderDispatcher.shouldRender: a claimed drop isn't drawn until its burst opens. */
    public static boolean hidden(Entity e) {
        if (CLAIMS.isEmpty()) return false;
        Claim c = CLAIMS.get(e.getId());
        return c != null && Double.isNaN(c.shownAt);
    }

    /** ItemEntityRenderer.extractRenderState: a shown claimed drop springs from the burst centre to its real place. */
    public static void offset(int id, EntityRenderState state) {
        Claim c = CLAIMS.get(id);
        if (c == null || Double.isNaN(c.shownAt)) return;
        double u = (Clock.now() - c.shownAt) / Math.max(1, Tune.get(DROP_SPRING));
        if (u >= 1) return;
        double k = 1 - u;
        k = k * k * k;
        state.x += (c.ox - state.x) * k;
        state.y += (c.oy - state.y) * k + c.arc * 4 * u * (1 - u);
        state.z += (c.oz - state.z) * k;
    }

    /** A section mesh went live (any thread). */
    public static void meshLive(long sectionNode, long compileStartNs) {
        if (!BURSTS.isEmpty()) LIVE.add(new long[] {sectionNode, compileStartNs});
    }

    /** Render thread, every frame before drawing and every client tick. */
    public static void update() {
        double now = Clock.now();
        long wall = System.nanoTime();
        long[] l;
        while ((l = LIVE.poll()) != null) {
            for (Burst b : BURSTS) {
                if (!b.live && l[1] > b.breakNs && SectionPos.blockToSection(b.pos.asLong()) == l[0]) {
                    b.live = true;
                    b.t0 = now;
                }
            }
        }
        for (Burst b : BURSTS) {
            if (!b.live && wall - b.breakWallNs > MESH_LIMIT_NS) {
                b.live = true;
                b.t0 = now;
                meshWaits++;
            }
        }
        double spring = Tune.get(DROP_SPRING);
        for (Iterator<Claim> it = CLAIMS.values().iterator(); it.hasNext(); ) {
            Claim c = it.next();
            if (Double.isNaN(c.shownAt)) {
                boolean stopped = Clock.stopped(), late = Clock.ticks() - c.hiddenTick >= HIDE_LIMIT_TICKS;
                boolean due = c.burst.live && now >= c.burst.t0 + c.revealAfter;
                if (due || late || stopped) {
                    c.shownAt = now;
                    revealed++;
                    if (!due && stopped) stopReveals++;
                    else if (!due) watchdogs++;
                }
            } else if (now - c.shownAt > spring + 1) {
                it.remove();
            }
        }
        double life = Tune.get(HOLD) + Tune.get(SHRINK) + 1;
        BURSTS.removeIf(b -> b.live && now - b.t0 > (b.fade ? FADE_TICKS : life) && now > b.claimUntil);
        long t = Clock.ticks();
        EVENTS.values().removeIf(e -> t - (long) e[0] > 1);
        REMOVED.values().removeIf(r -> t - (long) r[0] > 1);
        RECENT.removeIf(r -> t - (long) r[4] > 2);
    }

    /**
     * Chunky chips cut from the block's texture, outward from the centre. Their flight follows the material (glass
     * flatter and faster, leaves drift, sand and gravel fall sooner, wool soft).
     */
    /** End of every frame: a new chip budget. */
    public static void frame() {
        frameChips = 0;
    }

    /** Returns how many chips it made. */
    private static int chips(Burst b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || b.lite || !b.state.shouldSpawnTerrainParticles()) return 0;
        int n = (int) Math.round(Tune.get(CHIPS));
        switch (mc.options.particles().get()) {
            case MINIMAL -> n = 0;
            case DECREASED -> n /= 2;
            default -> {
            }
        }
        Chip.Material m = Chip.Material.of(b.state);
        lastMaterial = m;
        float size = (float) Tune.get(CHIP_SIZE);
        double speed = 0.2 * Tune.get(CHIP_SPEED) * Config.get().amplitude();
        for (int i = 0; i < n; i++) {
            double ox = RANDOM.nextDouble() - 0.5, oy = RANDOM.nextDouble() - 0.5, oz = RANDOM.nextDouble() - 0.5;
            Chip c = new Chip(mc.level, b.cx + ox * 0.8, b.cy + oy * 0.8, b.cz + oz * 0.8, 0, 0, 0, b.state, b.pos);
            c.fly(m, ox * speed * 2, (oy + 0.6) * speed, oz * speed * 2);
            c.scale(size * (0.75f + 0.5f * RANDOM.nextFloat()));
            mc.particleEngine.add(c);
        }
        return n;
    }

    /**
     * {sxz, sy, s} at {@code t} ticks: during the hold the block squashes about its floor (the punch); then the squash
     * springs back while it shrinks away about its centre, accelerating. A fade shrinks from {@code from} only.
     */
    static double[] shape(Burst b, double t) {
        if (b.fade) {
            double u = Math.min(1, t / FADE_TICKS);
            return new double[] {1, 1, b.from * (1 - u * u)};
        }
        double hold = b.bare ? 0 : Tune.get(HOLD), shrink = Math.max(1, Tune.get(SHRINK));
        double squash = Tune.get(SQUASH) * Config.get().amplitude();
        if (t < hold) {
            double a = t / Math.max(0.05, hold);
            a = 1 - (1 - a) * (1 - a);
            return new double[] {1 + squash * 0.5 * a, 1 - squash * a, 1};
        }
        double u = Math.min(1, (t - hold) / shrink);
        double back = Math.max(0, 1 - u / 0.35);
        return new double[] {1 + squash * 0.5 * back, 1 - squash * back, 1 - u * u};
    }

    /** With the pops, in the transient-block pass. */
    public static void submit(PoseStack ps, SubmitNodeCollector collector, LevelRenderState state) {
        long t0 = System.nanoTime();
        try {
            update();
            if (BURSTS.isEmpty()) return;
            Vec3 cam = state.cameraRenderState.pos;
            double now = Clock.now(), shrink = Math.max(1, Tune.get(SHRINK));
            for (Burst b : BURSTS) {
                if (!b.live) continue;
                double t = now - b.t0, hold = b.bare ? 0 : Tune.get(HOLD);
                if (t >= hold && !b.chipped && frameChips < CHIPS_PER_FRAME) {
                    b.chipped = true;
                    long c0 = System.nanoTime();
                    frameChips += chips(b);
                    chipNanos += System.nanoTime() - c0;
                }
                if (b.bare || t >= (b.fade ? FADE_TICKS : hold + shrink)) continue;
                double[] sc = shape(b, t);
                ps.pushPose();
                if (b.fade) {
                    ps.translate(b.pos.getX() - cam.x, b.pos.getY() - cam.y, b.pos.getZ() - cam.z);
                    ps.translate(b.px, b.py, b.pz);
                    ps.scale((float) sc[2], (float) sc[2], (float) sc[2]);
                    ps.translate(-b.px, -b.py, -b.pz);
                } else {
                    ps.translate(b.cx - cam.x, b.pos.getY() - cam.y, b.cz - cam.z);
                    ps.scale((float) sc[0], (float) sc[1], (float) sc[0]);
                    ps.translate(0, 0.5, 0);
                    ps.scale((float) sc[2], (float) sc[2], (float) sc[2]);
                    ps.translate(-0.5, -0.5, -0.5);
                }
                collector.submitMovingBlock(ps, b.whole, 0);
                ps.popPose();
            }
        } finally {
            spentNanos += System.nanoTime() - t0;
        }
    }
}
