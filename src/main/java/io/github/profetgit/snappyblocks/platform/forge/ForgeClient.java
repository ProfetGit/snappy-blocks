package io.github.profetgit.snappyblocks.platform.forge;

//? forge {
/*import io.github.profetgit.snappyblocks.SettingsScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// Client only (ForgeEntry calls it on the client): the settings button in Forge's mod list.
final class ForgeClient {
    private ForgeClient() {
    }

    static void register(FMLJavaModLoadingContext context) {
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent)));
    }
}
*///?}
