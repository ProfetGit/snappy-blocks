package io.github.profetgit.snappyblocks.burst;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.snappyblocks.Clock;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.Probe;
import io.github.profetgit.snappyblocks.Rtt;
import io.github.profetgit.snappyblocks.Tune;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import io.github.profetgit.snappyblocks.pop.PopRenderState;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
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
    /** Frame-time watchdog (PRD pillar 4): a claimed drop is shown after this long whatever happens. */
    static final long HIDE_LIMIT_NS = 300_000_000L;
    /** Full bursts per tick across the world (PRD §5.3 budget); beyond it, vanilla crumbs only. */
    static final int BUDGET = 8;

    static final class Burst {
        final BlockPos pos;
        final BlockState state;
        final double t0;
        final double cx, cy, cz;
        /** The block as it stood, lit by the real level around it (same look as the terrain block it replaces). */
        final PopRenderState whole = new PopRenderState();
        boolean chipped;
        final double claimUntil;
        int claims;

        Burst(BlockPos pos, BlockState state, double t0, double claimUntil) {
            this.pos = pos;
            this.state = state;
            this.t0 = t0;
            this.cx = pos.getX() + 0.5;
            this.cy = pos.getY() + 0.5;
            this.cz = pos.getZ() + 0.5;
            this.claimUntil = claimUntil;
        }
    }

    /** A claimed drop: hidden until {@code revealAt}, then sprung from the burst centre. */
    static final class Claim {
        final double ox, oy, oz;
        final double revealAt;
        final long hiddenNs = System.nanoTime();
        double shownAt = Double.NaN;

        Claim(double ox, double oy, double oz, double revealAt) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.revealAt = revealAt;
        }
    }

    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final Long2ObjectOpenHashMap<double[]> EVENTS = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<Object[]> REMOVED = new Long2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<Claim> CLAIMS = new Int2ObjectOpenHashMap<>();
    /** Items that arrived in the last ticks: a server sends a break's drops before its block update. */
    private static final List<double[]> RECENT = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static long budgetTick = -1;
    private static int budgetUsed;

    public static int started, claimed, revealed, watchdogs, overBudget;
    /** Ticks from a burst's start to the arrival of a drop it claimed: the round trip as the player sees it. */
    public static double maxClaimDelay;
    public static long spentNanos;

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
        if (level == null || state.getRenderShape() != RenderShape.MODEL) return;
        if (mc.player == null || mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 * 64) return;
        if (budgetTick != Clock.ticks()) {
            budgetTick = Clock.ticks();
            budgetUsed = 0;
        }
        if (++budgetUsed > BUDGET) {
            overBudget++;
            return;
        }
        double now = Clock.now();
        Burst b = new Burst(pos, state, now, now + Rtt.ticks() + 4);
        setup(b.whole, level, pos, state);
        BURSTS.add(b);
        started++;
        for (Iterator<double[]> it = RECENT.iterator(); it.hasNext(); ) {
            double[] r = it.next();
            if (tryClaim(b, (int) r[0], r[1], r[2], r[3])) it.remove();
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

    private static boolean tryClaim(Burst b, int id, double x, double y, double z) {
        if (b.claims >= 8) return false;
        double dx = x - b.cx, dy = y - b.cy, dz = z - b.cz;
        if (dx * dx + dy * dy + dz * dz > 0.6 * 0.6) return false;
        b.claims++;
        maxClaimDelay = Math.max(maxClaimDelay, Clock.now() - b.t0);
        CLAIMS.put(id, new Claim(b.cx, b.cy, b.cz, b.t0 + Tune.get(HOLD) + 1));
        claimed++;
        return true;
    }

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
        state.y += (c.oy - state.y) * k;
        state.z += (c.oz - state.z) * k;
    }

    private static void update() {
        double now = Clock.now();
        long wall = System.nanoTime();
        double spring = Tune.get(DROP_SPRING);
        for (Iterator<Claim> it = CLAIMS.values().iterator(); it.hasNext(); ) {
            Claim c = it.next();
            if (Double.isNaN(c.shownAt)) {
                boolean late = wall - c.hiddenNs > HIDE_LIMIT_NS;
                if (now >= c.revealAt || late) {
                    c.shownAt = now;
                    revealed++;
                    if (late && now < c.revealAt) watchdogs++;
                }
            } else if (now - c.shownAt > spring + 1) {
                it.remove();
            }
        }
        double life = Tune.get(HOLD) + Tune.get(SHRINK) + 1;
        BURSTS.removeIf(b -> now - b.t0 > life && now > b.claimUntil);
        long t = Clock.ticks();
        EVENTS.values().removeIf(e -> t - (long) e[0] > 1);
        REMOVED.values().removeIf(r -> t - (long) r[0] > 1);
        RECENT.removeIf(r -> t - (long) r[4] > 2);
    }

    /** Chunky chips cut from the block's texture (vanilla terrain particles, bigger and faster), outward from the centre. */
    private static void chips(Burst b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !b.state.shouldSpawnTerrainParticles()) return;
        int n = (int) Math.round(Tune.get(CHIPS));
        switch (mc.options.particles().get()) {
            case MINIMAL -> n = 0;
            case DECREASED -> n /= 2;
            default -> {
            }
        }
        float size = (float) Tune.get(CHIP_SIZE), power = (float) (Tune.get(CHIP_SPEED) * Config.get().amplitude());
        for (int i = 0; i < n; i++) {
            double ox = RANDOM.nextDouble() - 0.5, oy = RANDOM.nextDouble() - 0.5, oz = RANDOM.nextDouble() - 0.5;
            TerrainParticle p = new TerrainParticle(mc.level, b.cx + ox * 0.8, b.cy + oy * 0.8, b.cz + oz * 0.8, ox, oy, oz, b.state, b.pos);
            p.setPower(power);
            p.scale(size * (0.75f + 0.5f * RANDOM.nextFloat()));
            mc.particleEngine.add(p);
        }
    }

    /** With the pops, in the transient-block pass. */
    public static void submit(PoseStack ps, SubmitNodeCollector collector, LevelRenderState state) {
        long t0 = System.nanoTime();
        try {
            update();
            if (BURSTS.isEmpty()) return;
            Vec3 cam = state.cameraRenderState.pos;
            double now = Clock.now(), hold = Tune.get(HOLD), shrink = Math.max(1, Tune.get(SHRINK));
            double squash = Tune.get(SQUASH) * Config.get().amplitude();
            for (Burst b : BURSTS) {
                double t = now - b.t0;
                if (t >= hold && !b.chipped) {
                    b.chipped = true;
                    chips(b);
                }
                if (t >= hold + shrink) continue;
                // hold: squash in about the floor (the punch); shrink: the squash springs back while the block shrinks
                // away about its centre, accelerating
                double sy, sxz, s = 1;
                if (t < hold) {
                    double a = t / Math.max(0.05, hold);
                    a = 1 - (1 - a) * (1 - a);
                    sy = 1 - squash * a;
                    sxz = 1 + squash * 0.5 * a;
                } else {
                    double u = (t - hold) / shrink;
                    double back = Math.max(0, 1 - u / 0.35);
                    sy = 1 - squash * back;
                    sxz = 1 + squash * 0.5 * back;
                    s = 1 - u * u;
                }
                ps.pushPose();
                ps.translate(b.cx - cam.x, b.pos.getY() - cam.y, b.cz - cam.z);
                ps.scale((float) sxz, (float) sy, (float) sxz);
                ps.translate(0, 0.5, 0);
                ps.scale((float) s, (float) s, (float) s);
                ps.translate(-0.5, -0.5, -0.5);
                collector.submitMovingBlock(ps, b.whole, 0);
                ps.popPose();
            }
        } finally {
            spentNanos += System.nanoTime() - t0;
        }
    }
}
