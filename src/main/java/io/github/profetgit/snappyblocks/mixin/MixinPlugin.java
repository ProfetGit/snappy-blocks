package io.github.profetgit.snappyblocks.mixin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Mixins into other mods (package `compat`, Sodium and Iris) apply only when their target class exists and, if listed
 * in {@link #METHODS}, has a method with exactly that name and descriptor. A handler whose descriptor doesn't match
 * fails with "Invalid descriptor" even with require = 0 and crashes the game (Traveler's Lantern 0.2.14), so a
 * mismatch skips the mixin instead. Pattern from mods/BeltLantern's MixinPlugin.
 */
public final class MixinPlugin implements IMixinConfigPlugin {
//? if >=1.21.2 {
    private static final String IRIS_SHADOWS = "renderShadows(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;Lnet/minecraft/client/Camera;"
        + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";
//?} else {
    /*// Fabric and Quilt run intermediary names (class_4184 = Camera), NeoForge and Forge Mojang names
    private static final String IRIS_SHADOWS = "renderShadows(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;Lnet/minecraft/client/Camera;)V"
        + "~renderShadows(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;Lnet/minecraft/class_4184;)V";
*///?}
    /** Mixin simple name -> "methodName" + descriptor its handlers need ("|": all of them, "~": any of these). Filled as compat mixins are added (M1). */
    private static final Map<String, String> METHODS = mapOf(
        "SodiumMeshingTaskMixin", "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;"
            + "Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)"
            + "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
        "SodiumMeshingTaskInit8Mixin", "<init>(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;ILorg/joml/Vector3dc;"
            + "Lnet/caffeinemc/mods/sodium/client/world/cloned/ChunkRenderContext;"
            + "Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/SortBehavior;ZZJ)V",
        "SodiumMeshingTaskInit7Mixin", "<init>(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;ILorg/joml/Vector3dc;"
            + "Lnet/caffeinemc/mods/sodium/client/world/cloned/ChunkRenderContext;"
            + "Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/SortBehavior;ZZ)V",
        "SodiumMeshingTaskInit6Mixin", "<init>(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;ILorg/joml/Vector3dc;"
            + "Lnet/caffeinemc/mods/sodium/client/world/cloned/ChunkRenderContext;"
            + "Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/SortBehavior;Z)V",
        "SodiumOcclusionCacheMixin", "shouldDrawSide(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
        "IrisShadowMixin", IRIS_SHADOWS,
        "SodiumBlockRenderContextMixin", "shouldDrawSide(Lnet/minecraft/core/Direction;)Z",
        "SodiumNonTerrainAoMixin", "shadeQuad(Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;"
            + "Lnet/caffeinemc/mods/sodium/client/model/light/LightMode;ZLnet/caffeinemc/mods/sodium/client/render/model/SodiumShadeMode;)V",
        "Sodium05MeshingTaskMixin", "<init>(Lme/jellysquid/mods/sodium/client/render/chunk/RenderSection;"
            + "Lme/jellysquid/mods/sodium/client/world/cloned/ChunkRenderContext;I)V"
            + "|execute(Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildContext;"
            + "Lme/jellysquid/mods/sodium/client/util/task/CancellationToken;)Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
        "Sodium05MeshLiveMixin", "createRebuildTask(Lme/jellysquid/mods/sodium/client/render/chunk/RenderSection;I)"
            + "Lme/jellysquid/mods/sodium/client/render/chunk/compile/tasks/ChunkBuilderMeshingTask;"
            + "|processChunkBuildResults(Ljava/util/ArrayList;)V",
        "SodiumMeshLive09Mixin", "createRebuildTask(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;IZ)"
            + "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/tasks/ChunkBuilderMeshingTask;"
            + "|processChunkBuildResults(Ljava/util/ArrayList;Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;"
            + "Lnet/caffeinemc/mods/sodium/client/render/chunk/UniformBufferManager;)I",
        "SodiumMeshLiveMixin", "createRebuildTask(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;I)"
            + "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/tasks/ChunkBuilderMeshingTask;"
            + "|processChunkBuildResults(Ljava/util/ArrayList;)Z");

    private static Map<String, String> mapOf(String... kv) {
        Map<String, String> m = new java.util.HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.contains(".compat.")) return true;
//? if >=1.21.2 {
        if (mixinClassName.endsWith(".SodiumMeshLiveMixin")) return false;
//?}
//? if >=26.3 || <1.21.2 {
        if (mixinClassName.endsWith(".SodiumMeshLive09Mixin")) return false;
//?}
        ClassNode node;
        try {
            node = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
        } catch (Throwable absent) {
            return false;
        }
        String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        String wanted = METHODS.get(name);
        if (wanted == null) return true;
        for (String one : wanted.split("\\|")) {
            boolean found = false;
            List<String> any = List.of(one.split("~"));
            for (MethodNode m : node.methods) {
                if (any.contains(m.name + m.desc)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                org.slf4j.LoggerFactory.getLogger("Snappy Blocks").warn("Snappy Blocks: {} skipped, {} has no {}", name, targetClassName, one);
                return false;
            }
        }
        org.slf4j.LoggerFactory.getLogger("Snappy Blocks").info("Snappy Blocks: {} applies", name);
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
