package io.github.profetgit.snappyblocks.item;

/** Where an item entity is drawn this frame (world position of its feet) and its landing squash; the effects edit it. */
public final class ItemPose {
    /** One reused instance: the render thread draws one item at a time. */
    public static final ItemPose SHARED = new ItemPose();

    public double x, y, z;
    public float sxz = 1, sy = 1;

    public void squash(float sxz, float sy) {
        this.sxz = sxz;
        this.sy = sy;
    }
}
