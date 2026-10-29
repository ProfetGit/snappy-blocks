package io.github.profetgit.snappyblocks.item;

/** Added to ItemEntityRenderState: the landing squash, applied about the item's feet in ItemEntityRenderer.submit. */
public interface ItemFxState {
    float snappy_blocks$sxz();

    float snappy_blocks$sy();

    void snappy_blocks$squash(float sxz, float sy);
}
