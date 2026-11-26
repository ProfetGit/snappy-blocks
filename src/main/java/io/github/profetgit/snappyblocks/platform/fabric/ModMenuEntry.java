package io.github.profetgit.snappyblocks.platform.fabric;

//? fabric {
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.profetgit.snappyblocks.ConfigScreens;

/** Mod Menu's settings button (only loaded when Mod Menu is installed). */
public final class ModMenuEntry implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreens::create;
    }
}
//?}
