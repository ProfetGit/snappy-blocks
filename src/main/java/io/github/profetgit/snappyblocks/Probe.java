package io.github.profetgit.snappyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Counters of what the mod's hooks saw, for the test driver and `-Dsnappy_blocks.trace=true`. M0 only observes; the
 * effects (PRD §5) are built on these hooks from M1.
 */
public final class Probe {
    private static final boolean TRACE = Boolean.getBoolean("snappy_blocks.trace");

    public static int placed, placedPredicted, removed, removedPredicted, destroyEvents, hitEvents;
    public static BlockPos lastPlaced, lastRemoved, lastDestroyEvent, lastHitEvent;

    private Probe() {}

    static void reset() {
        placed = placedPredicted = removed = removedPredicted = destroyEvents = hitEvents = 0;
        lastPlaced = lastRemoved = lastDestroyEvent = lastHitEvent = null;
    }

    /** Air or a bare fluid: what a broken block leaves and what a placement may fill (PRD §5.1, §5.3). */
    public static boolean empty(BlockState s) {
        return s.isAir() || s.getBlock() instanceof LiquidBlock;
    }

    public static void blockChanged(BlockPos pos, BlockState old, BlockState now, boolean predicted) {
        if ((empty(old) || old.canBeReplaced()) && !empty(now) && !now.canBeReplaced()) {
            placed++;
            if (predicted) placedPredicted++;
            lastPlaced = pos.immutable();
            if (TRACE) SnappyBlocks.LOG.info("[probe] placed {} {} predicted={}", pos, now, predicted);
        } else if (!empty(old) && empty(now)) {
            removed++;
            if (predicted) removedPredicted++;
            lastRemoved = pos.immutable();
            if (TRACE) SnappyBlocks.LOG.info("[probe] removed {} {} predicted={}", pos, old, predicted);
        }
    }

    public static void levelEvent(int type, BlockPos pos, int data) {
        if (type == LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK) {
            destroyEvents++;
            lastDestroyEvent = pos.immutable();
            if (TRACE) SnappyBlocks.LOG.info("[probe] 2001 {} data {}", pos, data);
        } else if (TRACE && type == LevelEvent.PARTICLES_DESTROY_PROGRESS) {
            SnappyBlocks.LOG.info("[probe] 2019 {}", pos);
        } else if (type == LevelEvent.PARTICLES_AND_SOUND_DESTROY_PROGRESS) {
            hitEvents++;
            lastHitEvent = pos.immutable();
            if (TRACE) SnappyBlocks.LOG.info("[probe] 2020 {}", pos);
        }
    }

    public static String summary() {
        return String.format("placed %d (%d predicted), removed %d (%d predicted), 2001 x%d, 2020 x%d",
            placed, placedPredicted, removed, removedPredicted, destroyEvents, hitEvents);
    }
}
