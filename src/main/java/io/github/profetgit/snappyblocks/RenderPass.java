package io.github.profetgit.snappyblocks;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
//? if >=1.21.2 {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
//?} else {
/*import io.github.profetgit.snappyblocks.pop.ShaderPack;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
*///?}

/** What the world draw hook hands to the effects: the pose stack, the camera and where blocks and items are submitted. */
public final class RenderPass {
    public final PoseStack ps;
    public final Vec3 cam;
    public final Matrix4fc projection, viewRotation;
//? if >=1.21.2 {
    public final SubmitNodeCollector collector;
    public final CameraRenderState camera;

    public RenderPass(PoseStack ps, SubmitNodeCollector collector, LevelRenderState state) {
        this.ps = ps;
        this.collector = collector;
        this.camera = state.cameraRenderState;
        this.cam = state.cameraRenderState.pos;
        this.projection = state.cameraRenderState.projectionMatrix;
        this.viewRotation = state.cameraRenderState.viewRotationMatrix;
    }

    public void flush() {
    }
//?} else {
/*    public final MultiBufferSource.BufferSource buffers;
    private final Set<RenderType> used = new LinkedHashSet<>();

    public RenderPass(PoseStack ps, MultiBufferSource.BufferSource buffers, Vec3 cam, Matrix4fc projection, Matrix4fc viewRotation) {
        this.ps = ps;
        this.buffers = buffers;
        this.cam = cam;
        this.projection = projection;
        this.viewRotation = viewRotation;
    }

    public void used(RenderType type) {
        used.add(type);
    }

    public void flush() {
        for (RenderType t : used) {
            // block copies are lit like the chunk mesh when drawn in Iris's terrain phase (items and the rest stay as they are)
            String phase = t == RenderType.solid() ? "TERRAIN_SOLID" : t == RenderType.cutout() ? "TERRAIN_CUTOUT"
                : t == RenderType.cutoutMipped() ? "TERRAIN_CUTOUT_MIPPED" : t == RenderType.translucent() ? "TERRAIN_TRANSLUCENT" : null;
            if (phase != null) ShaderPack.phase(phase);
            buffers.endBatch(t);
            if (phase != null) ShaderPack.phase(null);
        }
    }
*///?}
}
