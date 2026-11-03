package io.github.profetgit.snappyblocks.item;

import io.github.profetgit.snappyblocks.RenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.item.ItemEntity;
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
//?} else {
/*import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
*///?}

/**
 * What a ghost (an item gone from the level, still drawn: Ghosts) is made of. 26.3 keeps the item's render state;
 * before 1.21.2 there is none, so it keeps a copy of the entity that never joins the level, drawn by the dispatcher.
 */
final class GhostItem {
    /** Where the item stood when captured: its feet, with the effects' offsets (hop, spring) included. */
    final double x, y, z;
    /** Age in ticks (spin and bob) when captured. */
    final float age0;
//? if >=1.21.2 {
    private final ItemEntityRenderState state;

    private GhostItem(ItemEntityRenderState state) {
        this.state = state;
        this.x = state.x;
        this.y = state.y;
        this.z = state.z;
        this.age0 = state.ageInTicks;
    }

    /** A partial pickup ({@code count} below the stack's) flies a split copy. Null when the item can't be drawn. */
    static GhostItem capture(ItemEntity e, int count) {
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        EntityRenderState s = mc.getEntityRenderDispatcher().extractEntity(e, pt);
        if (!(s instanceof ItemEntityRenderState state)) return null;
        ((ItemFxState) state).snappy_blocks$squash(1, 1);
        state.shadowPieces.clear();
        state.shadowRadius = 0;
        state.outlineColor = 0;
        if (count > 0 && count < e.getItem().getCount()) state.count = 1;
        return new GhostItem(state);
    }

    void age(float ticks) {
        state.ageInTicks = ticks;
    }

    /** Height of the item's visual centre above its feet, as ItemEntityRenderer draws it (bob included). */
    double lift() {
        AABB box = state.item.getModelBoundingBox();
        double bob = Mth.sin(state.ageInTicks / 10.0F + state.bobOffset) * 0.1F + 0.1F;
        return bob + 0.0625 + box.getYsize() / 2;
    }

    /** Edge of the item's model in blocks, for sizing it to a hotbar slot. */
    double size() {
        AABB box = state.item.getModelBoundingBox();
        return Math.max(box.getXsize(), box.getYsize());
    }

    /** With the pose at the item's visual centre. */
    void draw(RenderPass pass, double lift, double cx, double cy, double cz) {
        Minecraft.getInstance().getEntityRenderDispatcher().submit(state, pass.camera, 0, -lift, 0, pass.ps, pass.collector);
    }
//?} else {
/*    private static int ids;
    private final ItemEntity copy;
    private float age;

    private GhostItem(ItemEntity copy, ItemPose pose, float age) {
        this.copy = copy;
        this.x = pose.x;
        this.y = pose.y;
        this.z = pose.z;
        this.age0 = this.age = age;
    }

    static GhostItem capture(ItemEntity e, int count) {
        ItemEntity copy = e.copy();
        // a negative id never matches a real entity's in the effects' tables
        copy.setId(--ids);
        if (count > 0 && count < e.getItem().getCount()) copy.getItem().setCount(1);
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        ItemPose pose = new ItemPose();
        pose.x = Mth.lerp(pt, e.xo, e.getX());
        pose.y = Mth.lerp(pt, e.yo, e.getY());
        pose.z = Mth.lerp(pt, e.zo, e.getZ());
        io.github.profetgit.snappyblocks.burst.Bursts.offset(e.getId(), pose);
        ItemFx.extract(e, pose);
        return new GhostItem(copy, pose, e.getAge() + pt);
    }

    void age(float ticks) {
        age = ticks;
    }

    double lift() {
        double bob = Mth.sin(age / 10.0F + copy.bobOffs) * 0.1F + 0.1F;
        return bob + 0.25 * groundScale();
    }

    private float groundScale() {
        return Minecraft.getInstance().getItemRenderer().getModel(copy.getItem(), copy.level(), null, copy.getId())
            .getTransforms().getTransform(ItemDisplayContext.GROUND).scale.y();
    }

    double size() {
        return Math.max(0.1, groundScale());
    }

    void draw(RenderPass pass, double lift, double cx, double cy, double cz) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        boolean hitBoxes = dispatcher.shouldRenderHitBoxes();
        dispatcher.setRenderShadow(false);
        dispatcher.setRenderHitBoxes(false);
        try {
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(cx, cy, cz));
            dispatcher.render(copy, 0, -lift, 0, 0f, age - copy.getAge(), pass.ps, pass.buffers, light);
        } finally {
            dispatcher.setRenderShadow(true);
            dispatcher.setRenderHitBoxes(hitBoxes);
        }
    }
*///?}
}
