package io.github.profetgit.snappyblocks.pop;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * The copy's view of the world: the block itself, and the real level around it. Vanilla's moving block sees only air
 * around it (no ambient occlusion from neighbours); with the real neighbours the copy is lit exactly like the chunk
 * mesh that takes over from it, so the hand-off shows no shading step.
 */
public final class PopRenderState extends MovingBlockRenderState {
    public ClientLevel level;

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return pos.equals(this.blockPos) ? this.blockState : level.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public int getHeight() {
        return level.getHeight();
    }

    @Override
    public int getMinY() {
        return level.getMinY();
    }
}
