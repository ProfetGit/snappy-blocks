// Compile-time stand-in for a Sodium class; excluded from the jar (build.fabric.gradle.kts). The real one comes from Sodium.
package net.caffeinemc.mods.sodium.client.render.chunk;

public class RenderSection {
    public int getChunkX() {
        return 0;
    }

    public int getChunkY() {
        return 0;
    }

    public int getChunkZ() {
        return 0;
    }
}
