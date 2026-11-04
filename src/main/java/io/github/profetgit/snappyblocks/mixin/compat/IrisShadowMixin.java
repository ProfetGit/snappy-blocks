package io.github.profetgit.snappyblocks.mixin.compat;

//? if <1.21.2 {
/*import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.snappyblocks.RenderPass;
import io.github.profetgit.snappyblocks.pop.Pops;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Iris draws its shadow map by itself (terrain, entities, block entities) and never runs the world hook that draws our
// copies, so a block growing in under shaders cast no shadow until it was handed back to the chunk mesh. This draws the
// pop copies into the shadow pass too, after the block entities, from the shadow's own pose and camera. Iris 1.8 on 1.21.1
// (MixinPlugin checks the method).
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shadows.ShadowRenderer", remap = false)
public abstract class IrisShadowMixin {
    @Inject(method = "renderShadows", remap = false, require = 0, at = @At(value = "INVOKE", remap = true, ordinal = 4,
        target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V"))
    private void snappy_blocks$shadowPops(CallbackInfo ci, @Local PoseStack modelView, @Local MultiBufferSource.BufferSource buffers,
                                          @Local(ordinal = 0) double cameraX, @Local(ordinal = 1) double cameraY, @Local(ordinal = 2) double cameraZ) {
        Pops.submitShadow(new RenderPass(modelView, buffers, new Vec3(cameraX, cameraY, cameraZ), new Matrix4f(), new Matrix4f()));
    }
}
*///?} else {
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Iris 1.8 (1.21.1) only: the shadow pass has no world hook to join there; MixinPlugin skips this stand-in. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shadows.ShadowRenderer", remap = false)
public abstract class IrisShadowMixin {
}
//?}
