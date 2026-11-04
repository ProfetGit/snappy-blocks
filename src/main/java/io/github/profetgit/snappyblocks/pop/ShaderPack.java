package io.github.profetgit.snappyblocks.pop;

import io.github.profetgit.snappyblocks.SnappyBlocks;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Whether an Iris shader pack is in use (reflective, no Iris dependency). Pattern from Seamless Displays' IrisCompat. */
public final class ShaderPack {
    private static MethodHandle inUse;
    private static boolean tried;

    private ShaderPack() {}

    public static boolean active() {
        if (!tried) {
            tried = true;
            if (SnappyBlocks.platform().isModLoaded("iris")) {
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
