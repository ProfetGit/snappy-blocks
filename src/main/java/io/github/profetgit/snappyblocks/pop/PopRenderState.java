package io.github.profetgit.snappyblocks.pop;

import io.github.profetgit.snappyblocks.RenderPass;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
//? if >=1.21.2 {
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.lighting.LevelLightEngine;
*///?}

/**
 * The copy's view of the world: the block itself, and the real level around it. Vanilla's moving block sees only air
 * around it (no ambient occlusion from neighbours); with the real neighbours the copy is lit exactly like the chunk
 * mesh that takes over from it, so the hand-off shows no shading step.
 */
//? if >=1.21.2 {
public final class PopRenderState extends MovingBlockRenderState {
    public ClientLevel level;
    /** Off the grid: no neighbour hides a side or shades it, and every side takes the brightest light round the spot. */
    public boolean loose;
    /** The copy turns, so a side against a neighbour can show: it is drawn (1.21.2+ copies cull none) and lit. */
    public boolean allSides;

    public void init(ClientLevel level, BlockPos pos, BlockState state) {
        this.level = level;
        this.loose = false;
        this.allSides = false;
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

    private static int looseLight(ClientLevel level, BlockPos pos, LightLayer layer) {
        int light = level.getBrightness(layer, pos);
        for (Direction d : Direction.values()) light = Math.max(light, level.getBrightness(layer, pos.relative(d)));
        return light;
    }

    // the light a side turned out from under an opaque neighbour gets: the neighbour's own is none
    private boolean uncovered(ClientLevel level, BlockPos at, BlockPos p) {
        return allSides && p.distManhattan(at) == 1 && level.getBlockState(p).canOcclude();
    }

    /** With the pose at the block's corner. */
    public void draw(RenderPass pass) {
        (pass.order == 0 ? pass.collector : pass.collector.order(pass.order)).submitMovingBlock(pass.ps, this, 0);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (pos.equals(this.blockPos)) return this.blockState;
        return loose ? Blocks.AIR.defaultBlockState() : level.getBlockState(pos);
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return loose || uncovered(level, this.blockPos, pos) ? looseLight(level, this.blockPos, layer) : super.getBrightness(layer, pos);
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
    // off the grid: no neighbour hides a side or shades it, and every side takes the brightest light round the spot
    public boolean loose;
    // The copy turns, so a side against a neighbour can show: every side is drawn, shaded and tinted as in place.
    public boolean allSides;

    public void init(ClientLevel level, BlockPos pos, BlockState state) {
        this.level = level;
        this.loose = false;
        this.allSides = false;
        this.pos = pos;
        this.blockState = state;
    }

    public void state(BlockState state) {
        this.blockState = state;
    }

    private static int looseLight(ClientLevel level, BlockPos pos, LightLayer layer) {
        int light = level.getBrightness(layer, pos);
        for (Direction d : Direction.values()) light = Math.max(light, level.getBrightness(layer, pos.relative(d)));
        return light;
    }

    // the light a side turned out from under an opaque neighbour gets: the neighbour's own is none
    private boolean uncovered(ClientLevel level, BlockPos at, BlockPos p) {
        return allSides && p.distManhattan(at) == 1 && level.getBlockState(p).canOcclude();
    }

    // the piston's moving-block path: the real level around the block gives the chunk mesh's own light and shading
    public void draw(RenderPass pass) {
        RANDOM.setSeed(blockState.getSeed(pos));
        RenderType type = layer(blockState, RANDOM);
        if (type == RenderType.translucent()) type = RenderType.translucentMovingBlock();
        RANDOM.setSeed(blockState.getSeed(pos));
        VertexConsumer out = AoConsumer.wrap(pass.buffers.getBuffer(type));
        Minecraft.getInstance().getBlockRenderer().renderBatched(blockState, pos, loose || allSides ? new View() : level, pass.ps, out, !allSides, RANDOM);
        pass.used(type);
    }

    private final class View implements BlockAndTintGetter {
        public BlockState getBlockState(BlockPos p) {
            if (p.equals(pos)) return blockState;
            return loose ? Blocks.AIR.defaultBlockState() : level.getBlockState(p);
        }

        public FluidState getFluidState(BlockPos p) {
            return getBlockState(p).getFluidState();
        }

        public BlockEntity getBlockEntity(BlockPos p) {
            return null;
        }

        public int getBrightness(LightLayer layer, BlockPos p) {
            return loose || uncovered(level, pos, p) ? looseLight(level, pos, layer) : level.getBrightness(layer, p);
        }

        public float getShade(Direction direction, boolean shade) {
            return level.getShade(direction, shade);
        }

        public LevelLightEngine getLightEngine() {
            return level.getLightEngine();
        }

        public int getBlockTint(BlockPos p, ColorResolver color) {
            return level.getBlockTint(p, color);
        }

        public int getHeight() {
            return level.getHeight();
        }

        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }
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
