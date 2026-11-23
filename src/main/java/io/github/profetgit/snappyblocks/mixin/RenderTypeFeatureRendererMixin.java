package io.github.profetgit.snappyblocks.mixin;

//? if >=26.2 {
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.profetgit.snappyblocks.pop.ShaderPack;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every moving-block quad (vanilla's loop and Fabric API's quad consumer alike) gets its buffer here. Iris writes a chunk-format
 * buffer's per-vertex block id (mc_Entity: the shader pack's ore glow and other id-driven looks) from beginBlock, which vanilla's
 * moving blocks never call, so a copy drawn with the terrain program had no id.
 */
@Mixin(RenderTypeFeatureRenderer.class)
public abstract class RenderTypeFeatureRendererMixin {
    @Inject(method = "getVertexBuilder", require = 0, at = @At("RETURN"))
    private void snappy_blocks$tag(RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
        ShaderPack.tag(cir.getReturnValue());
    }
}
//?} else {
/*import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

// Placeholder: before 26.2 the copies are drawn elsewhere.
@Mixin(Minecraft.class)
public abstract class RenderTypeFeatureRendererMixin {
}
*///?}
