package io.github.profetgit.snappyblocks.ui;

import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.profetsettings.Preview;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.burst.Break;
import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.burst.Chip;
import io.github.profetgit.snappyblocks.burst.Dust;
import io.github.profetgit.snappyblocks.burst.DustArt;
import io.github.profetgit.snappyblocks.burst.Puff;
import io.github.profetgit.snappyblocks.item.ItemFx;
import io.github.profetgit.snappyblocks.pulse.Pulses;
import java.util.Objects;
import java.util.Random;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

// What Snappy Blocks' preview scenes share: the loop clock, the tool swings, chips and drops on the stage (Diorama).
// Every movement comes from the mod's own curve functions, with the config as it is right now; only vanilla's item and
// particle physics are repeated here. Curve times are in ticks (50 ms), the unit the curves use.
abstract class SnappyScene extends Preview {
    static final float MS = 50;
    // ms the raise clip lasts: it ends in the first hit
    static final float RAISE = 250;

    static Identifier tex(String block) {
        return Identifier.tryParse("textures/block/" + block + ".png");
    }

    static Identifier itemTex(String item) {
        return Identifier.tryParse("textures/item/" + item + ".png");
    }

    // restarts the loop when any option changes
    float time(Ctx c, float period) {
        Config k = Config.get();
        return clock(c, Objects.hash(k.enabled, k.intensity, k.calm, k.pop, k.popSpeed, k.breakSpeed, k.pulse, k.pulseStyle, k.pulseSpeed, k.pulseStrength, k.burst, k.breakStyle, k.breakStrength, k.chips, k.placeEffect, k.placeStrength, k.cubes, k.items, k.pickup), period);
    }

    static String t(String key, Object... args) {
        return I18n.get("snappy_blocks.preview." + key, args);
    }

    // ---- the tools ----

    static int rest() {
        return Diorama.FOLLOW + Diorama.FOLLOW_FRAMES - 1;
    }

    // a tool's frame at {@code time}: at rest, raising into the first hit at {@code first}, swinging until the block
    // breaks at {@code end}, then following through. The swing is vanilla's 300 ms one, or the {@code quick} one that
    // keeps time with the 200 ms between mining hits.
    static int swing(float time, float first, float end, boolean quick) {
        if (time < first - RAISE) return rest();
        if (time < first) return Diorama.RAISE + frame(time - (first - RAISE), Diorama.RAISE_FRAMES);
        if (time >= end) return Diorama.FOLLOW + frame(time - end, Diorama.FOLLOW_FRAMES);
        int clip = quick ? Diorama.MINE : Diorama.SWING, frames = quick ? Diorama.MINE_FRAMES : Diorama.SWING_FRAMES;
        // the nudge keeps a whole number of frames from rounding down
        return clip + (int) ((time - first) * 60 / 1000 + 0.01f) % frames;
    }

    // the frame of a 60 fps clip {@code ms} after it started
    static int frame(float ms, int count) {
        return Math.max(0, Math.min(count - 1, (int) (ms * 60 / 1000)));
    }

    // ---- chips ----

    // the break's chips {@code t} ticks after it, with the chosen animation's launch (the struck face is the east one)
    // and the real sizes and speeds; all gone {@code gone} ticks after the break
    static void burstChips(Gfx g, float cx, float floor, Identifier tex, float t, float gone, boolean front) {
        burstChips(g, cx, floor, tex, Bursts.chipCount(), t, gone, front);
    }

    static void burstChips(Gfx g, float cx, float floor, Identifier tex, int n, float t, float gone, boolean front) {
        Break.Style style = Break.style();
        float at = (float) Break.chipsAt(style, false), age = t - at;
        if (age < 0) return;
        Random r = new Random(41);
        Chip.Material m = Chip.Material.PLAIN;
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble(), b = r.nextDouble(), c = r.nextDouble();
            float size = (float) (Bursts.chipSize() * Bursts.sizeFactor(i, n) * (0.9 + 0.2 * r.nextDouble()));
            double[] p = Break.launch(style, i, n, a, b, c, size, Direction.EAST);
            double[] v = m.speed(p[3], p[4], p[5]);
            Diorama.chip(g, r, cx, floor, tex, age, p[0], p[1], p[2], v, m, size, Bursts.LIFE + r.nextInt(Bursts.LIFE_SPREAD), gone - at, front);
        }
    }

    // whether the breaking block, in its shape, still stands in front of the drop
    static boolean covers(double[] shape) {
        return shape != null && shape[2] * Math.min(1, shape[1]) > 0.35;
    }

    // the chips one mining hit knocks off the block's right-hand (east) face, where the tool strikes
    static void hitChips(Gfx g, float cx, float floor, Identifier tex, int hit, float age, float gone, boolean front) {
        if (age < 0) return;
        Random r = new Random(7 + hit * 31L);
        double amp = Pulses.amplitude() * Pulses.hitForce(hit);
        for (int n = 0; n < Pulses.hitChips(hit); n++) {
            double u = r.nextDouble() - 0.5, v = r.nextDouble() - 0.5;
            double[] speed = Pulses.chipSpeed(Direction.EAST, u, v, amp);
            float size = 1.3f + 0.6f * r.nextFloat();
            Diorama.chip(g, r, cx, floor, tex, age, 0.51 + Chip.half(size), 0.5 + u * 0.8, v * 0.8, speed, Chip.Material.PLAIN, size,
                Pulses.LIFE + r.nextInt(Pulses.LIFE_SPREAD), gone, front);
        }
    }

    // a placed block's puff {@code age} ticks after it, round the foot of the block on the floor (Puff's own launch)
    static void puffChips(Gfx g, float cx, float floor, Identifier tex, float age, float gone, boolean front) {
        if (age < 0) return;
        Random r = new Random(23);
        double speed = Puff.speed();
        Chip.Material m = Chip.Material.PLAIN;
        for (int i = 0; i < Puff.chipCount(); i++) {
            double[] e = Puff.edge(i, r.nextDouble(), r.nextDouble());
            float size = Puff.size(r.nextDouble());
            double clear = Chip.half(size) + 0.01;
            boolean along = Math.abs(e[0]) > Math.abs(e[1]);
            double x = e[0] + (along ? Math.signum(e[0]) * clear : 0), z = e[1] + (along ? 0 : Math.signum(e[1]) * clear);
            double[] v = m.speed(e[2] * speed, (Puff.AWAY + Puff.UP) * speed, e[3] * speed);
            Diorama.chip(g, r, cx, floor, tex, age, x, clear, z, v, m, size, Puff.LIFE + r.nextInt(Puff.LIFE), gone, front);
        }
    }

    // the explosion {@code age} ticks after a block was placed: its bits off the four sides and the top (Puff's own launch)
    static void blastChips(Gfx g, float cx, float floor, Identifier tex, float age, float gone, boolean front) {
        if (age < 0) return;
        Random r = new Random(61);
        double speed = Puff.speed() / 1.25;
        Chip.Material m = Chip.Material.PLAIN;
        Direction[] sides = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (int i = 0; i < Puff.blastCount(); i++) {
            Direction side = sides[i % sides.length];
            Direction.Axis axis = side.getAxis();
            Direction.Axis[] across = Puff.across(axis);
            double a = r.nextDouble() - 0.5, b = r.nextDouble() - 0.5;
            float size = Puff.blastSize(r.nextDouble());
            double clear = Chip.half(size) + 0.01;
            // from the block's centre
            double[] p = new double[3];
            p[across[0].ordinal()] = a;
            p[across[1].ordinal()] = b;
            p[axis.ordinal()] = (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1) * (0.5 + clear);
            double[] raw = Puff.blast(side, a, b, r.nextDouble());
            double[] v = m.speed(raw[0] * speed, raw[1] * speed, raw[2] * speed);
            Diorama.chip(g, r, cx, floor, tex, age, p[0], Math.max(clear, p[1] + 0.5), p[2], v, m, size,
                Puff.BLAST_LIFE + r.nextInt(Puff.BLAST_SPREAD), gone, front);
        }
    }

    // the poofs {@code age} ticks after a block was placed, round its foot on the floor (Dust's own motion)
    static void poofs(Gfx g, float cx, float floor, int color, float age, boolean front) {
        if (age < 0) return;
        Random r = new Random(31);
        double speed = Dust.speed();
        int n = Dust.count(Dust.Style.POOF);
        for (int i = 0; i < n; i++) {
            double[] e = Dust.ring(i, n, r.nextDouble(), r.nextDouble());
            double fast = speed * (0.4 + 0.3 * r.nextDouble()), far = Dust.travel(age) * fast;
            int life = Dust.poofLife(r.nextDouble());
            boolean mirrored = r.nextBoolean();
            if (age >= life) continue;
            Diorama.cloud(g, cx, floor, DustArt.Sheet.POOF, age / life, e[0] + e[2] * far, Dust.poofHeight(age), e[1] + e[3] * far,
                Dust.POOF_HALF * Dust.size(), color, mirrored, front);
        }
    }

    // the shockwave's ring on the floor; drawn before the block that stands on it
    static void shockRing(Gfx g, float cx, float floor, int color, float age) {
        int life = Dust.ringLife();
        if (age >= 0 && age < life) Diorama.ring(g, cx, floor, age / life, Dust.RING_HALF * Dust.size(), color);
    }

    // the wisps racing on the shockwave's front
    static void wisps(Gfx g, float cx, float floor, int color, float age, boolean front) {
        if (age < 0) return;
        Random r = new Random(53);
        double speed = Dust.speed();
        int n = Dust.count(Dust.Style.SHOCKWAVE);
        for (int i = 0; i < n; i++) {
            double angle = (i + 0.4 * r.nextDouble()) / n * Math.PI * 2;
            int life = Dust.wispLife(r.nextDouble());
            double ahead = 0.04 + 0.2 * r.nextDouble(), rise = 0.006 + 0.012 * r.nextDouble();
            boolean mirrored = r.nextBoolean();
            if (age >= life) continue;
            double radius = Dust.radius(age / Dust.ringLife()) + ahead * speed;
            Diorama.cloud(g, cx, floor, DustArt.Sheet.WISP, age / life, Math.cos(angle) * radius,
                Dust.WISP_HALF * Dust.size() * 0.7 + rise * speed * age, Math.sin(angle) * radius, Dust.WISP_HALF * Dust.size(), color, mirrored, front);
        }
    }

    // ---- drops ----

    // the broken block's drop {@code t} ticks after the break: with Item drops on it ({@code fx}) it waits for the burst
    // to open, springs out of the middle and lands with the mod's squash and hop; otherwise it is vanilla's, there at once.
    // Vanilla's gentle bob lifts it either way. {@code size} is below 1 while it despawns.
    static void drop(Gfx g, float cx, float floor, Identifier item, int count, float t, double size, boolean fx, boolean burst) {
        double open = Bursts.revealAfter(false);
        if (fx && burst && t < open) return;
        double[] e = fall(0.1, 0.35, 0.045, 0.2, t);
        double[] pose = fx ? landing(e[2]) : new double[] {1, 1, 0};
        double pull = fx && burst ? Bursts.springBack((t - open) / Bursts.dropSpringTicks()) : 0;
        double y = e[1] + pose[2] + bob(t);
        float gx = Diorama.sx(cx, e[0], 0.35), gy = Diorama.sy(floor, e[0], 0, 0.35);
        float ix = gx, iy = Diorama.sy(floor, e[0], y, 0.35) + 4;
        ix += (float) ((cx - ix) * pull);
        iy += (float) ((floor - Diorama.TALL / 2 + Diorama.ITEM_HALF - iy) * pull);
        gx += (float) ((cx - gx) * pull);
        gy += (float) ((floor - gy) * pull);
        Diorama.groundShadow(g, gx, gy + 2, (float) size, e[1] + pose[2] + bob(t));
        // it shrinks about its middle
        iy -= (float) (Diorama.ITEM_HALF * (1 - size));
        Diorama.item(g, item, ix, iy, pose[0] * size, pose[1] * size, count);
    }

    // how high vanilla's bob holds an item {@code t} ticks after it appeared, in blocks
    static double bob(double t) {
        return 0.1 - 0.1 * Math.cos(t / 10);
    }

    // vanilla's flight of an item entity after {@code t} ticks: {x, y, ticks since it touched down or -1}
    static double[] fall(double x, double y, double vx, double vy, double t) {
        double px = x, py = y, landed = -1;
        for (int i = 0; i <= (int) t; i++) {
            px = x;
            py = y;
            if (landed < 0) {
                vy -= 0.04;
                x += vx;
                y += vy;
                vx *= 0.98;
                vy *= 0.98;
                if (y <= 0) {
                    y = 0;
                    landed = i + 1;
                }
            }
        }
        double a = t - (int) t;
        return new double[] {px + (x - px) * a, py + (y - py) * a, landed < 0 || t < landed ? -1 : t - landed};
    }

    // the landing squash and hop {@code since} ticks after touchdown: {width, height, lift in blocks}
    static double[] landing(double since) {
        double[] p = since < 0 ? null : ItemFx.landing(since);
        return p == null ? new double[] {1, 1, 0} : p;
    }

    // ---- captions and keys ----

    // the player's own binding centred on cx: a mouse icon for a mouse button, else a key cap
    static void key(Gfx g, Ctx c, KeyMapping mapping, float cx, int y, boolean pressed) {
        g.binding(mapping, cx, y, c.sz.fs, pressed);
    }

    // why this scene's feature does nothing right now, or null; the scene then plays what vanilla does and says so, so
    // that switching the option compares the two
    static String off(String feature, boolean on, boolean aside) {
        if (!Config.get().enabled) return t("off.all");
        if (!on) return t("off." + feature);
        return aside ? t("off.aside") : null;
    }

    static int ms(double ticks) {
        return (int) Math.round(ticks * MS);
    }
}
