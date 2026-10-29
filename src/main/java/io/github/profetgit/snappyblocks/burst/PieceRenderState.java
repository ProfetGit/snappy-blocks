package io.github.profetgit.snappyblocks.burst;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;

/**
 * A flying piece is lit by the cell it is in, on every face. Vanilla's moving block takes each face's light from the
 * cell next to its block position, so a piece's face that touched the ground stayed black as it turned.
 */
final class PieceRenderState extends MovingBlockRenderState {
    final BlockPos.MutableBlockPos lightPos = new BlockPos.MutableBlockPos();

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return lightEngine.getLayerListener(layer).getLightValue(lightPos);
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return lightEngine.getRawBrightness(lightPos, darkening);
    }
}
