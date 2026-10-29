package io.github.profetgit.snappyblocks.mixin;

import io.github.profetgit.snappyblocks.burst.Bursts;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    /** A new item entity may be a fresh burst's drop (PRD §5.4 claimed drop). */
    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    private void snappy_blocks$added(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        Bursts.entityAdded(packet.getId(), packet.getType(), packet.getX(), packet.getY(), packet.getZ());
    }
}
