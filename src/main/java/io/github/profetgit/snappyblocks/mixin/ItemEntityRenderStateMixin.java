package io.github.profetgit.snappyblocks.mixin;

import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.2 {
import io.github.profetgit.snappyblocks.item.ItemFxState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemFxState {
    @Unique
    private float snappy_blocks$sxz = 1, snappy_blocks$sy = 1;

    @Override
    public float snappy_blocks$sxz() {
        return snappy_blocks$sxz;
    }

    @Override
    public float snappy_blocks$sy() {
        return snappy_blocks$sy;
    }

    @Override
    public void snappy_blocks$squash(float sxz, float sy) {
        snappy_blocks$sxz = sxz;
        snappy_blocks$sy = sy;
    }
}
//?} else {
/*// item entities have no render state before 1.21.2 (ItemEntityRendererMixin): nothing to add
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class ItemEntityRenderStateMixin {
}
*///?}
