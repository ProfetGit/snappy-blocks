package io.github.profetgit.snappyblocks.burst;

import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.Tune;
import net.minecraft.client.particle.TerrainParticle;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 3D block particles: with `cubes` on, every vanilla terrain particle (the mod's chips and vanilla's own break, hit,
 * sprint and fall crumbs) is drawn as a small tumbling cube, six quads through vanilla's own particle pipeline
 * (mixin SingleQuadParticleMixin), so shaders and Sodium keep treating it as a particle.
 */
public final class Cubes {
    static final String SIZE = Tune.def("cube.size", 0.75);
    static final String SPIN = Tune.def("cube.spin", 0.32);
    /** A cube that touched down turns flat onto its nearest face over this many ticks. */
    public static final float SETTLE_TICKS = 2.5f;
    /** The vanilla quad faces +z; these turn it onto each side of a cube. */
    private static final Quaternionf[] FACES = {
        new Quaternionf().rotateX((float) (Math.PI / 2)), new Quaternionf().rotateX((float) (-Math.PI / 2)),
        new Quaternionf().rotateY((float) Math.PI), new Quaternionf(),
        new Quaternionf().rotateY((float) (-Math.PI / 2)), new Quaternionf().rotateY((float) (Math.PI / 2))};
    public static final int SIDES = 6;
    /** Cubes drawn and the time they took (test hooks). */
    public static int drawn;
    public static long spentNanos;

    private Cubes() {}

    public static boolean on() {
        return Config.get().enabled && Config.get().cubes;
    }

    public static boolean draws(Object particle) {
        return particle instanceof TerrainParticle && on();
    }

    /** A cube's half edge as a share of the flat quad's half size. */
    public static float size() {
        return size;
    }

    /** The live numbers, read once a frame: a mass break draws thousands of cubes, and a tune lookup allocates. */
    private static float size = 0.75f, spin = 0.32f;

    public static void frame() {
        size = (float) Tune.get(SIZE);
        spin = (float) (Tune.get(SPIN) * Config.get().amplitude());
    }

    /**
     * A cube's turn at {@code t} ticks old: its own yaw, tumbling about two axes; from {@code landed} (the age it
     * touched down, negative while it flies) it settles onto the nearest face.
     */
    public static Quaternionf rotation(int seed, float t, float landed, Quaternionf out) {
        float yaw = (seed & 1023) / 1023f * (float) (Math.PI * 2);
        float wx = spin * (0.5f + (seed >>> 10 & 255) / 255f) * ((seed >>> 18 & 1) == 0 ? 1 : -1);
        float wz = spin * (0.5f + (seed >>> 19 & 255) / 255f) * ((seed >>> 27 & 1) == 0 ? 1 : -1);
        return out.rotationY(yaw).rotateX(angle(wx, t, landed)).rotateZ(angle(wz, t, landed));
    }

    private static float angle(float speed, float t, float landed) {
        if (landed < 0) return speed * t;
        float from = speed * landed, quarter = (float) (Math.PI / 2);
        float to = Math.round(from / quarter) * quarter;
        float u = Math.min(1, Math.max(0, (t - landed) / SETTLE_TICKS));
        u = 1 - (1 - u) * (1 - u);
        return from + (to - from) * u;
    }

    /** Side {@code i} of a cube turned by {@code turn}: the quad's rotation in {@code quad}, its outward normal in {@code normal}. */
    public static void side(int i, Quaternionf turn, Quaternionf quad, Vector3f normal) {
        turn.mul(FACES[i], quad);
        quad.transform(normal.set(0, 0, 1));
    }

    /** How bright a side with this world normal is, over the terrain particle's own grey: Minecraft's block shading. */
    public static float shade(Vector3f n) {
        return n.x * n.x * 0.95f + n.z * n.z * 1.15f + n.y * n.y * (n.y > 0 ? 1.45f : 0.75f);
    }
}
