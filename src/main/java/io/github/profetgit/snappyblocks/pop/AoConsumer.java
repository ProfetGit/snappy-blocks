package io.github.profetgit.snappyblocks.pop;

//? if >=1.21.2 {
//?} else if >=1.21 {
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
/*import com.mojang.blaze3d.vertex.VertexConsumer;

// 1.20.1: no Iris hook yet, blocks are drawn as they are
final class AoConsumer {
    static VertexConsumer wrap(VertexConsumer out) {
        return out;
    }
}
*///?}
