package io.github.profetgit.snappyblocks.pulse;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.snappyblocks.Clock;
import io.github.profetgit.snappyblocks.Compat;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.Probe;
import io.github.profetgit.snappyblocks.RenderPass;
import io.github.profetgit.snappyblocks.Tune;
import io.github.profetgit.snappyblocks.burst.Chip;
import io.github.profetgit.snappyblocks.pop.PopRenderState;
import io.github.profetgit.snappyblocks.pop.Pops;
import io.github.profetgit.snappyblocks.pop.ShaderPack;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Mining pulse (PRD §5.2). Every mining hit (the miner's own first hit, then the server's level event 2020 every 4
 * ticks, for every miner in range; both reach ClientLevel.addBreakingBlockEffects with the sound flag) pushes the block out to pulse.peak and back in pulse.ticks, with a chip or two off
 * the hit face. The block stays in the chunk mesh: a copy a hair larger than the block is drawn over it (the bulge),
 * so a pulse never waits on a remesh. The crack overlay and the outline scale with it ({@link #transform}).
 */
public final class Pulses {
    static final String PEAK = Tune.def("pulse.peak", 1.03);
    static final String TICKS = Tune.def("pulse.ticks", 2);
    static final String CHIPS = Tune.def("pulse.chips", 2);
    /** 0: under a shader pack the bulge copy stays out (it can be lit brighter than the chunk mesh, a flicker each hit); 1: draw it. */
    static final String SHADED = Tune.def("pulse.shaded", 1);
    /** The copy never sits closer to the block than this, so it never z-fights with it (24 blocks out). */
    static final double FLOOR = 1.004;
    static final double RANGE = 24;

    static final class Pulse {
        final BlockPos pos;
        final BlockState state;
        final double t0;
        final PopRenderState render = new PopRenderState();

        Pulse(BlockPos pos, BlockState state, double t0) {
            this.pos = pos;
            this.state = state;
            this.t0 = t0;
        }
    }

    private static final Long2ObjectOpenHashMap<Pulse> PULSES = new Long2ObjectOpenHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    public static int started;
    public static long spentNanos;

    private Pulses() {}

    public static void clear() {
        PULSES.clear();
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
        if (eligible(state) && !Pops.popping(at) && (Tune.get(SHADED) > 0.5 || !ShaderPack.active())) {
            Pulse p = new Pulse(at, state, Clock.now());
            p.render.init(level, at, state);
            PULSES.put(at.asLong(), p);
            started++;
        }
        chips(level, at, state, face);
    }

    /** A block changed (broken, replaced): its pulse ends at once, the burst or the new block takes over. */
    public static void blockChanged(BlockPos pos) {
        if (!PULSES.isEmpty()) PULSES.remove(pos.asLong());
    }

    private static void chips(ClientLevel level, BlockPos pos, BlockState state, Direction face) {
        if (!state.shouldSpawnTerrainParticles()) return;
        int n = (int) Math.round(Tune.get(CHIPS));
        switch (Minecraft.getInstance().options.particles().get()) {
            case MINIMAL -> n = 0;
            case DECREASED -> n = Math.min(n, 1);
            default -> {
            }
        }
        double amp = Config.get().amplitude();
        for (int i = 0; i < n; i++) {
            double u = RANDOM.nextDouble() - 0.5, v = RANDOM.nextDouble() - 0.5;
            double x = pos.getX() + 0.5 + face.getStepX() * 0.52, y = pos.getY() + 0.5 + face.getStepY() * 0.52, z = pos.getZ() + 0.5 + face.getStepZ() * 0.52;
            // spread across the face, flung out of it
            if (face.getAxis() != Direction.Axis.X) x += u * 0.8;
            if (face.getAxis() != Direction.Axis.Y) y += (face.getAxis() == Direction.Axis.X ? u : v) * 0.8;
            if (face.getAxis() != Direction.Axis.Z) z += v * 0.8;
            double out = 0.12 * amp;
            Chip c = new Chip(level, x, y, z, face.getStepX() * out + u * 0.05, face.getStepY() * out + 0.08, face.getStepZ() * out + v * 0.05, state, pos);
            c.scale(1.4f + 0.4f * RANDOM.nextFloat());
            Minecraft.getInstance().particleEngine.add(c);
        }
    }

    /** Scale at {@code t} ticks into a pulse: out to the peak in the first third, back by the end. */
    static double scale(double t) {
        double ticks = Math.max(0.5, Tune.get(TICKS));
        if (t <= 0 || t >= ticks) return 1;
        double u = t / ticks;
        double bump = u < 1 / 3.0 ? 1 - Math.pow(1 - u * 3, 2) : 1 - Math.pow((u - 1 / 3.0) * 1.5, 2);
        return 1 + (Tune.get(PEAK) - 1) * Config.get().amplitude() * bump;
    }

    /**
     * For the crack overlay and the outline at {@code pos}: the pulse's scale about the block centre, or 1. The pose is
     * at the block's corner.
     */
    public static void transform(PoseStack ps, BlockPos pos) {
        if (PULSES.isEmpty()) return;
        Pulse p = PULSES.get(pos.asLong());
        if (p == null) return;
        double s = scale(Clock.now() - p.t0);
        if (s == 1) return;
        float f = (float) Math.max(FLOOR, s);
        ps.translate(0.5f, 0.5f, 0.5f);
        ps.scale(f, f, f);
        ps.translate(-0.5f, -0.5f, -0.5f);
    }

    /** With the pops, in the transient-block pass. */
    public static void submit(RenderPass pass) {
        if (PULSES.isEmpty()) return;
        long t0 = System.nanoTime();
        try {
            PoseStack ps = pass.ps;
            Vec3 cam = pass.cam;
            double now = Clock.now(), ticks = Math.max(0.5, Tune.get(TICKS));
            ClientLevel level = Minecraft.getInstance().level;
            for (Iterator<Pulse> it = PULSES.values().iterator(); it.hasNext(); ) {
                Pulse p = it.next();
                double t = now - p.t0;
                if (t >= ticks || level == null || level.getBlockState(p.pos) != p.state) {
                    it.remove();
                    continue;
                }
                float s = (float) Math.max(FLOOR, scale(t));
                ps.pushPose();
                ps.translate(p.pos.getX() + 0.5 - cam.x, p.pos.getY() + 0.5 - cam.y, p.pos.getZ() + 0.5 - cam.z);
                ps.scale(s, s, s);
                ps.translate(-0.5f, -0.5f, -0.5f);
                p.render.draw(pass);
                ps.popPose();
            }
        } finally {
            spentNanos += System.nanoTime() - t0;
        }
    }
}
