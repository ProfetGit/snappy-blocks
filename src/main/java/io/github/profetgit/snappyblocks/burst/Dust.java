package io.github.profetgit.snappyblocks.burst;

import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.Tune;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
//? if >=1.21.2 {
import net.minecraft.client.Camera;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
//?} else {
/*import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
*///?}
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
//?} else if >=1.21.2 {
/*import net.minecraft.client.renderer.state.QuadParticleRenderState;
*///?}

/**
 * The two dust placing effects ({@link Puff}), in the mod's own pixel clouds ({@link DustArt}), tinted a pale shade of the block.
 * Poof: small clouds swell round the foot of the block and tear apart. Shockwave: a ring of cloud rolls out along the
 * surface from under the block, with wisps racing on its front. Every motion is a function of age alone, which the
 * settings preview draws from too. A cloud's quad keeps its size, so its pixels do: the frames do the growing.
 */
public final class Dust {
    public enum Style { OFF, POOF, SHOCKWAVE }

    static final String SIZE = Tune.def("dust.size", 1);
    static final String SPEED = Tune.def("dust.speed", 1);
    /** How fast the clouds play: 2 halves every lifetime. */
    static final String PACE = Tune.def("dust.pace", 1);
    static final String ALPHA = Tune.def("dust.alpha", 1);
    static final String PUFFS = Tune.def("dust.poofs", 8);
    static final String WISPS = Tune.def("dust.wisps", 14);
    /** At pace 1: a poof lasts this many ticks, plus up to the spread; the ring this many; a wisp this many plus the spread. */
    private static final int POOF_LIFE = 8, POOF_SPREAD = 3, RING_LIFE = 8, WISP_LIFE = 5, WISP_SPREAD = 3;
    /** Half sizes (blocks) of the quads: 16 sprite pixels to a block. */
    public static final double POOF_HALF = 6 / 16.0, WISP_HALF = 3 / 16.0, RING_HALF = 24 / 16.0;
    /** The ring's radius (blocks) in each of its frames, as drawn. */
    private static final double[] RING_RADIUS = {10.5 / 16, 13.5 / 16, 16.0 / 16, 18.0 / 16, 19.5 / 16, 20.8 / 16, 21.7 / 16, 22.3 / 16};
    /** The share of its speed a poof keeps each tick. */
    private static final double DRAG = 0.72;
    /** Dust is this share of white, the rest the block's own map colour. */
    private static final double PALE = 0.68;
    private static final int PALE_COLOR = 0xF4EEE2;
    /** Clouds made, rings among them (test hooks). */
    public static int made, rings;

    private Dust() {}

    /** The dust the placing effect asks for. */
    public static Style style() {
        return switch (Puff.effect()) {
            case POOF -> Style.POOF;
            case SHOCKWAVE -> Style.SHOCKWAVE;
            default -> Style.OFF;
        };
    }

    /** Poofs or wisps per placement, after the particles video setting. */
    public static int count(Style s) {
        int n = (int) Math.round(Tune.get(s == Style.POOF ? PUFFS : WISPS) * Puff.strength());
        return switch (Minecraft.getInstance().options.particles().get()) {
            case MINIMAL -> 0;
            case DECREASED -> n / 2;
            default -> n;
        };
    }

    private static int paced(int ticks) {
        return Math.max(2, (int) Math.round(ticks / Math.max(0.2, Tune.get(PACE))));
    }

    /** A poof's lifetime in ticks; {@code r} in 0..1. */
    public static int poofLife(double r) {
        return paced(POOF_LIFE + (int) (r * (POOF_SPREAD + 1)));
    }

    public static int ringLife() {
        return paced(RING_LIFE);
    }

    /** A wisp's lifetime in ticks; {@code r} in 0..1. */
    public static int wispLife(double r) {
        return paced(WISP_LIFE + (int) (r * (WISP_SPREAD + 1)));
    }

    /** The longest-lived cloud of a style, in ticks. */
    public static int longest(Style s) {
        return s == Style.POOF ? poofLife(0.999) : ringLife();
    }

    /** The size factor of every cloud. */
    public static double size() {
        return Tune.get(SIZE);
    }

    /** The outward speed factor. */
    public static double speed() {
        return Tune.get(SPEED) * Config.get().amplitude() * (0.6 + 0.4 * Puff.strength());
    }

    /**
     * Where poof {@code i} of {@code n} leaves the contact face, as {@link Puff#ring}: evenly spaced along the four
     * edges and a little outside them. {@code r}, {@code s} in 0..1.
     */
    public static double[] ring(int i, int n, double r, double s) {
        int perSide = Math.max(1, (n + 3) / 4);
        double[] e = Puff.ring(i & 3, ((i >> 2) + 0.15 + 0.7 * r) / perSide - 0.5, s);
        int out = Math.abs(e[0]) > Math.abs(e[1]) ? 0 : 1;
        e[out] += Math.signum(e[out]) * 0.14;
        return e;
    }

    /** How far a poof has come after {@code t} ticks, in multiples of its first tick's step. */
    public static double travel(double t) {
        return (1 - Math.pow(DRAG, Math.max(0, t))) / (1 - DRAG);
    }

    /** How far off the surface a poof's centre is after {@code t} ticks (blocks): resting on it, then lifting. */
    public static double poofHeight(double t) {
        return POOF_HALF * size() * 0.46 + 0.007 * speed() * travel(t);
    }

    /** The radius (blocks) of the shockwave's front at {@code u} = age over the ring's life. */
    public static double radius(double u) {
        double at = Math.max(0, Math.min(1, u)) * (RING_RADIUS.length - 1);
        int i = Math.min(RING_RADIUS.length - 2, (int) at);
        return (RING_RADIUS[i] + (RING_RADIUS[i + 1] - RING_RADIUS[i]) * (at - i)) * size();
    }

    /** Opacity at {@code u} = age over life: full, thinning over the last half. */
    public static float alpha(DustArt.Sheet sheet, double u) {
        double full = sheet == DustArt.Sheet.RING ? 0.62 : sheet == DustArt.Sheet.WISP ? 0.78 : 0.64;
        double left = Math.min(1, Math.max(0, (1 - u) / 0.5));
        return (float) Math.max(0, Math.min(1, full * Tune.get(ALPHA) * (0.55 + 0.45 * Puff.strength()) * (0.35 + 0.65 * left)));
    }

    /** The tint for a block with map colour {@code mapColor} (0: none), as RGB. */
    public static int color(int mapColor) {
        if (mapColor == 0) return PALE_COLOR;
        int out = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            double c = (PALE_COLOR >> shift & 255) * PALE + (mapColor >> shift & 255) * (1 - PALE);
            out |= (int) Math.round(c) << shift;
        }
        return out;
    }

    /** How a cloud moves: its position (world) at an age in ticks. */
    @FunctionalInterface
    interface Path {
        void at(int age, double[] out);
    }

    /**
     * A cloud of {@code sheet}; {@code flat} (a unit normal) lays it on a surface, null faces it to the camera. It is
     * lit as at {@code lit} (null: where it is).
     */
    static void spawn(ClientLevel level, DustArt.Sheet sheet, Path path, double[] flat, double[] lit, int rgb, int life, boolean mirrored) {
        if (!DustArt.ready(sheet)) return;
        Cloud c = new Cloud(level, sheet, path, flat, life, mirrored);
        c.lit = lit;
        c.tint(rgb);
        Minecraft.getInstance().particleEngine.add(c);
        made++;
        if (sheet == DustArt.Sheet.RING) rings++;
    }

    private static double[] start(Path p) {
        double[] out = new double[3];
        p.at(0, out);
        return out;
    }

//? if >=1.21.2 {
    private static final class Cloud extends SingleQuadParticle {
        private final DustArt.Sheet sheet;
        private final Path path;
        private final Quaternionf lie;
        private final boolean mirrored;
        private final double[] now = new double[3];
        private int frame;
        private double[] lit;

        Cloud(ClientLevel level, DustArt.Sheet sheet, Path path, double[] flat, int life, boolean mirrored) {
            this(level, sheet, path, flat, life, mirrored, start(path));
        }

        private Cloud(ClientLevel level, DustArt.Sheet sheet, Path path, double[] flat, int life, boolean mirrored, double[] at) {
            super(level, at[0], at[1], at[2], placeholder());
            this.sheet = sheet;
            this.path = path;
            this.lie = flat == null ? null : new Quaternionf().rotationTo(0, 0, 1, (float) flat[0], (float) flat[1], (float) flat[2]);
            this.mirrored = mirrored;
            this.lifetime = life;
            this.hasPhysics = false;
            this.quadSize = (float) (half(sheet) * size());
            this.alpha = Dust.alpha(sheet, 0);
        }

        void tint(int rgb) {
            this.setColor((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f);
        }

        @Override
        public void tick() {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            if (++this.age >= this.lifetime) {
                this.remove();
                return;
            }
            path.at(this.age, now);
            this.setPos(now[0], now[1], now[2]);
            double u = (double) this.age / this.lifetime;
            this.frame = sheet.frame(u);
            this.alpha = Dust.alpha(sheet, u);
        }

//? if >=26.1 {
        @Override
        public void extract(QuadParticleRenderState out, Camera camera, float partialTick) {
            if (lie == null) super.extract(out, camera, partialTick);
            else this.extractRotatedQuad(out, camera, lie, partialTick);
        }
//?} else {
        /*@Override
        public void extract(QuadParticleRenderState out, Camera camera, float partialTick) {
            if (lie == null) super.extract(out, camera, partialTick);
            else this.extractRotatedQuad(out, camera, lie, partialTick);
        }
*///?}

//? if >=26.1 {
        @Override
        protected int getLightCoords(float partialTick) {
//?} else {
        /*@Override
        protected int getLightColor(float partialTick) {
*///?}
            if (lit == null) {
//? if >=26.1 {
                return super.getLightCoords(partialTick);
//?} else {
                /*return super.getLightColor(partialTick);
*///?}
            }
            // the ring's own spot is inside the block it came from, which is dark
            double ox = this.x, oy = this.y, oz = this.z;
            this.x = lit[0];
            this.y = lit[1];
            this.z = lit[2];
//? if >=26.1 {
            int light = super.getLightCoords(partialTick);
//?} else {
            /*int light = super.getLightColor(partialTick);
*///?}
            this.x = ox;
            this.y = oy;
            this.z = oz;
            return light;
        }

        @Override
        protected float getU0() {
            return (float) (mirrored ? frame + 1 : frame) / sheet.frames;
        }

        @Override
        protected float getU1() {
            return (float) (mirrored ? frame : frame + 1) / sheet.frames;
        }

        @Override
        protected float getV0() {
            return 0;
        }

        @Override
        protected float getV1() {
            return 1;
        }

        @Override
        protected SingleQuadParticle.Layer getLayer() {
            return LAYERS[sheet.ordinal()];
        }
    }

    private static final SingleQuadParticle.Layer[] LAYERS = layers();

    private static SingleQuadParticle.Layer[] layers() {
        DustArt.Sheet[] sheets = DustArt.Sheet.values();
        SingleQuadParticle.Layer[] out = new SingleQuadParticle.Layer[sheets.length];
        for (int i = 0; i < sheets.length; i++) {
//? if >=26.3 {
            out[i] = new SingleQuadParticle.Layer(true, DustArt.id(sheets[i]), RenderPipelines.TRANSLUCENT_PARTICLE, RenderPipelines.OIT_PARTICLE);
//?} else {
            /*out[i] = new SingleQuadParticle.Layer(true, DustArt.id(sheets[i]), RenderPipelines.TRANSLUCENT_PARTICLE);
*///?}
        }
        return out;
    }

    /** A particle needs an atlas sprite to be born with; the cloud's own texture coordinates replace it. */
    private static TextureAtlasSprite placeholder() {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(Identifier.parse("minecraft:generic_0"));
    }
//?} else {
    /*// Before 1.21.2 vanilla draws only its own particle sheets, so a cloud draws itself (the CUSTOM type) with the
    // text render type: a lit, see-through textured quad
    private static final class Cloud extends Particle {
        private final DustArt.Sheet sheet;
        private final Path path;
        private final Quaternionf lie;
        private final boolean mirrored;
        private final double[] now = new double[3];
        private int frame;
        private double[] lit;

        Cloud(ClientLevel level, DustArt.Sheet sheet, Path path, double[] flat, int life, boolean mirrored) {
            this(level, sheet, path, flat, life, mirrored, start(path));
        }

        private Cloud(ClientLevel level, DustArt.Sheet sheet, Path path, double[] flat, int life, boolean mirrored, double[] at) {
            super(level, at[0], at[1], at[2]);
            this.sheet = sheet;
            this.path = path;
            this.lie = flat == null ? null : new Quaternionf().rotationTo(0, 0, 1, (float) flat[0], (float) flat[1], (float) flat[2]);
            this.mirrored = mirrored;
            this.lifetime = life;
            this.hasPhysics = false;
            this.alpha = Dust.alpha(sheet, 0);
        }

        void tint(int rgb) {
            this.setColor((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f);
        }

        @Override
        public void tick() {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            if (++this.age >= this.lifetime) {
                this.remove();
                return;
            }
            path.at(this.age, now);
            this.setPos(now[0], now[1], now[2]);
            double u = (double) this.age / this.lifetime;
            this.frame = sheet.frame(u);
            this.alpha = Dust.alpha(sheet, u);
        }

        @Override
        public void render(VertexConsumer ignored, Camera camera, float partialTick) {
            Vec3 cam = camera.getPosition();
            float px = (float) (Mth.lerp(partialTick, this.xo, this.x) - cam.x), py = (float) (Mth.lerp(partialTick, this.yo, this.y) - cam.y),
                pz = (float) (Mth.lerp(partialTick, this.zo, this.z) - cam.z);
            Quaternionf q = lie != null ? lie : camera.rotation();
            float h = (float) (half(sheet) * size());
            float u0 = (float) (mirrored ? frame + 1 : frame) / sheet.frames, u1 = (float) (mirrored ? frame : frame + 1) / sheet.frames;
            int light = this.getLightColor(partialTick);
            RenderType type = RenderType.text(DustArt.id(sheet));
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            VertexConsumer out = buffers.getBuffer(type);
            // both windings: the one facing the camera survives culling
            int[][] corners = {{1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < 4; i++) {
                    int[] c = corners[pass == 0 ? i : 3 - i];
                    Vector3f p = new Vector3f(c[0], c[1], 0).rotate(q).mul(h).add(px, py, pz);
                    vertex(out, p, c[0] > 0 ? u1 : u0, c[1] > 0 ? 0 : 1, light);
                }
            }
            buffers.endBatch(type);
        }

        @Override
        protected int getLightColor(float partialTick) {
            if (lit == null) return super.getLightColor(partialTick);
            // the ring's own spot is inside the block it came from, which is dark
            double ox = this.x, oy = this.y, oz = this.z;
            this.x = lit[0];
            this.y = lit[1];
            this.z = lit[2];
            int light = super.getLightColor(partialTick);
            this.x = ox;
            this.y = oy;
            this.z = oz;
            return light;
        }

        private void vertex(VertexConsumer out, Vector3f p, float u, float v, int light) {
//? if >=1.21 {
            out.addVertex(p.x, p.y, p.z).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setUv(u, v).setLight(light);
//?} else {
            /^out.vertex(p.x, p.y, p.z).color(this.rCol, this.gCol, this.bCol, this.alpha).uv(u, v).uv2(light).endVertex();
^///?}
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.CUSTOM;
        }
    }
*///?}

    private static double half(DustArt.Sheet s) {
        return s == DustArt.Sheet.RING ? RING_HALF : s == DustArt.Sheet.WISP ? WISP_HALF : POOF_HALF;
    }
}
