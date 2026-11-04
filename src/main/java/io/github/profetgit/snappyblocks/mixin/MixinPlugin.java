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
    /** Mixin simple name -> "methodName" + descriptor its handlers need. Filled as compat mixins are added (M1). */
    private static final Map<String, String> METHODS = Map.of(
        "SodiumMeshingTaskMixin", "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;"
            + "Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)"
            + "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
        "SodiumMeshingTaskInit8Mixin", "<init>(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;ILorg/joml/Vector3dc;"
            + "Lnet/caffeinemc/mods/sodium/client/world/cloned/ChunkRenderContext;"
            + "Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/SortBehavior;ZZJ)V",
        "SodiumMeshingTaskInit6Mixin", "<init>(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;ILorg/joml/Vector3dc;"
            + "Lnet/caffeinemc/mods/sodium/client/world/cloned/ChunkRenderContext;"
            + "Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/SortBehavior;Z)V",
        "SodiumOcclusionCacheMixin", "shouldDrawSide(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
        "IrisShadowMixin", "renderShadows(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;Lnet/minecraft/client/Camera;)V",
        "SodiumBlockRenderContextMixin", "shouldDrawSide(Lnet/minecraft/core/Direction;)Z",
        "SodiumMeshLiveMixin", "createRebuildTask(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;I)"
            + "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/tasks/ChunkBuilderMeshingTask;"
            + "|processChunkBuildResults(Ljava/util/ArrayList;)Z");

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
        if (mixinClassName.endsWith(".SodiumMeshLiveMixin") || mixinClassName.endsWith(".IrisShadowMixin")) return false;
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
            for (MethodNode m : node.methods) {
                if ((m.name + m.desc).equals(one)) {
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
