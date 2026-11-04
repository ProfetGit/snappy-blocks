package io.github.profetgit.snappyblocks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.item.Ghosts;
import io.github.profetgit.snappyblocks.item.ItemFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Shadow
    private ClientLevel level;

    @Unique
    private boolean snappy_blocks$slotWasEmpty;

    /** A new item entity may be a fresh burst's drop (PRD §5.4 claimed drop), or the one our Q press threw. */
    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    private void snappy_blocks$added(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        Bursts.entityAdded(packet.getId(), packet.getType(), packet.getX(), packet.getY(), packet.getZ());
        if (level != null && level.getEntity(packet.getId()) instanceof ItemEntity item) ItemFx.added(item);
    }

    /** Our pickup arc replaces vanilla's straight 3-tick particle (PRD §5.5). */
    @WrapOperation(method = "handleTakeItemEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;add(Lnet/minecraft/client/particle/Particle;)V"))
    private void snappy_blocks$take(ParticleEngine engine, Particle particle, Operation<Void> original, @Local(argsOnly = true) ClientboundTakeItemEntityPacket packet) {
        Entity from = level.getEntity(packet.getItemId());
        Entity to = level.getEntity(packet.getPlayerId());
        if (!(to instanceof LivingEntity picker) || !Ghosts.take(from, picker, packet.getAmount())) original.call(engine, particle);
    }

    /** Items removed without a take packet: drawn on until we know whether they merged or despawned. */
    @Inject(method = "handleRemoveEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void snappy_blocks$removed(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        if (level == null) return;
        packet.entityIds().forEach(id -> {
            if (level.getEntity(id) instanceof ItemEntity item) {
                if (!Bursts.hidden(item)) Ghosts.removed(item);
                ItemFx.forget(id);
            }
        });
    }

    @Inject(method = "handleSetEntityData", at = @At("TAIL"))
    private void snappy_blocks$data(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        if (level != null && level.getEntity(packet.id()) instanceof ItemEntity item) {
            int growth = ItemFx.stackChanged(item);
            if (growth > 0) Ghosts.grew(item, growth);
        }
    }

    @Inject(method = "handleContainerSetSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void snappy_blocks$slotBefore(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        int slot = packet.getSlot();
        snappy_blocks$slotWasEmpty = mc.player != null && packet.getContainerId() == 0 && slot >= 0 && slot < mc.player.inventoryMenu.slots.size()
            && mc.player.inventoryMenu.getSlot(slot).getItem().isEmpty();
    }

    /** The server says where a pickup went: its flight targets that slot, and the slot bumps when it arrives. */
    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void snappy_blocks$slot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        int slot = packet.getSlot();
        if (mc.player == null || packet.getContainerId() != 0 || slot < 0 || slot >= mc.player.inventoryMenu.slots.size()) return;
        Ghosts.slotSet(slot, mc.player.inventoryMenu.getSlot(slot).getItem(), snappy_blocks$slotWasEmpty);
    }
}
