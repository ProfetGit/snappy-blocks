package io.github.profetgit.snappyblocks.pop;

import io.github.profetgit.snappyblocks.RenderPass;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.2 {
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.world.level.material.FluidState;
//?} else {
/*import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
*///?}

/**
 * The copy's view of the world: the block itself, and the real level around it. Vanilla's moving block sees only air
 * around it (no ambient occlusion from neighbours); with the real neighbours the copy is lit exactly like the chunk
 * mesh that takes over from it, so the hand-off shows no shading step.
 */
//? if >=1.21.2 {
public final class PopRenderState extends MovingBlockRenderState {
    public ClientLevel level;

    public void init(ClientLevel level, BlockPos pos, BlockState state) {
        this.level = level;
        this.blockPos = pos;
        this.randomSeedPos = pos;
        this.blockState = state;
        this.biome = level.getBiome(pos);
        this.cardinalLighting = level.cardinalLighting();
        this.lightEngine = level.getLightEngine();
    }

    public void state(BlockState state) {
        this.blockState = state;
    }

    /** With the pose at the block's corner. */
    public void draw(RenderPass pass) {
        pass.collector.submitMovingBlock(pass.ps, this, 0);
    }

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
//?} else {
/*public final class PopRenderState {
    private static final RandomSource RANDOM = RandomSource.create();
    public ClientLevel level;
    private BlockPos pos;
    private BlockState blockState;

    public void init(ClientLevel level, BlockPos pos, BlockState state) {
        this.level = level;
        this.pos = pos;
        this.blockState = state;
    }

    public void state(BlockState state) {
        this.blockState = state;
    }

    // the piston's moving-block path: the real level around the block gives the chunk mesh's own light and shading
    public void draw(RenderPass pass) {
        RenderType type = ItemBlockRenderTypes.getMovingBlockRenderType(blockState);
        RANDOM.setSeed(blockState.getSeed(pos));
        VertexConsumer out = AoConsumer.wrap(pass.buffers.getBuffer(type));
        Minecraft.getInstance().getBlockRenderer().renderBatched(blockState, pos, level, pass.ps, out, true, RANDOM);
        pass.used(type);
    }
}
*///?}
