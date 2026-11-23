package io.github.profetgit.snappyblocks.pop;

//? if >=26.1 {
//?} else if >=1.21.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

// 1.21.11: Iris with `separateAo` wipes the brightness of vanilla's quad writes (MixinBufferBuilder_SeparateAo) and the chunk mesh keeps the
// ambient occlusion in the vertex alpha, so a copy drawn with smooth lighting on had none (a pop of colour at the hand-off). This writes the
// quads like VertexConsumer.putBulkData with the ambient occlusion as the alpha.
public record AoConsumer(VertexConsumer d) implements VertexConsumer {
    public static VertexConsumer wrap(VertexConsumer out) {
        return ShaderPack.separateAo() ? new AoConsumer(out) : out;
    }

    @Override public VertexConsumer addVertex(float x, float y, float z) { return d.addVertex(x, y, z); }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) { return d.setColor(r, g, b, a); }
    @Override public VertexConsumer setColor(int argb) { return d.setColor(argb); }
    @Override public VertexConsumer setUv(float u, float v) { return d.setUv(u, v); }
    @Override public VertexConsumer setUv1(int u, int v) { return d.setUv1(u, v); }
    @Override public VertexConsumer setUv2(int u, int v) { return d.setUv2(u, v); }
    @Override public VertexConsumer setNormal(float x, float y, float z) { return d.setNormal(x, y, z); }
    @Override public VertexConsumer setLineWidth(float w) { return d.setLineWidth(w); }

    @Override
    public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] ao, float red, float green, float blue, float alpha, int[] light, int overlay) {
        Vector3fc dir = quad.direction().getUnitVec3f();
        Matrix4f m = pose.pose();
        Vector3f n = pose.transformNormal(dir, new Vector3f());
        int emission = quad.lightEmission();
        for (int i = 0; i < 4; i++) {
            long uv = quad.packedUV(i);
            Vector3f p = m.transformPosition(quad.position(i), new Vector3f());
            float a = Math.max(0f, Math.min(1f, ao[i] * alpha));
            d.addVertex(p.x(), p.y(), p.z(), ARGB.colorFromFloat(a, red, green, blue), UVPair.unpackU(uv), UVPair.unpackV(uv), overlay,
                LightTexture.lightCoordsWithEmission(light[i], emission), n.x(), n.y(), n.z());
        }
    }
}
*///?} else if >=1.21 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Vec3i;
import net.minecraft.util.FastColor;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// Iris with `separateAo` wipes the brightness of vanilla's block quads and never moves it to the alpha where the chunk mesh keeps it:
// this writes the quads like VertexConsumer.putBulkData, with the ambient occlusion as the vertex alpha
record AoConsumer(VertexConsumer d) implements VertexConsumer {
    static VertexConsumer wrap(VertexConsumer out) {
        return ShaderPack.separateAo() ? new AoConsumer(out) : out;
    }

    @Override public VertexConsumer addVertex(float x, float y, float z) { return d.addVertex(x, y, z); }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) { return d.setColor(r, g, b, a); }
    @Override public VertexConsumer setUv(float u, float v) { return d.setUv(u, v); }
    @Override public VertexConsumer setUv1(int u, int v) { return d.setUv1(u, v); }
    @Override public VertexConsumer setUv2(int u, int v) { return d.setUv2(u, v); }
    @Override public VertexConsumer setNormal(float x, float y, float z) { return d.setNormal(x, y, z); }

    @Override
    public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] ao, float red, float green, float blue, float alpha, int[] light, int overlay, boolean readColor) {
        int[] v = quad.getVertices();
        Vec3i dir = quad.getDirection().getNormal();
        Matrix4f m = pose.pose();
        Vector3f n = pose.transformNormal(dir.getX(), dir.getY(), dir.getZ(), new Vector3f());
        for (int i = 0; i < v.length / 8; i++) {
            int o = i * 8;
            Vector3f p = m.transformPosition(Float.intBitsToFloat(v[o]), Float.intBitsToFloat(v[o + 1]), Float.intBitsToFloat(v[o + 2]), new Vector3f());
            int c = v[o + 3];
            float r = readColor ? (c & 0xFF) * red : red * 255f;
            float g = readColor ? (c >> 8 & 0xFF) * green : green * 255f;
            float b = readColor ? (c >> 16 & 0xFF) * blue : blue * 255f;
            int a = Math.max(0, Math.min(255, (int) (ao[i] * alpha * 255f)));
            d.addVertex(p.x(), p.y(), p.z(), FastColor.ARGB32.color(a, (int) r, (int) g, (int) b), Float.intBitsToFloat(v[o + 4]),
                Float.intBitsToFloat(v[o + 5]), overlay, light[i], n.x(), n.y(), n.z());
        }
    }
}
*///?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Vec3i;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

// The same for 1.20.1's VertexConsumer (Iris 1.7 keeps the AO only for putBulkData on its own BufferBuilder path)
record AoConsumer(VertexConsumer d) implements VertexConsumer {
    static VertexConsumer wrap(VertexConsumer out) {
        return ShaderPack.separateAo() ? new AoConsumer(out) : out;
    }

    @Override public VertexConsumer vertex(double x, double y, double z) { return d.vertex(x, y, z); }
    @Override public VertexConsumer color(int r, int g, int b, int a) { return d.color(r, g, b, a); }
    @Override public VertexConsumer uv(float u, float v) { return d.uv(u, v); }
    @Override public VertexConsumer overlayCoords(int u, int v) { return d.overlayCoords(u, v); }
    @Override public VertexConsumer uv2(int u, int v) { return d.uv2(u, v); }
    @Override public VertexConsumer normal(float x, float y, float z) { return d.normal(x, y, z); }
    @Override public void endVertex() { d.endVertex(); }
    @Override public void defaultColor(int r, int g, int b, int a) { d.defaultColor(r, g, b, a); }
    @Override public void unsetDefaultColor() { d.unsetDefaultColor(); }

    @Override
    public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] ao, float red, float green, float blue, int[] light, int overlay, boolean readColor) {
        int[] v = quad.getVertices();
        Vec3i dir = quad.getDirection().getNormal();
        Matrix4f m = pose.pose();
        Vector3f n = pose.normal().transform(new Vector3f(dir.getX(), dir.getY(), dir.getZ()));
        for (int i = 0; i < v.length / 8; i++) {
            int o = i * 8;
            Vector4f p = m.transform(new Vector4f(Float.intBitsToFloat(v[o]), Float.intBitsToFloat(v[o + 1]), Float.intBitsToFloat(v[o + 2]), 1f));
            int c = v[o + 3];
            float r = readColor ? (c & 0xFF) / 255f * red : red;
            float g = readColor ? (c >> 8 & 0xFF) / 255f * green : green;
            float b = readColor ? (c >> 16 & 0xFF) / 255f * blue : blue;
            d.vertex(p.x(), p.y(), p.z(), r, g, b, Math.max(0f, Math.min(1f, ao[i])), Float.intBitsToFloat(v[o + 4]), Float.intBitsToFloat(v[o + 5]),
                overlay, light[i], n.x(), n.y(), n.z());
        }
    }
}
*///?}
