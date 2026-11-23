package io.github.profetgit.snappyblocks.pop;

import io.github.profetgit.snappyblocks.SnappyBlocks;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Whether an Iris shader pack is in use (reflective, no Iris dependency). Pattern from Seamless Displays' IrisCompat. */
public final class ShaderPack {
    private static MethodHandle inUse;
    private static boolean tried;

    private ShaderPack() {}

    private static MethodHandle setPhase, notifyPhase;
    private static Class<?> phases;
    private static boolean phaseTried;

    /**
     * Makes Iris draw the next batch with the program of a terrain phase (TERRAIN_SOLID, TERRAIN_CUTOUT, ...), or back to the
     * current phase for null. A copy of a block drawn in the entity phase gets the entity program and is lit differently from
     * the chunk mesh; in the terrain phase it is lit like it. Nothing happens without Iris or a shader pack.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void phase(String terrainPhase) {
        if (!active()) return;
        try {
            if (!phaseTried) {
                phaseTried = true;
                phases = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPhase");
                Class<?> g = Class.forName("net.irisshaders.iris.layer.GbufferPrograms");
                setPhase = MethodHandles.publicLookup().findStatic(g, "setOverridePhase", MethodType.methodType(void.class, phases));
                notifyPhase = MethodHandles.publicLookup().findStatic(g, "runPhaseChangeNotifier", MethodType.methodType(void.class));
            }
            if (setPhase == null) return;
            setPhase.invoke(terrainPhase == null ? null : Enum.valueOf((Class<? extends Enum>) phases, terrainPhase));
            notifyPhase.invoke();
        } catch (Throwable t) {
            setPhase = null;
            SnappyBlocks.LOG.warn("Snappy Blocks: can't set Iris's rendering phase, copies keep the entity program ({})", t.toString());
        }
    }

    private static MethodHandle separateAo;
    private static boolean aoTried;

    /**
     * Whether the shader pack asks for ambient occlusion in the vertex alpha (shaders.properties `separateAo`). Iris then
     * stores it there in the chunk mesh but drops it from vanilla's block batches, so a block copy would be drawn without it.
     */
    public static boolean separateAo() {
        if (!active()) return false;
        try {
            if (!aoTried) {
                aoTried = true;
                Class<?> w = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings");
                Object instance = w.getField("INSTANCE").get(null);
                separateAo = MethodHandles.publicLookup().findVirtual(w, "shouldUseSeparateAo", MethodType.methodType(boolean.class)).bindTo(instance);
            }
            return separateAo != null && (boolean) separateAo.invoke();
        } catch (Throwable t) {
            separateAo = null;
            SnappyBlocks.LOG.warn("Snappy Blocks: can't ask Iris about separate ambient occlusion ({})", t.toString());
            return false;
        }
    }

    private static MethodHandle stateIds, idOf, beginBlock;
    private static Class<?> sensitive;
    private static boolean tagTried;
    private static BlockState tagState;
    private static BlockPos tagPos;

    /** The block copy being tesselated (null when none), for {@link #tag}. */
    public static void tagging(BlockState state, BlockPos pos) {
        tagState = state;
        tagPos = pos;
    }

    /**
     * Gives the buffer of a block copy the block's id in the shader pack (Iris's per-vertex mc_Entity, plus emission and position in
     * the section). Iris writes those from {@code BlockSensitiveBufferBuilder.beginBlock}, which only its own chunk meshing calls;
     * moving blocks (our copies) are drawn with the terrain program but arrived with no id, so id-driven looks such as a pack's ore
     * glow were missing on them. Called for every buffer a copy's quads are written to.
     */
    public static void tag(VertexConsumer vc) {
        if (tagState == null || !active()) return;
        try {
            if (!tagTried) {
                tagTried = true;
                MethodHandles.Lookup l = MethodHandles.publicLookup();
                Class<?> w = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings");
                stateIds = l.findVirtual(w, "getBlockStateIds", MethodType.methodType(Class.forName("it.unimi.dsi.fastutil.objects.Object2IntMap")))
                    .bindTo(w.getField("INSTANCE").get(null));
                idOf = l.findVirtual(Class.forName("it.unimi.dsi.fastutil.objects.Object2IntFunction"), "getOrDefault", MethodType.methodType(int.class, Object.class, int.class));
                sensitive = Class.forName("net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder");
                beginBlock = l.findVirtual(sensitive, "beginBlock", MethodType.methodType(void.class, int.class, byte.class, byte.class, int.class, int.class, int.class));
            }
            if (beginBlock == null || !sensitive.isInstance(vc)) return;
            Object ids = stateIds.invoke();
            if (ids == null) return;
            int id = (int) idOf.invoke(ids, tagState, -1);
            if (id < 0) return;
            beginBlock.invoke(vc, id, (byte) 0, (byte) tagState.getLightEmission(), tagPos.getX() & 15, tagPos.getY() & 15, tagPos.getZ() & 15);
        } catch (Throwable t) {
            beginBlock = null;
            SnappyBlocks.LOG.warn("Snappy Blocks: can't give block copies their shader-pack id ({})", t.toString());
        }
    }

    /** Set while a block copy is tesselated under a shader pack with `separateAo` (26.x, MovingBlockFeatureRendererMixin). */
    public static boolean aoCopy;

    public static boolean active() {
        if (!tried) {
            tried = true;
            // Oculus (Iris for Embeddium, 1.20.1 Forge) keeps Iris's classes under its own mod id
            if (SnappyBlocks.platform().isModLoaded("iris") || SnappyBlocks.platform().isModLoaded("oculus")) {
                try {
                    inUse = MethodHandles.publicLookup().findStatic(Class.forName("net.irisshaders.iris.Iris"), "isPackInUseQuick",
                        MethodType.methodType(boolean.class));
                } catch (Throwable t) {
                    SnappyBlocks.LOG.warn("Snappy Blocks: can't ask Iris whether a shader pack is on ({})", t.toString());
                }
            }
        }
        if (inUse == null) return false;
        try {
            return (boolean) inUse.invokeExact();
        } catch (Throwable t) {
            inUse = null;
            return false;
        }
    }
}
