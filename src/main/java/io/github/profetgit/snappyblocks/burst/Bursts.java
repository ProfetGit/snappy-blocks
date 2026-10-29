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
import net.minecraft.client.renderer.block.MovingBlockRenderState;
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
import org.joml.Quaternionf;

/**
 * Break burst (PRD §5.3, M1 core). A break is level event 2001 together with the spot turning to air or fluid within a
 * tick, in either order. The block cracks into mini copies of itself that hold for a beat (the hit-stop), then fly and
 * shrink away. Item entities that spawn at its centre right after (the drops, one round trip later on a server) are
 * claimed: hidden until the burst opens, then drawn springing out of the centre to where they really are.
 */
public final class Bursts {
    static final String HOLD = Tune.def("burst.hold", 1);
    static final String PIECES = Tune.def("burst.pieces", 6);
    static final String FLIGHT = Tune.def("burst.flight", 12);
    static final String SPEED = Tune.def("burst.speed", 0.16);
    static final String LIFT = Tune.def("burst.lift", 0.22);
    static final String PIECE_SCALE = Tune.def("burst.pieceScale", 0.42);
    static final String DROP_SPRING = Tune.def("drop.spring", 6);
    static final double GRAVITY = 0.08;
    /** Frame-time watchdog (PRD pillar 4): a claimed drop is shown after this long whatever happens. */
    static final long HIDE_LIMIT_NS = 300_000_000L;
    /** Full bursts per tick across the world (PRD §5.3 budget); beyond it, vanilla crumbs only. */
    static final int BUDGET = 8;

    static final class Piece {
        double x0, y0, z0, vx, vy, vz, floor;
        float ax, ay, az, spin;
        final PieceRenderState render = new PieceRenderState();
    }

    static final class Burst {
        final BlockPos pos;
        final BlockState state;
        final double t0;
        final double cx, cy, cz;
        final List<Piece> pieces = new ArrayList<>();
        final MovingBlockRenderState whole = new MovingBlockRenderState();
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
    private static final Quaternionf Q = new Quaternionf();
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
        int n = (int) Math.round(Tune.get(PIECES));
        double speed = Tune.get(SPEED) * Config.get().amplitude(), lift = Tune.get(LIFT) * Config.get().amplitude();
        boolean solidBelow = level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
        int[] corners = shuffledCorners();
        for (int i = 0; i < n; i++) {
            int c = corners[i % 8];
            double ox = (c & 1) == 0 ? -0.25 : 0.25, oy = (c & 2) == 0 ? -0.25 : 0.25, oz = (c & 4) == 0 ? -0.25 : 0.25;
            Piece p = new Piece();
            p.x0 = b.cx + ox;
            p.y0 = b.cy + oy;
            p.z0 = b.cz + oz;
            double jitter = 0.75 + 0.5 * RANDOM.nextDouble();
            p.vx = ox * 4 * speed * jitter;
            p.vz = oz * 4 * speed * jitter;
            p.vy = lift * (0.6 + 0.6 * RANDOM.nextDouble()) + oy * 2 * speed;
            p.floor = solidBelow ? pos.getY() : pos.getY() - 64;
            double ax = RANDOM.nextGaussian(), ay = RANDOM.nextGaussian(), az = RANDOM.nextGaussian();
            double len = Math.max(1e-3, Math.sqrt(ax * ax + ay * ay + az * az));
            p.ax = (float) (ax / len);
            p.ay = (float) (ay / len);
            p.az = (float) (az / len);
            p.spin = (float) ((0.25 + 0.25 * RANDOM.nextDouble()) * (RANDOM.nextBoolean() ? 1 : -1));
            setup(p.render, level, pos, state);
            b.pieces.add(p);
        }
        setup(b.whole, level, pos, state);
        BURSTS.add(b);
        started++;
        for (Iterator<double[]> it = RECENT.iterator(); it.hasNext(); ) {
            double[] r = it.next();
            if (tryClaim(b, (int) r[0], r[1], r[2], r[3])) it.remove();
        }
    }

    private static void setup(MovingBlockRenderState r, ClientLevel level, BlockPos pos, BlockState state) {
        r.blockPos = pos;
        r.randomSeedPos = pos;
        r.blockState = state;
        r.biome = level.getBiome(pos);
        r.cardinalLighting = level.cardinalLighting();
        r.lightEngine = level.getLightEngine();
    }

    private static int[] shuffledCorners() {
        int[] c = {0, 1, 2, 3, 4, 5, 6, 7};
        for (int i = 7; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1), t = c[i];
            c[i] = c[j];
            c[j] = t;
        }
        return c;
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
        double life = Tune.get(HOLD) + Tune.get(FLIGHT) + 3;
        BURSTS.removeIf(b -> now - b.t0 > life && now > b.claimUntil);
        long t = Clock.ticks();
        EVENTS.values().removeIf(e -> t - (long) e[0] > 1);
        REMOVED.values().removeIf(r -> t - (long) r[0] > 1);
        RECENT.removeIf(r -> t - (long) r[4] > 2);
    }

    /** With the pops, in the transient-block pass. */
    public static void submit(PoseStack ps, SubmitNodeCollector collector, LevelRenderState state) {
        long t0 = System.nanoTime();
        try {
            update();
            if (BURSTS.isEmpty()) return;
            Vec3 cam = state.cameraRenderState.pos;
            double now = Clock.now(), hold = Tune.get(HOLD), flight = Tune.get(FLIGHT);
            float scale0 = (float) Tune.get(PIECE_SCALE);
            for (Burst b : BURSTS) {
                double t = now - b.t0;
                if (t > hold + flight + 3) continue;
                if (t < hold) {
                    // the hit-stop: the whole block holds for a beat where it stood, then cracks
                    ps.pushPose();
                    ps.translate(b.pos.getX() - cam.x + 0.5, b.pos.getY() - cam.y + 0.5, b.pos.getZ() - cam.z + 0.5);
                    ps.scale(1.002f, 1.002f, 1.002f);
                    ps.translate(-0.5f, -0.5f, -0.5f);
                    collector.submitMovingBlock(ps, b.whole, 0);
                    ps.popPose();
                    continue;
                }
                for (Piece p : b.pieces) {
                    double tf = Math.max(0, t - hold);
                    double x = p.x0 + p.vx * tf, z = p.z0 + p.vz * tf;
                    double y = p.y0 + p.vy * tf - 0.5 * GRAVITY * tf * tf;
                    // each piece starts as its corner of the block (half size) and shrinks to its flight size
                    float s = (float) (scale0 + (0.5 - scale0) * Math.max(0, 1 - tf / 3));
                    double low = p.floor + s * 0.5;
                    float angle = (float) (p.spin * tf);
                    if (y < low) {
                        y = low;
                        angle *= 0.6f;
                    }
                    double end = t - hold - flight;
                    if (end > 0) s *= (float) Math.max(0, 1 - end / 3);
                    if (s <= 0.01f) continue;
                    p.render.lightPos.set(Math.floor(x), Math.floor(y), Math.floor(z));
                    ps.pushPose();
                    ps.translate(x - cam.x, y - cam.y, z - cam.z);
                    Q.identity().rotateAxis(angle, p.ax, p.ay, p.az);
                    ps.rotateAround(Q, 0, 0, 0);
                    ps.scale(s, s, s);
                    ps.translate(-0.5f, -0.5f, -0.5f);
                    collector.submitMovingBlock(ps, p.render, 0);
                    ps.popPose();
                }
            }
        } finally {
            spentNanos += System.nanoTime() - t0;
        }
    }
}
