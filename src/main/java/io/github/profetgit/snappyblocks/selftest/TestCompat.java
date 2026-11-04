package io.github.profetgit.snappyblocks.selftest;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
//? if >=1.21.2 {
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
//?} else {
/*import io.github.profetgit.snappyblocks.item.ItemFx;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
*///?}

/** What the test driver does differently across Minecraft versions. */
final class TestCompat {
    private TestCompat() {}

    static void createWorld(Minecraft mc, String name) {
//? if >=1.21.2 {
        mc.createWorldOpenFlows().createFreshLevel(name,
            new LevelSettings(name, GameType.SURVIVAL, new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT),
            new WorldOptions(42L, false, false), WorldPresets::createTestWorldDimensions, mc.gui.screen());
//?} else {
/*        mc.createWorldOpenFlows().createFreshLevel(name,
            new LevelSettings(name, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
            new WorldOptions(42L, false, false),
            access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.gui.screen());
*///?}
    }

    static void screenshot(RenderTarget target, Consumer<NativeImage> then) {
//? if >=1.21.2 {
        Screenshot.takeScreenshot(target, then);
//?} else {
/*        then.accept(Screenshot.takeScreenshot(target));
*///?}
    }

    static void selectSlot(Player player, int slot) {
//? if >=1.21.2 {
        player.getInventory().setSelectedSlot(slot);
//?} else {
/*        player.getInventory().selected = slot;
        Minecraft.getInstance().getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(slot));
*///?}
    }

    /** The drop key's effect (before 1.21.2 the key calls LocalPlayer.drop itself, MinecraftMixin). */
    static void drop(Minecraft mc) {
//? if >=26.3 {
        mc.gameMode.dropItem(mc.player, false);
//?} else {
/*        if (!mc.player.getInventory().getSelectedItem().isEmpty()) io.github.profetgit.snappyblocks.item.ItemFx.dropPressed(mc);
        mc.player.drop(false);
*///?}
    }

    static void toggleHud(Minecraft mc) {
//? if >=26.2 {
        mc.gui.hud.toggle();
//?} else {
/*        mc.options.hideGui = !mc.options.hideGui;
*///?}
    }

    static boolean hudHidden(Minecraft mc) {
//? if >=26.2 {
        return mc.gui.hud.isHidden();
//?} else {
/*        return mc.options.hideGui;
*///?}
    }

    static String versionName() {
//? if >=1.21.6 {
        return net.minecraft.SharedConstants.getCurrentVersion().name();
//?} else {
/*        return net.minecraft.SharedConstants.getCurrentVersion().getName();
*///?}
    }
}
