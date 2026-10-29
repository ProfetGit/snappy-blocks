// Compile-time stand-in for a Sodium class; excluded from the jar (build.fabric.gradle.kts). The real one comes from Sodium.
package net.caffeinemc.mods.sodium.client.world;

public class LevelSlice {
    public net.minecraft.world.level.block.state.BlockState getBlockState(int x, int y, int z) {
        throw new AssertionError();
    }
}
