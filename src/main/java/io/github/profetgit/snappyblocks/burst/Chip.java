package io.github.profetgit.snappyblocks.burst;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/** A chunky chip cut from a block's texture: a vanilla terrain particle whose flight follows the block's material. */
public final class Chip extends TerrainParticle {
    /** How chips of a material fly (PRD §5.3 materials, chosen by the block's sound type). */
    public enum Material {
        PLAIN(1, 1, 1, 0.98f),
        /** Flatter and faster. */
        GLASS(1.3, 0.35, 1.1, 0.98f),
        /** Drift down slowly. */
        LEAVES(0.7, 0.8, 0.18, 0.9f),
        /** Fall sooner. */
        GRANULAR(0.8, 0.6, 1.7, 0.98f),
        /** Soft, a short arc. */
        WOOL(0.55, 0.8, 0.8, 0.94f);

        final double speed, lift;
        final float gravity, friction;

        Material(double speed, double lift, double gravity, float friction) {
            this.speed = speed;
            this.lift = lift;
            this.gravity = (float) gravity;
            this.friction = friction;
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

    public Chip(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, BlockState state, BlockPos pos) {
        super(level, x, y, z, 0, 0, 0, state, pos);
        this.setParticleSpeed(xd, yd, zd);
    }

    /** A chip shrinks away over its last ticks: a 2x chip near the camera that blinks out reads as a pop. */
    @Override
    public float getQuadSize(float a) {
        float left = this.lifetime - this.age - a;
        return super.getQuadSize(a) * Math.clamp(left / FADE_TICKS, 0f, 1f);
    }

    private static final float FADE_TICKS = 4;

    /** Sets the speed (blocks per tick) along a direction, shaped by the material. */
    public Chip fly(Material m, double xd, double yd, double zd) {
        this.setParticleSpeed(xd * m.speed, yd * m.speed * m.lift, zd * m.speed);
        this.gravity = m.gravity;
        this.friction = m.friction;
        return this;
    }
}
