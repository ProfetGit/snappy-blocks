package io.github.profetgit.snappyblocks.burst;

import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.Tune;
import net.minecraft.core.Direction;

/**
 * The break animations (config `breakStyle`, one at a time; `burst` is the master switch) as curves, shared by
 * {@link Bursts} and the settings previews. Burst: a hit-stop squash, then the block shrinks away. Twirl: it winds up,
 * spins faster and faster while hopping, flinging its chips off, and collapses into its drop. Times are ticks from the
 * start of the burst.
 */
public final class Break {
    static final String WINDUP = Tune.def("twirl.windup", 1.5);
    static final String SPIN = Tune.def("twirl.spin", 6.5);
    /** Degrees turned. The spin ends near 25 degrees a frame at 60 fps; much more and a cube seems to turn backwards. */
    static final String TURN = Tune.def("twirl.turn", 270);
    static final String LIFT = Tune.def("twirl.lift", 0.28);
    /** Beside a full block, the size it draws in to before it turns far: at 0.7 its corners stay inside its own spot. */
    static final String GATHER = Tune.def("twirl.gather", 0.72);

    public enum Style { BURST, TWIRL }

    /** The config file's names, in the order of {@link Style}. */
    public static final String[] STYLES = {"burst", "twirl"};

    private Break() {}

    /** The break animation's own strength (config `breakStrength`) as a factor. */
    public static double strength() {
        return Math.max(25, Math.min(200, Config.get().breakStrength)) / 100.0;
    }

    // how big the animation's movements are: the mod's intensity (and Calm) times the animation's strength
    private static double amplitude() {
        return Config.get().amplitude() * strength();
    }

    // the share of the twirl's turn: more strength turns further, but only so far
    private static double turning() {
        return Math.min(1.6, Math.min(1.1, Config.get().amplitude()) * strength());
    }

    /** The chosen animation; BURST while the effect is off. */
    public static Style style() {
        int i = Config.get().burst ? java.util.Arrays.asList(STYLES).indexOf(Config.get().breakStyle) : -1;
        return i < 0 ? Style.BURST : Style.values()[i];
    }

    // the anticipation before the main move: none for a bare burst or in Calm mode (PRD §5.6)
    private static double ready(Style style, boolean bare) {
        if (style == Style.BURST) return Bursts.hold(bare);
        return bare || Config.get().calm ? 0 : Math.max(0, Tune.get(WINDUP));
    }

    /** Ticks until the block is gone. */
    public static double duration(Style style, boolean bare) {
        return ready(style, bare) + switch (style) {
            case BURST -> Math.max(1, Tune.get(Bursts.SHRINK));
            case TWIRL -> spin();
        };
    }

    /** Ticks at which the chips fly. */
    public static double chipsAt(Style style, boolean bare) {
        return ready(style, bare) + switch (style) {
            case BURST -> 0;
            case TWIRL -> spin() * 0.45;
        };
    }

    /** Ticks at which the first drop shows. */
    public static double revealAfter(Style style, boolean bare) {
        if (bare) return 0;
        return switch (style) {
            case BURST -> ready(style, false) + 1;
            case TWIRL -> duration(style, false) - 0.75;
        };
    }

    // a longer turn takes a little longer, so its fastest frame stays one the eye can follow
    private static double spin() {
        return Math.max(2, Tune.get(SPIN)) * Math.sqrt(Math.max(1, turning()));
    }

    /**
     * {sxz, sy, s, yaw, lift} at {@code t} ticks: width and height about the floor, then size and the turn (radians,
     * about the upright axis) about the centre, then the rise in blocks. {@code tight}: a full block stands beside the
     * spot, so a turning block draws in to keep clear of it.
     */
    public static double[] shape(Style style, boolean bare, boolean tight, double t) {
        double amp = amplitude(), ready = ready(style, bare);
        return switch (style) {
            case BURST -> burst(ready, t, amp);
            case TWIRL -> twirl(ready, t, amp, tight);
        };
    }

    // during the hold the block squashes about its floor (the punch); then the squash springs back while it shrinks
    private static double[] burst(double hold, double t, double amp) {
        double squash = Tune.get(Bursts.SQUASH) * amp;
        if (t < hold) {
            double a = out(t / Math.max(0.05, hold));
            return new double[] {1 + squash * 0.5 * a, 1 - squash * a, 1, 0, 0};
        }
        double u = Math.min(1, (t - hold) / Math.max(1, Tune.get(Bursts.SHRINK)));
        double back = Math.max(0, 1 - u / 0.35);
        return new double[] {1 + squash * 0.5 * back, 1 - squash * back, 1 - u * u, 0, 0};
    }

    // a crouch turning the wrong way, then the spin: the crouch springs into a stretch, a hop that lands back on the
    // centre the drop leaves from, and the size going to nothing late, so the turn is seen. Beside a full block it also
    // draws in during the crouch, so it turns inside its own spot instead of cutting through its neighbours
    private static double[] twirl(double windup, double t, double amp, boolean tight) {
        double back = windup > 0 ? Math.toRadians(tight ? Math.min(12, 18 * amp) : 18 * Math.min(1.5, amp)) : 0;
        double gather = tight ? Math.min(1, Math.max(0.4, Tune.get(GATHER))) : 1;
        double crouch = Math.min(0.3, 0.12 * amp), broad = Math.min(0.12, 0.07 * amp);
        if (t < windup) {
            double a = out(t / windup);
            return new double[] {1 + broad * a, 1 - crouch * a, 1 + (gather - 1) * a, -back * a, 0};
        }
        double u = Math.min(1, (t - windup) / spin());
        double release = windup > 0 ? Math.max(0, 1 - u / 0.2) : 0;
        // without a wind-up (Calm) it draws in as the spin starts
        double size = windup > 0 ? gather : 1 + (gather - 1) * out(u / 0.2);
        double stretch = Math.sin(Math.PI * Math.min(1, Math.max(0, (u - 0.1) / 0.75))) * Math.min(2, amp);
        double turn = Math.toRadians(Tune.get(TURN) * turning());
        double yaw = -back + (back + turn) * Math.pow(u, 1.7);
        double wide = 1 + broad * release - 0.08 * stretch;
        double tall = 1 - crouch * release + 0.14 * stretch;
        return new double[] {wide, tall, size * (1 - Math.pow(u, 2.4)), yaw, Tune.get(LIFT) * amp * Math.sin(Math.PI * u)};
    }

    private static double out(double a) {
        a = Math.min(1, Math.max(0, a));
        return 1 - (1 - a) * (1 - a);
    }

    /**
     * Chip {@code i} of {@code n} as it leaves the block: {x, y, z, vx, vy, vz}, the position from the middle of the
     * spot's floor, the speed in blocks per tick before its material. {@code a, b, c} in 0..1 spread it,
     * {@code size} is its size factor and {@code face} the mined face (null: broken in one hit).
     */
    public static double[] launch(Style style, int i, int n, double a, double b, double c, float size, Direction face) {
        double clear = Chip.half(size) + 0.01, speed = Bursts.chipSpeed() * Bursts.speedFactor(i, n);
        if (style == Style.BURST) {
            double ox = a - 0.5, oy = b - 0.5, oz = c - 0.5;
            double[] v = Bursts.burstSpeed(ox, oy, oz, speed, face);
            // never starting inside the floor, or it would fall through it
            return new double[] {ox * 0.7, Math.max(clear, 0.5 + oy * 0.7), oz * 0.7, v[0], v[1], v[2]};
        }
        // flung off all round the block, each chip in its own slice of the circle, the way it turns and a little outward
        double angle = (i + a) / n * Math.PI * 2, x = Math.cos(angle), z = Math.sin(angle);
        double lift = Tune.get(LIFT) * amplitude();
        return new double[] {x * 0.42, Math.max(clear, 0.5 + lift + (b - 0.5) * 0.6), z * 0.42,
            (z + 0.45 * x) * speed * 0.9, speed * (0.15 + 0.4 * c), (-x + 0.45 * z) * speed * 0.9};
    }
}
