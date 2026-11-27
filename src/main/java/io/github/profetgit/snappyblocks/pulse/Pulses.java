package io.github.profetgit.snappyblocks.pulse;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.snappyblocks.Clock;
import io.github.profetgit.snappyblocks.Compat;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.Probe;
import io.github.profetgit.snappyblocks.RenderPass;
import io.github.profetgit.snappyblocks.Tune;
import io.github.profetgit.snappyblocks.burst.Chip;
import io.github.profetgit.snappyblocks.pop.Hidden;
import io.github.profetgit.snappyblocks.pop.PopRenderState;
import io.github.profetgit.snappyblocks.pop.Pops;
import io.github.profetgit.snappyblocks.pop.ShaderPack;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Mining pulse (PRD §5.2). Every mining hit (the miner's own first hit, then the server's level event 2020 every 4
 * ticks, for every miner in range; both reach ClientLevel.addBreakingBlockEffects with the sound flag) pushes the block out to pulse.peak and back in pulse.ticks, with a chip or two off
 * the hit face. Pulse and Wobble leave the block in the chunk mesh: a copy a hair larger than the block is drawn over it
 * (the bulge), so a hit never waits on a remesh. The crack overlay and the outline move with it ({@link #transform}).
 *
 * <p>The player picks what a hit does (config `pulseStyle`): Pulse bulges, Wobble stretches one way and then the other
 * like jelly, Shake twists and tips the block. Pulse and Wobble only grow, so they cover the real block. A turning copy
 * can't, so Shake leaves the block out of the mesh ({@link Hidden}) from the first hit until the hits stop, and draws
 * every side of the copy. Then the block is meshed again and the copy stays until a mesh whose compile started after
 * that is live (the rule of {@link Pops}).
 */
public final class Pulses {
    static final String PEAK = Tune.def("pulse.peak", 1.03);
    static final String TICKS = Tune.def("pulse.ticks", 2);
    static final String CHIPS = Tune.def("pulse.chips", 2);
    /** Further chips per hit as the block gives way: one more every third hit, up to this many. */
    static final String CHIPS_MORE = Tune.def("pulse.chipsMore", 2);
    /** A block's hits count as one mining while they are this close together, and its face is remembered this long. */
    static final long HIT_GAP_TICKS = 12, FACE_TICKS = 20;
    /** A hit chip lasts this many ticks, plus up to the spread. */
    public static final int LIFE = 12, LIFE_SPREAD = 8;
    /** 0: under a shader pack the bulge copy stays out (it can be lit brighter than the chunk mesh, a flicker each hit); 1: draw it. */
    static final String SHADED = Tune.def("pulse.shaded", 1);
    /** The copy never sits closer to the block than this, so it never z-fights with it (24 blocks out). */
    static final double FLOOR = 1.004;
    static final double RANGE = 24;
    static final double CRACK_OUT = 1.002;

    static final class Pulse {
        final BlockPos pos;
        final BlockState state;
        final double t0;
        /** The struck face, the hits on this block before this one and the animation, as the config had it. */
        final Direction face;
        final int hits;
        final Style style = style() == Style.SHAKE && !Compat.hideWorks() ? Style.PULSE : style();
        final PopRenderState render = new PopRenderState();
        /** Shake: the real block is out of the chunk mesh; then, from release on, the copy covers the remesh. */
        boolean hidden;
        /** Util.getNanos() when meshed again (the compile-start clock); 0 until then. */
        long releasedNs, releasedWallNs;

        Pulse(BlockPos pos, BlockState state, double t0, Direction face, int hits) {
            this.pos = pos;
            this.state = state;
            this.t0 = t0;
            this.face = face;
            this.hits = hits;
        }

        double[] shape(double now) {
            return Pulses.shape(style, now - t0, face, hits);
        }
    }

    /** The shake per hit: blocks moved, and degrees turned about the upright axis and tipped about the level one. */
    static final String SHAKE_REACH = Tune.def("twist.reach", 0.018);
    static final String SHAKE_YAW = Tune.def("twist.yaw", 7);
    static final String SHAKE_PITCH = Tune.def("twist.pitch", 5);
    static final String SHAKE_TICKS = Tune.def("twist.ticks", 3.6);
    /** Ticks of one swing there and back. */
    static final String SHAKE_PERIOD = Tune.def("twist.period", 2.2);
    /** The shaking block stays out of the mesh until this long after the last hit (hits come every 4 ticks). */
    static final double HOLD_TICKS = 7;
    /** A released copy waiting for its mesh is dropped after this long. */
    static final long COVER_LIMIT_NS = 1_000_000_000L;
    static final String WOBBLE_REACH = Tune.def("wobble.reach", 0.1);
    static final String WOBBLE_TICKS = Tune.def("wobble.ticks", 3);
    static final String WOBBLE_PERIOD = Tune.def("wobble.period", 2);

    public enum Style { PULSE, SHAKE, WOBBLE }

    /** The config file's names, in the order of {@link Style}. */
    public static final String[] STYLES = {"pulse", "shake", "wobble"};

    /** The chosen animation; PULSE while the effect is off. */
    public static Style style() {
        int i = Config.get().pulse ? java.util.Arrays.asList(STYLES).indexOf(Config.get().pulseStyle) : -1;
        return i < 0 ? Style.PULSE : Style.values()[i];
    }

    /** The mining animation's own strength (config `pulseStrength`) as a factor. */
    public static double strength() {
        return Math.max(25, Math.min(200, Config.get().pulseStrength)) / 100.0;
    }

    /** Intensity, Calm and the mining strength together. */
    public static double amplitude() {
        return Config.get().amplitude() * strength();
    }

    private static final Long2ObjectOpenHashMap<Pulse> PULSES = new Long2ObjectOpenHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private static final class Mined {
        Direction face;
        int hits;
        long tick;
    }

    /** The blocks being mined: the face last hit (the burst throws chips out of it) and the hits so far. */
    private static final Long2ObjectOpenHashMap<Mined> MINED = new Long2ObjectOpenHashMap<>();

    /** (sectionNode, compileTaskStartTimeNs) of meshes that went live; filled from mesh threads too. */
    private static final ConcurrentLinkedQueue<long[]> LIVE = new ConcurrentLinkedQueue<>();

    public static int started;
    /** Shakes that took their block out of the mesh, and released copies no mesh took over in time (test hooks). */
    public static int hid, watchdogs;
    /** Chips made by mining hits (test hook). */
    public static int chipsMade;
    public static long spentNanos;

    private Pulses() {}

    public static void clear() {
        PULSES.clear();
        MINED.clear();
        LIVE.clear();
    }

    /** The face {@code pos} was last mined from, or null (broken in one hit, or long ago). */
    public static Direction minedFace(BlockPos pos) {
        Mined m = MINED.get(pos.asLong());
        return m == null || Clock.ticks() - m.tick > FACE_TICKS ? null : m.face;
    }

    private static int count(BlockPos pos, Direction face) {
        long now = Clock.ticks();
        if (MINED.size() > 32) MINED.values().removeIf(m -> now - m.tick > FACE_TICKS);
        Mined m = MINED.get(pos.asLong());
        if (m == null || now - m.tick > HIT_GAP_TICKS) MINED.put(pos.asLong(), m = new Mined());
        m.face = face;
        m.tick = now;
        return m.hits++;
    }

    public static int active() {
        return PULSES.size();
    }

    /**
     * Opaque model blocks that the chunk mesh draws whole. Translucent blocks would double their alpha under a copy,
     * block-entity renderers aren't in the copy, and under a shader pack the per-material blocks change look in the
     * copy (the same rule as the place pop).
     */
    static boolean eligible(BlockState s) {
        return s.canOcclude() && Pops.eligible(s) && !s.hasBlockEntity();
    }

    /**
     * ClientLevel.addBreakingBlockEffects with the hit sound. The server sends the miner's first hit back one round trip
     * later: a hit on a block that is still pulsing is the same hit and is skipped.
     */
    public static void hit(BlockPos pos, Direction face) {
        if (!Config.get().enabled || !Config.get().pulse || Compat.pulseAside) return;
        Pulse running = PULSES.get(pos.asLong());
        if (running != null && Clock.now() - running.t0 < Math.max(0.5, Tune.get(TICKS))) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        if (mc.player.distanceToSqr(Vec3.atCenterOf(pos)) > RANGE * RANGE) return;
        BlockState state = level.getBlockState(pos);
        if (Probe.empty(state)) return;
        BlockPos at = pos.immutable();
        int hits = count(at, face);
        if (eligible(state) && !Pops.popping(at) && (Tune.get(SHADED) > 0.5 || !ShaderPack.active())) {
            Pulse p = new Pulse(at, state, Clock.now(), face, hits);
            p.render.init(level, at, state);
            if (p.style == Style.SHAKE) {
                p.render.allSides = true;
                p.hidden = true;
                if (running == null || !running.hidden) {
                    hid++;
                    Hidden.add(at.asLong());
                    Pops.remesh(at);
                }
            } else if (running != null) {
                unhide(running);
            }
            PULSES.put(at.asLong(), p);
            started++;
        }
        chips(level, at, state, face, hits);
    }

    /** A block changed (broken, replaced): its pulse ends at once, the burst or the new block takes over. */
    public static void blockChanged(BlockPos pos) {
        if (PULSES.isEmpty()) return;
        Pulse p = PULSES.remove(pos.asLong());
        if (p != null) unhide(p);
    }

    private static void unhide(Pulse p) {
        if (!p.hidden) return;
        p.hidden = false;
        p.releasedNs = Util.getNanos();
        p.releasedWallNs = System.nanoTime();
        Hidden.remove(p.pos.asLong());
        Pops.remesh(p.pos);
    }

    private static void chips(ClientLevel level, BlockPos pos, BlockState state, Direction face, int hits) {
        if (!state.shouldSpawnTerrainParticles()) return;
        int n = hitChips(hits);
        double amp = amplitude();
        for (int i = 0; i < n; i++) {
            double u = RANDOM.nextDouble() - 0.5, v = RANDOM.nextDouble() - 0.5;
            float size = 1.3f + 0.6f * RANDOM.nextFloat();
            double out = 0.51 + Chip.half(size);
            double x = pos.getX() + 0.5 + face.getStepX() * out, y = pos.getY() + 0.5 + face.getStepY() * out, z = pos.getZ() + 0.5 + face.getStepZ() * out;
            // spread across the face, flung out of it
            if (face.getAxis() != Direction.Axis.X) x += u * 0.8;
            if (face.getAxis() != Direction.Axis.Y) y += (face.getAxis() == Direction.Axis.X ? u : v) * 0.8;
            if (face.getAxis() != Direction.Axis.Z) z += v * 0.8;
            double[] s = chipSpeed(face, u, v, amp * hitForce(hits));
            Chip c = new Chip(level, x, y, z, 0, 0, 0, state, pos);
            c.sized(size);
            c.fly(Chip.Material.of(state), s[0], s[1], s[2], LIFE + RANDOM.nextInt(LIFE_SPREAD));
            Minecraft.getInstance().particleEngine.add(c);
            chipsMade++;
        }
    }

    /** Chips per hit, after the particles video setting. */
    public static int chipCount() {
        if (!Config.get().chips) return 0;
        int n = (int) Math.round(Tune.get(CHIPS));
        return switch (Minecraft.getInstance().options.particles().get()) {
            case MINIMAL -> 0;
            case DECREASED -> Math.min(n, 1);
            default -> n;
        };
    }

    /** Chips of the hit after {@code hits} earlier ones on the same block. */
    public static int hitChips(int hits) {
        int n = chipCount();
        // Chips off, or the particles video setting turned down: no ramp
        if (n < (int) Math.round(Tune.get(CHIPS)) || n == 0) return n;
        return n + Math.min((int) Math.round(Tune.get(CHIPS_MORE)), hits / 3);
    }

    /** Later hits throw harder, up to 1.5x. */
    public static double hitForce(int hits) {
        return 1 + 0.5 * Math.min(1, hits / 9.0);
    }

    /** A hit chip's speed (blocks per tick): out of {@code face}, a little up; {@code u}, {@code v} in -0.5..0.5 spread it. */
    public static double[] chipSpeed(Direction face, double u, double v, double amp) {
        double out = 0.12 * amp;
        return new double[] {face.getStepX() * out + u * 0.05, face.getStepY() * out + 0.08, face.getStepZ() * out + v * 0.05};
    }

    /** Scale at {@code t} ticks into a pulse: out to the peak in the first third, back by the end. */
    public static double scale(double t) {
        double ticks = Math.max(0.5, Tune.get(TICKS));
        if (t <= 0 || t >= ticks) return 1;
        double u = t / ticks;
        double bump = u < 1 / 3.0 ? 1 - Math.pow(1 - u * 3, 2) : 1 - Math.pow((u - 1 / 3.0) * 1.5, 2);
        return 1 + (Tune.get(PEAK) - 1) * amplitude() * bump;
    }

    /** Ticks one hit's animation lasts. */
    public static double duration(Style style) {
        return Math.max(0.5, Tune.get(switch (style) {
            case PULSE -> TICKS;
            case SHAKE -> SHAKE_TICKS;
            case WOBBLE -> WOBBLE_TICKS;
        }));
    }

    /**
     * {sx, sy, sz, ox, oy, oz, rx, ry, rz} {@code t} ticks into a hit on {@code face} after {@code hits} earlier ones:
     * the size about the block's centre, the move in blocks, then (Shake) the turn about the centre in radians. Pulse
     * has no turn and stops at six. The identity outside the hit.
     */
    public static double[] shape(Style style, double t, Direction face, int hits) {
        if (style == Style.PULSE) {
            double s = scale(t);
            return new double[] {s, s, s, 0, 0, 0};
        }
        // then the turn about x, y, z in radians
        double[] p = {1, 1, 1, 0, 0, 0, 0, 0, 0};
        double ticks = duration(style);
        if (t <= 0 || t >= ticks) return p;
        // quick in, easing out to the next hit; later hits are harder
        double fade = Math.min(1, t / 0.4) * (1 - t / ticks), amp = amplitude() * hitForce(hits);
        for (int i = 0; i < 3; i++) p[i] = FLOOR + 0.004;
        // the two axes in the plane of the struck face
        int u = face.getAxis() == Direction.Axis.X ? 2 : 0, v = face.getAxis() == Direction.Axis.Y ? 2 : 1;
        if (style == Style.SHAKE) {
            // every other hit twists the other way
            double reach = Tune.get(SHAKE_REACH) * amp * fade, turn = Math.PI * 2 * t / Math.max(0.5, Tune.get(SHAKE_PERIOD));
            double side = (hits & 1) == 0 ? 1 : -1, kick = amp * fade;
            p[3 + u] = side * reach * Math.sin(turn);
            p[3 + v] = 0.7 * reach * Math.sin(turn * 1.37);
            p[7] = side * Math.toRadians(Tune.get(SHAKE_YAW)) * kick * Math.sin(turn);
            // tip about the level axis that lies in the struck face
            p[face.getAxis() == Direction.Axis.X ? 8 : 6] = Math.toRadians(Tune.get(SHAKE_PITCH)) * kick * Math.sin(turn * 0.8 + side * 0.6);
        } else {
            double reach = Tune.get(WOBBLE_REACH) * amp * fade, swing = Math.sin(Math.PI * 2 * t / Math.max(0.5, Tune.get(WOBBLE_PERIOD)));
            p[u] += reach * (1 + swing) / 2;
            p[v] += reach * (1 - swing) / 2;
        }
        return p;
    }

    /**
     * For the crack overlay and the outline at {@code pos}: the running hit's move and size about the block centre. The
     * pose is at the block's corner.
     */
    public static void transform(PoseStack ps, BlockPos pos) {
        transform(ps, pos, 1);
    }

    /**
     * The crack overlay: the copy's shape (FLOOR included, or the crack sits inside the copy) and a hair outside it, so
     * the crack, drawn after the copy, doesn't z-fight with its faces.
     */
    public static void transformCrack(PoseStack ps, BlockPos pos) {
        transform(ps, pos, CRACK_OUT);
    }

    private static void transform(PoseStack ps, BlockPos pos, double out) {
        if (PULSES.isEmpty()) return;
        Pulse p = PULSES.get(pos.asLong());
        if (p == null) return;
        double[] sc = p.shape(Clock.now());
        if (sc[0] == 1 && sc[1] == 1 && sc[2] == 1 && out == 1 && !p.hidden) return;
        pose(ps, sc, 0.5, 0.5, 0.5, out);
    }

    /** The turn of a {@link #shape} that has one. */
    public static Quaternionf turn(double[] sc) {
        return new Quaternionf().rotationYXZ((float) sc[7], (float) sc[6], (float) sc[8]);
    }

    /** A section mesh went live (any thread). */
    public static void meshLive(long sectionNode, long compileStartNs) {
        if (!PULSES.isEmpty()) LIVE.add(new long[] {sectionNode, compileStartNs});
    }

    // released copies whose block is back in a live mesh
    private static void handOff() {
        long[] live;
        while ((live = LIVE.poll()) != null) {
            for (Iterator<Pulse> it = PULSES.values().iterator(); it.hasNext(); ) {
                Pulse p = it.next();
                if (p.releasedNs != 0 && live[1] > p.releasedNs && SectionPos.blockToSection(p.pos.asLong()) == live[0]) it.remove();
            }
        }
    }

    /** The shaking copies, into Iris's shadow pass too: their blocks are out of the mesh. Only the draw. */
    public static void submitShadow(RenderPass pass) {
        double now = Clock.now();
        for (Pulse p : PULSES.values()) {
            if (p.style != Style.SHAKE) continue;
            pass.ps.pushPose();
            pose(pass.ps, p.shape(now), p.pos.getX() + 0.5 - pass.cam.x, p.pos.getY() + 0.5 - pass.cam.y, p.pos.getZ() + 0.5 - pass.cam.z, 1);
            p.render.draw(pass);
            pass.ps.popPose();
        }
    }

    // the shape about the point (x, y, z) of the pose
    private static void pose(PoseStack ps, double[] sc, double x, double y, double z, double out) {
        ps.translate(x + sc[3], y + sc[4], z + sc[5]);
        if (sc.length > 6) ps.rotateAround(turn(sc), 0, 0, 0);
        ps.scale((float) (Math.max(FLOOR, sc[0]) * out), (float) (Math.max(FLOOR, sc[1]) * out), (float) (Math.max(FLOOR, sc[2]) * out));
        ps.translate(-0.5f, -0.5f, -0.5f);
    }

    /** With the pops, in the transient-block pass. */
    public static void submit(RenderPass pass) {
        if (PULSES.isEmpty()) return;
        long t0 = System.nanoTime();
        try {
            handOff();
            PoseStack ps = pass.ps;
            Vec3 cam = pass.cam;
            double now = Clock.now();
            ClientLevel level = Minecraft.getInstance().level;
            // before vanilla's order 0: the crack overlay (same solid phase, no depth write) must be drawn over the copy, not under it
            pass.order = -1;
            for (Iterator<Pulse> it = PULSES.values().iterator(); it.hasNext(); ) {
                Pulse p = it.next();
                boolean gone = level == null || level.getBlockState(p.pos) != p.state;
                if (!gone && p.hidden && now - p.t0 >= HOLD_TICKS) unhide(p);
                boolean late = p.releasedNs != 0 && System.nanoTime() - p.releasedWallNs > COVER_LIMIT_NS;
                if (late) watchdogs++;
                boolean done = p.releasedNs == 0 && !p.hidden && now - p.t0 >= duration(p.style);
                if (gone || late || done) {
                    unhide(p);
                    it.remove();
                    continue;
                }
                ps.pushPose();
                pose(ps, p.shape(now), p.pos.getX() + 0.5 - cam.x, p.pos.getY() + 0.5 - cam.y, p.pos.getZ() + 0.5 - cam.z, 1);
                p.render.draw(pass);
                ps.popPose();
            }
        } finally {
            pass.order = 0;
            spentNanos += System.nanoTime() - t0;
        }
    }
}
