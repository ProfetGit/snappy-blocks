package io.github.profetgit.snappyblocks;

import net.minecraft.client.gui.screens.Screen;

/** The one place that decides which settings screen a loader's "config" button opens. */
public final class ConfigScreens {
    private ConfigScreens() {}

    public static Screen create(Screen parent) {
        return io.github.profetgit.profetsettings.ProfetSettings.screen(parent, SnappyBlocks.ID);
    }

    public static boolean isConfigScreen(Screen screen) {
        return io.github.profetgit.profetsettings.ProfetSettings.isScreen(screen);
    }
}
