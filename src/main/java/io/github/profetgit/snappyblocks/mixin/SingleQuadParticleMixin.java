package io.github.profetgit.snappyblocks.mixin;

import io.github.profetgit.snappyblocks.burst.Chip;
import io.github.profetgit.snappyblocks.burst.Cubes;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
//?} else if >=1.21.2 {
/*import net.minecraft.client.renderer.state.QuadParticleRenderState;
*///?} else {
/*import com.mojang.blaze3d.vertex.VertexConsumer;
*///?}

/**
 * 3D block particles ({@link Cubes}): a terrain particle's one camera-facing quad becomes the sides of a cube that face
 * the camera. Priority: from 1.21 after Sodium, so the injection survives its replacement of the quad writer; on 1.20.1
 * before it, since Sodium 0.5 cancels the same method from its own HEAD injection and the first one applied runs first.
 */
//? if >=1.21 {
@Mixin(value = SingleQuadParticle.class, priority = 1500)
//?} else {
/*@Mixin(value = SingleQuadParticle.class, priority = 500)
*///?}
public abstract class SingleQuadParticleMixin extends Particle {
    @Shadow protected float quadSize;
//? if >=1.21.2 {
    @Shadow protected float rCol;
    @Shadow protected float gCol;
    @Shadow protected float bCol;

    @Shadow protected abstract void extractRotatedQuad(QuadParticleRenderState state, Quaternionf rotation, float x, float y, float z, float partialTick);
//?} else if >=1.21 {
    /*@Shadow protected abstract void renderRotatedQuad(VertexConsumer buffer, Quaternionf rotation, float x, float y, float z, float partialTick);
*///?} else {
    /*@Shadow protected abstract float getU0();
    @Shadow protected abstract float getU1();
    @Shadow protected abstract float getV0();
    @Shadow protected abstract float getV1();
*///?}

    @Shadow public abstract float getQuadSize(float partialTick);

    @Unique private int snappy_blocks$seed;
    /** The age the cube touched down, negative while it flies. */
    @Unique private float snappy_blocks$landed = -1;

    private SingleQuadParticleMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

//? if >=1.21.2 {
    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void snappy_blocks$cube(QuadParticleRenderState out, Camera camera, float partialTick, CallbackInfo ci) {
        if (!Cubes.draws(this)) return;
        ci.cancel();
        snappy_blocks$draw(out, camera.position(), partialTick);
    }
//?} else {
    /*@Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void snappy_blocks$cube(VertexConsumer out, Camera camera, float partialTick, CallbackInfo ci) {
        if (!Cubes.draws(this)) return;
        ci.cancel();
        snappy_blocks$draw(out, camera.getPosition(), partialTick);
    }
*///?}

    @Unique
//? if >=1.21.2 {
    private void snappy_blocks$draw(QuadParticleRenderState out, Vec3 cam, float partialTick) {
//?} else {
    /*private void snappy_blocks$draw(VertexConsumer out, Vec3 cam, float partialTick) {
*///?}
        long start = System.nanoTime();
        while (snappy_blocks$seed == 0) snappy_blocks$seed = this.random.nextInt();
        float t = this.age + partialTick;
        if (this.onGround && snappy_blocks$landed < 0) snappy_blocks$landed = t;
        float size = this.quadSize, r = this.rCol, g = this.gCol, b = this.bCol;
        this.quadSize = size * Cubes.size();
        float h = this.getQuadSize(partialTick);
        float x = (float) (Mth.lerp(partialTick, this.xo, this.x) - cam.x);
        float y = (float) (Mth.lerp(partialTick, this.yo, this.y) - cam.y);
        float z = (float) (Mth.lerp(partialTick, this.zo, this.z) - cam.z);
        // vanilla's crumbs sit with their centre on the floor; the mod's chips are centred in their box already
        if (!((Object) this instanceof Chip)) y += h;
        Quaternionf turn = Cubes.rotation(snappy_blocks$seed, t, snappy_blocks$landed, new Quaternionf()), quad = new Quaternionf();
        Vector3f n = new Vector3f();
        for (int i = 0; h > 0 && i < Cubes.SIDES; i++) {
            Cubes.side(i, turn, quad, n);
            // seen from the camera (the origin) only if the side faces it
            if (n.x * x + n.y * y + n.z * z + h >= 0) continue;
            float shade = Cubes.shade(n);
            this.rCol = Math.min(1, r * shade);
            this.gCol = Math.min(1, g * shade);
            this.bCol = Math.min(1, b * shade);
            float fx = x + n.x * h, fy = y + n.y * h, fz = z + n.z * h;
//? if >=1.21.2 {
            this.extractRotatedQuad(out, quad, fx, fy, fz, partialTick);
//?} else if >=1.21 {
            /*this.renderRotatedQuad(out, quad, fx, fy, fz, partialTick);
*///?} else {
            /*int light = this.getLightColor(partialTick);
            float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
            snappy_blocks$vertex(out, quad, fx, fy, fz, 1, -1, h, u1, v1, light);
            snappy_blocks$vertex(out, quad, fx, fy, fz, 1, 1, h, u1, v0, light);
            snappy_blocks$vertex(out, quad, fx, fy, fz, -1, 1, h, u0, v0, light);
            snappy_blocks$vertex(out, quad, fx, fy, fz, -1, -1, h, u0, v1, light);
*///?}
        }
        this.quadSize = size;
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
        Cubes.drawn++;
        Cubes.spentNanos += System.nanoTime() - start;
    }

//? if <1.21 {
    /*@Unique
    private void snappy_blocks$vertex(VertexConsumer out, Quaternionf q, float x, float y, float z, float cx, float cy, float h, float u, float v, int light) {
        Vector3f p = new Vector3f(cx, cy, 0).rotate(q).mul(h).add(x, y, z);
        out.vertex(p.x, p.y, p.z).uv(u, v).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
    }
*///?}
}
