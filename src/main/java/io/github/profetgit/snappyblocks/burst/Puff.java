package io.github.profetgit.snappyblocks.burst;

import io.github.profetgit.snappyblocks.Clock;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.Tune;
import io.github.profetgit.snappyblocks.pop.Pops;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The placing effect (config `placeEffect`), one at a time, when a placed block reaches the peak of its pop. Bits:
 * small pieces of the block burst from the edges of the face it was set against, along that face. Explosion: pieces of
 * the block fly off every free side of it. Poof and shockwave: dust ({@link Dust}).
 */
public final class Puff {
    static final String CHIPS = Tune.def("place.chips", 12);
    static final String SPEED = Tune.def("place.speed", 1.25);
    static final String SIZE = Tune.def("place.size", 1);
    static final String BLAST = Tune.def("place.explosion", 20);
    private static final double RANGE = 32;
    private static final int CAP = 32;
    /** A puff's chips also leave the surface it was set on, and lift a little (blocks per tick, times place.speed). */
    public static final double AWAY = 0.035, UP = 0.03;
    /** A puff chip lasts this many ticks, plus up to as many again. */
    public static final int LIFE = 9;
    /** An explosion chip lasts this many ticks, plus up to the spread. */
    public static final int BLAST_LIFE = 10, BLAST_SPREAD = 8;

    public enum Effect { OFF, BITS, POOF, SHOCKWAVE, EXPLOSION }

    /** The config file's names, in the order of {@link Effect}. */
    public static final String[] EFFECTS = {"off", "bits", "poof", "shockwave", "explosion"};

    private record Waiting(BlockPos pos, BlockState state, Direction support, double due) {}

    private static final List<Waiting> WAITING = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    /** Puffs made and their chips (test hooks). */
    public static int puffs, chipsMade;

    private Puff() {}

    static void clear() {
        WAITING.clear();
    }

    public static Effect effect() {
        if (!Config.get().enabled) return Effect.OFF;
        int i = java.util.Arrays.asList(EFFECTS).indexOf(Config.get().placeEffect);
        return i < 0 ? Effect.OFF : Effect.values()[i];
    }

    /** Whether a placement does anything here. */
    public static boolean on() {
        return effect() != Effect.OFF;
    }

    /** A block was placed at {@code pos} against the face towards {@code support}; its puff comes at the pop's peak. */
    public static void queue(BlockPos pos, BlockState state, Direction support, double t0) {
        if (!on() || WAITING.size() >= CAP) return;
        WAITING.add(new Waiting(pos.immutable(), state, support, t0 + delay()));
    }

    /** Ticks from the placement to the puff: the pop's peak. */
    public static double delay() {
        double pop = Config.get().pop ? Pops.popTicks() : Tune.get(Tune.POP_TICKS);
        return Math.max(0.05, Tune.get(Tune.POP_PEAK_AT)) * pop;
    }

    /** The placing effect's own strength (config `placeStrength`) as a factor. */
    public static double strength() {
        return Math.max(25, Math.min(200, Config.get().placeStrength)) / 100.0;
    }

    /** The launch speed factor. */
    public static double speed() {
        return Tune.get(SPEED) * Config.get().amplitude() * (0.4 + 0.6 * strength());
    }

    /** A puff chip's size factor; {@code r} in 0..1. */
    public static float size(double r) {
        return (float) (Tune.get(SIZE) * (0.8 + 0.5 * r));
    }

    /** Chips per puff, after the particles video setting. */
    public static int chipCount() {
        return limited((int) Math.round(Tune.get(CHIPS)));
    }

    /** Chips per explosion, after the particles video setting. */
    public static int blastCount() {
        return limited((int) Math.round(Tune.get(BLAST)));
    }

    private static int limited(int base) {
        int n = (int) Math.round(base * strength());
        return switch (Minecraft.getInstance().options.particles().get()) {
            case MINIMAL -> 0;
            case DECREASED -> n / 2;
            default -> n;
        };
    }

    /** Render thread, every frame. */
    static void update() {
        if (WAITING.isEmpty()) return;
        double now = Clock.now();
        for (Iterator<Waiting> it = WAITING.iterator(); it.hasNext(); ) {
            Waiting w = it.next();
            if (now < w.due) continue;
            Effect effect = effect();
            int n = effect == Effect.BITS ? chipCount() : effect == Effect.EXPLOSION ? blastCount() : 0;
            if (n > 0 && Bursts.spend(n) == 0) return;
            it.remove();
            puff(w, effect, n);
        }
    }

    /**
     * The {@code i}-th chip of {@code n} round a contact face: {offset along the face's first axis, along its second,
     * speed along the first, along the second}, offsets in -0.5..0.5 of the face, going round its four edges.
     * {@code r}, {@code s} in 0..1 spread it.
     */
    public static double[] edge(int i, double r, double s) {
        // each side's chips take turns in its thirds, so they ring the block instead of bunching
        return ring(i & 3, ((i >> 2) % 3 + r) / 3 - 0.5, s);
    }

    /** {@link #edge} for the point {@code along} (-0.5..0.5) side {@code side} (0..3). */
    public static double[] ring(int side, double along, double s) {
        double out = 0.5 + 0.05 * s;
        double speed = 0.07 + 0.05 * s, drift = along * 0.05;
        double[] e = (side & 2) == 0 ? new double[] {along, out, drift, speed} : new double[] {out, along, speed, drift};
        if ((side & 1) != 0) {
            int a = (side & 2) == 0 ? 1 : 0;
            e[a] = -e[a];
            e[a + 2] = -e[a + 2];
        }
        return e;
    }

    /**
     * An explosion chip's speed off side {@code face} (blocks per tick, before its material and place.speed): out of
     * the side, spread across it by {@code a, b} in -0.5..0.5, and up; {@code r} in 0..1 sets how hard.
     */
    public static double[] blast(Direction face, double a, double b, double r) {
        double out = 0.07 + 0.08 * r;
        double[] v = {face.getStepX() * out, face.getStepY() * out, face.getStepZ() * out};
        Direction.Axis[] across = across(face.getAxis());
        v[across[0].ordinal()] += a * 0.08;
        v[across[1].ordinal()] += b * 0.08;
        v[1] += 0.07;
        return v;
    }

    /** The two axes of a face on {@code axis}. */
    public static Direction.Axis[] across(Direction.Axis axis) {
        return new Direction.Axis[] {axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X,
            axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z};
    }

    /** An explosion chip's size factor; {@code r} in 0..1: mostly small, a few big. */
    public static float blastSize(double r) {
        return (float) (Tune.get(SIZE) * (0.6 + 0.6 * r * r));
    }

    /** One placement's effect, round the block and the face it was set against. */
    private static void puff(Waiting w, Effect effect, int chips) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        BlockState state = w.state;
        if (effect == Effect.OFF || level == null || level.getBlockState(w.pos).getBlock() != state.getBlock()) return;
        if (mc.gameRenderer.mainCamera().position().distanceToSqr(w.pos.getX() + 0.5, w.pos.getY() + 0.5, w.pos.getZ() + 0.5) > RANGE * RANGE) return;
        VoxelShape shape = state.getShape(level, w.pos);
        AABB box = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
        if (effect == Effect.POOF || effect == Effect.SHOCKWAVE) {
            dust(w, level, box, effect == Effect.POOF ? Dust.Style.POOF : Dust.Style.SHOCKWAVE);
            return;
        }
        if (chips == 0 || !state.shouldSpawnTerrainParticles()) return;
        if (effect == Effect.EXPLOSION) {
            explode(w, level, box, chips);
            return;
        }
        Direction.Axis axis = w.support.getAxis();
        Direction.Axis first = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        Direction.Axis second = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
        double speed = speed();
        Chip.Material m = Chip.Material.of(state);
        int made = 0;
        int turn = RANDOM.nextInt(4);
        for (int i = 0; i < chips; i++) {
            double[] e = edge(i + turn, RANDOM.nextDouble(), RANDOM.nextDouble());
            // an edge with a full block beside it has nothing to puff into
            Direction.Axis outAxis = Math.abs(e[0]) > Math.abs(e[1]) ? first : second;
            double outSign = Math.abs(e[0]) > Math.abs(e[1]) ? e[0] : e[1];
            Direction side = Direction.fromAxisAndDirection(outAxis, outSign > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
            if (full(level, w.pos.relative(side))) continue;
            float size = size(RANDOM.nextDouble());
            // just clear of the block and of the surface it was set on, or the chip would start inside them
            double clear = Chip.half(size) + 0.01;
            double[] p = new double[3], v = new double[3];
            put(p, first, mid(box, first) + e[0] * span(box, first) + (outAxis == first ? Math.signum(outSign) * clear : 0));
            put(p, second, mid(box, second) + e[1] * span(box, second) + (outAxis == second ? Math.signum(outSign) * clear : 0));
            double face = w.support.getAxisDirection() == Direction.AxisDirection.POSITIVE ? box.max(axis) : box.min(axis);
            put(p, axis, face - w.support.getAxisDirection().getStep() * clear);
            put(v, first, e[2] * speed);
            put(v, second, e[3] * speed);
            put(v, axis, -w.support.getAxisDirection().getStep() * AWAY * speed);
            v[1] += UP * speed;
            Chip c = new Chip(level, w.pos.getX() + p[0], w.pos.getY() + p[1], w.pos.getZ() + p[2], 0, 0, 0, state, w.pos);
            c.sized(size);
            c.fly(m, v[0], v[1], v[2], LIFE + RANDOM.nextInt(LIFE));
            mc.particleEngine.add(c);
            made++;
        }
        if (made > 0) puffs++;
        chipsMade += made;
    }

    /** Pieces of the block off every side of it that is free: not the one it stands on, not against a full block. */
    private static void explode(Waiting w, ClientLevel level, AABB box, int n) {
        List<Direction> sides = new ArrayList<>();
        for (Direction d : Direction.values()) if (d != w.support && !full(level, w.pos.relative(d))) sides.add(d);
        if (sides.isEmpty()) return;
        Chip.Material m = Chip.Material.of(w.state);
        double speed = speed() / 1.25;
        int made = 0;
        for (int i = 0; i < n; i++) {
            Direction side = sides.get(i % sides.size());
            Direction.Axis axis = side.getAxis();
            Direction.Axis[] across = across(axis);
            double a = RANDOM.nextDouble() - 0.5, b = RANDOM.nextDouble() - 0.5;
            float size = blastSize(RANDOM.nextDouble());
            double clear = Chip.half(size) + 0.01;
            double[] p = new double[3];
            put(p, across[0], mid(box, across[0]) + a * span(box, across[0]));
            put(p, across[1], mid(box, across[1]) + b * span(box, across[1]));
            boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            put(p, axis, (positive ? box.max(axis) : box.min(axis)) + (positive ? clear : -clear));
            // a side chip starting at the floor would start inside it
            if (w.support == Direction.DOWN) p[1] = Math.max(p[1], clear);
            double[] raw = blast(side, a, b, RANDOM.nextDouble());
            double[] v = m.speed(raw[0] * speed, raw[1] * speed, raw[2] * speed);
            Chip c = new Chip(level, w.pos.getX() + p[0], w.pos.getY() + p[1], w.pos.getZ() + p[2], 0, 0, 0, w.state, w.pos);
            c.sized(size);
            c.fly(m, v[0], v[1], v[2], BLAST_LIFE + RANDOM.nextInt(BLAST_SPREAD));
            Minecraft.getInstance().particleEngine.add(c);
            made++;
        }
        if (made > 0) puffs++;
        chipsMade += made;
    }

    private static void dust(Waiting w, ClientLevel level, AABB box, Dust.Style style) {
        Direction.Axis axis = w.support.getAxis();
        Direction.Axis first = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        Direction.Axis second = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
        double away = -w.support.getAxisDirection().getStep(), speed = Dust.speed();
        double face = w.support.getAxisDirection() == Direction.AxisDirection.POSITIVE ? box.max(axis) : box.min(axis);
        int rgb = Dust.color(w.state.getMapColor(level, w.pos).col);
        // the middle of the contact face, in the world
        double[] mid = new double[3], up = new double[3];
        put(mid, first, mid(box, first));
        put(mid, second, mid(box, second));
        put(mid, axis, face);
        for (Direction.Axis a : Direction.Axis.values()) mid[a.ordinal()] += w.pos.get(a);
        put(up, axis, away);
        int n = Dust.count(style);
        if (n == 0) return;
        if (style == Dust.Style.POOF) {
            for (int i = 0; i < n; i++) {
                double[] e = Dust.ring(i, n, RANDOM.nextDouble(), RANDOM.nextDouble());
                if (full(level, w.pos.relative(side(first, second, e[0], e[1])))) continue;
                double a = e[0] * span(box, first), b = e[1] * span(box, second);
                double fast = speed * (0.4 + 0.3 * RANDOM.nextDouble());
                double va = e[2] * fast, vb = e[3] * fast;
                Dust.spawn(level, DustArt.Sheet.POOF, (age, out) -> {
                    double far = Dust.travel(age);
                    set(out, mid, first, a + va * far, second, b + vb * far, up, Dust.poofHeight(age));
                }, null, null, rgb, Dust.poofLife(RANDOM.nextDouble()), RANDOM.nextBoolean());
            }
            return;
        }
        // the flat ring needs a surface to lie on
        int ground = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue;
                BlockPos at = w.pos.relative(w.support).relative(along(first), i).relative(along(second), j);
                if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) ground++;
            }
        }
        if (ground >= 5) {
            Dust.spawn(level, DustArt.Sheet.RING, (age, out) -> set(out, mid, first, 0, second, 0, up, 0.015), up, open(level, w), rgb,
                Dust.ringLife(), false);
        }
        double turn = RANDOM.nextDouble();
        for (int i = 0; i < n; i++) {
            double angle = (i + turn + 0.4 * RANDOM.nextDouble()) / n * Math.PI * 2, c = Math.cos(angle), d = Math.sin(angle);
            if (full(level, w.pos.relative(side(first, second, c, d)))) continue;
            int life = Dust.wispLife(RANDOM.nextDouble());
            double ahead = 0.04 + 0.2 * RANDOM.nextDouble(), rise = 0.006 + 0.012 * RANDOM.nextDouble();
            Dust.spawn(level, DustArt.Sheet.WISP, (age, out) -> {
                double r = Dust.radius((double) age / Dust.ringLife()) + ahead * speed;
                set(out, mid, first, c * r, second, d * r, up, Dust.WISP_HALF * Dust.size() * 0.7 + rise * speed * age);
            }, null, null, rgb, life, RANDOM.nextBoolean());
        }
    }

    /** The middle of a see-through neighbour of the placed block (above it first): where the ring takes its light. */
    private static double[] open(ClientLevel level, Waiting w) {
        BlockPos best = w.pos.relative(w.support.getOpposite());
        if (level.getBlockState(best).canOcclude()) {
            for (Direction d : Direction.values()) {
                if (d != w.support && !level.getBlockState(w.pos.relative(d)).canOcclude()) {
                    best = w.pos.relative(d);
                    break;
                }
            }
        }
        return new double[] {best.getX() + 0.5, best.getY() + 0.5, best.getZ() + 0.5};
    }

    /** The side of the block that the point ({@code a}, {@code b}) on its contact face lies towards. */
    private static Direction side(Direction.Axis first, Direction.Axis second, double a, double b) {
        boolean along = Math.abs(a) > Math.abs(b);
        return Direction.fromAxisAndDirection(along ? first : second,
            (along ? a : b) > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
    }

    private static Direction along(Direction.Axis a) {
        return Direction.fromAxisAndDirection(a, Direction.AxisDirection.POSITIVE);
    }

    /** {@code mid} moved {@code a} and {@code b} along the face's axes and {@code h} off it. */
    private static void set(double[] out, double[] mid, Direction.Axis first, double a, Direction.Axis second, double b, double[] up, double h) {
        for (int k = 0; k < 3; k++) out[k] = mid[k] + up[k] * h;
        out[first.ordinal()] += a;
        out[second.ordinal()] += b;
    }

    private static boolean full(ClientLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.canOcclude() && Block.isShapeFullBlock(s.getShape(level, pos));
    }

    private static double mid(AABB b, Direction.Axis a) {
        return (b.min(a) + b.max(a)) / 2;
    }

    private static double span(AABB b, Direction.Axis a) {
        return b.max(a) - b.min(a);
    }

    private static void put(double[] to, Direction.Axis a, double v) {
        to[a.ordinal()] = v;
    }
}
