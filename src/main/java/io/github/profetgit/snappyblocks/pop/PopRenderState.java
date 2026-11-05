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
import io.github.profetgit.snappyblocks.SnappyBlocks;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
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
        RANDOM.setSeed(blockState.getSeed(pos));
        RenderType type = layer(blockState, RANDOM);
        if (type == RenderType.translucent()) type = RenderType.translucentMovingBlock();
        RANDOM.setSeed(blockState.getSeed(pos));
        VertexConsumer out = AoConsumer.wrap(pass.buffers.getBuffer(type));
        Minecraft.getInstance().getBlockRenderer().renderBatched(blockState, pos, level, pass.ps, out, true, RANDOM);
        pass.used(type);
    }

    // Forge and NeoForge models can name their own layer (model JSON "render_type": "cutout"), which ItemBlockRenderTypes
    // doesn't know: modded plants then came out solid (their transparent pixels as planes). Ask the model, reflectively,
    // so this source builds on every loader.
    private static MethodHandle modelTypes, setContains;
    private static Object emptyData;
    private static boolean tried;

    public static RenderType layer(BlockState state, RandomSource random) {
        RenderType vanilla = ItemBlockRenderTypes.getChunkRenderType(state);
        if (!tried) {
            tried = true;
            for (String name : new String[] {"net.neoforged.neoforge.client.model.data.ModelData", "net.minecraftforge.client.model.data.ModelData"}) {
                try {
                    Class<?> data = Class.forName(name);
                    Method m = BakedModel.class.getMethod("getRenderTypes", BlockState.class, RandomSource.class, data);
                    emptyData = data.getField("EMPTY").get(null);
                    modelTypes = MethodHandles.publicLookup().unreflect(m);
                    setContains = MethodHandles.publicLookup().findVirtual(m.getReturnType(), "contains", MethodType.methodType(boolean.class, RenderType.class));
                    break;
                } catch (ClassNotFoundException absent) {
                    // not this loader
                } catch (Throwable t) {
                    modelTypes = null;
                    SnappyBlocks.LOG.warn("Snappy Blocks: can't read model render types, copies use the vanilla layer ({})", t.toString());
                    break;
                }
            }
        }
        if (modelTypes == null) return vanilla;
        try {
            Object set = modelTypes.invoke(Minecraft.getInstance().getBlockRenderer().getBlockModel(state), state, random, emptyData);
            for (RenderType t : new RenderType[] {RenderType.translucent(), RenderType.cutout(), RenderType.cutoutMipped()}) {
                if ((boolean) setContains.invoke(set, t)) return t;
            }
        } catch (Throwable t) {
            modelTypes = null;
            SnappyBlocks.LOG.warn("Snappy Blocks: can't read model render types, copies use the vanilla layer ({})", t.toString());
        }
        return vanilla;
    }
}
*///?}
