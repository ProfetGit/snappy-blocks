package io.github.profetgit.snappyblocks.burst;

import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.Tune;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A chunky chip cut from a block's texture: a vanilla terrain particle whose flight follows the block's material. It
 * spins while it flies, bounces off the floor, rests and shrinks away. Its position is the centre of its box.
 */
public final class Chip extends TerrainParticle {
    /** How chips of a material fly (PRD §5.3 materials, chosen by the block's sound type). */
    public enum Material {
        PLAIN(1, 1, 1, 0.98f, 0.55, 0.85),
        /** Flatter and faster, skittering on landing. */
        GLASS(1.3, 0.35, 1.1, 0.98f, 0.7, 1.15),
        /** Drift down slowly. */
        LEAVES(0.7, 0.8, 0.18, 0.9f, 0, 1),
        /** Fall sooner and stay where they land. */
        GRANULAR(0.8, 0.6, 1.7, 0.98f, 0, 1),
        /** Soft, a short arc and a dull hop. */
        WOOL(0.55, 0.8, 0.8, 0.94f, 0.25, 0.5);

        final double speed, lift, bounce, skid;
        final float gravity, friction;

        Material(double speed, double lift, double gravity, float friction, double bounce, double skid) {
            this.speed = speed;
            this.lift = lift;
            this.gravity = (float) gravity;
            this.friction = friction;
            this.bounce = bounce;
            this.skid = skid;
        }

        /** A burst chip's speed (blocks per tick) along a direction, shaped by this material. */
        public double[] speed(double xd, double yd, double zd) {
            return new double[] {xd * speed, yd * speed * lift, zd * speed};
        }

        public float gravity() {
            return gravity;
        }

        public float friction() {
            return friction;
        }

        /** The share of its sideways speed a chip keeps through a bounce, over vanilla's ground drag. */
        public double skid() {
            return skid;
        }

        public static Material of(BlockState s) {
            if (s.is(BlockTags.LEAVES)) return LEAVES;
            SoundType t = s.getSoundType();
            if (t == SoundType.GLASS) return GLASS;
            if (t == SoundType.SAND || t == SoundType.GRAVEL || t == SoundType.SUSPICIOUS_SAND || t == SoundType.SUSPICIOUS_GRAVEL) return GRANULAR;
            if (t == SoundType.WOOL) return WOOL;
            return PLAIN;
        }
    }

    static final String BOUNCE = Tune.def("chip.bounce", 1);
    static final String SPIN = Tune.def("chip.spin", 0.3);
    /** A chip bounces at most this often, and only when it lands faster than this (blocks per tick). */
    public static final int BOUNCES = 2;
    public static final double BOUNCE_MIN = 0.08;
    /** A chip of factor 1 has this half size (blocks): the middle of vanilla's own 0.05 to 0.1. */
    public static final float BASE = 0.075f;
    public static final float FADE_TICKS = 6;
    /** Bounces made (test hook). */
    public static int bounced;

    private Material material = Material.PLAIN;
    private int bounces;
    private float spin;

    public Chip(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, BlockState state, BlockPos pos) {
        super(level, x, y, z, 0, 0, 0, state, pos);
        this.setParticleSpeed(xd, yd, zd);
    }

    /** The speed (blocks per tick) a chip leaves the floor with after landing at {@code fall} (negative), or 0: it stays. */
    public static double bounce(Material m, double fall, int bounces) {
        if (bounces >= BOUNCES || fall > -BOUNCE_MIN) return 0;
        return -fall * m.bounce * Tune.get(BOUNCE) * (Config.get().calm ? 0.3 : 1);
    }

    /** A flat chip's spin (radians per tick); {@code r} in -0.5..0.5. */
    public static float spin(double r) {
        return (float) (Tune.get(SPIN) * (r < 0 ? r - 0.5 : r + 0.5) * Config.get().amplitude());
    }

    @Override
    public void tick() {
        double fall = this.yd - 0.04 * this.gravity;
        boolean was = this.onGround;
        super.tick();
        if (!was && this.onGround) {
            double up = bounce(material, fall, bounces);
            if (up > 0) {
                bounces++;
                bounced++;
                this.yd = up;
                // vanilla's ground drag has just been applied
                this.xd *= material.skid / 0.7;
                this.zd *= material.skid / 0.7;
                this.onGround = false;
                this.spin *= 0.6f;
            }
        }
        this.oRoll = this.roll;
        if (!this.onGround) this.roll += this.spin;
        else this.roll = rest(this.roll);
    }

    /** A landed flat chip turns square to the floor, half the way each tick. */
    public static float rest(float roll) {
        float quarter = (float) (Math.PI / 2);
        return roll + (Math.round(roll / quarter) * quarter - roll) * 0.5f;
    }

    /** A chip shrinks away over its last ticks: a 2x chip near the camera that blinks out reads as a pop. */
    @Override
    public float getQuadSize(float a) {
        float left = this.lifetime - this.age - a;
        return super.getQuadSize(a) * Math.clamp(left / FADE_TICKS, 0f, 1f);
    }

    /** Half the box of a chip of this size factor: what is drawn, flat or as a cube. */
    public static double half(float factor) {
        return BASE * factor * (Cubes.on() ? Cubes.size() : 1);
    }

    /** Sizes the chip ({@code factor} x {@link #BASE}) and its box, so it rests on the floor it is drawn on. */
    public Chip sized(float factor) {
        this.quadSize = BASE * factor;
        double h = half(factor);
        this.setSize((float) (2 * h), (float) (2 * h));
        this.setBoundingBox(new AABB(this.x - h, this.y - h, this.z - h, this.x + h, this.y + h, this.z + h));
        return this;
    }

    @Override
    protected void setLocationFromBoundingbox() {
        AABB box = this.getBoundingBox();
        this.x = (box.minX + box.maxX) / 2;
        this.y = (box.minY + box.maxY) / 2;
        this.z = (box.minZ + box.maxZ) / 2;
    }

    /** Sets the speed (blocks per tick) along a direction, shaped by the material, and how long the chip lasts. */
    public Chip fly(Material m, double xd, double yd, double zd, int lifetime) {
        double[] s = m.speed(xd, yd, zd);
        this.setParticleSpeed(s[0], s[1], s[2]);
        this.material = m;
        this.gravity = m.gravity;
        this.friction = m.friction;
        this.lifetime = lifetime;
        this.spin = spin(this.random.nextDouble() - 0.5);
        return this;
    }
}
