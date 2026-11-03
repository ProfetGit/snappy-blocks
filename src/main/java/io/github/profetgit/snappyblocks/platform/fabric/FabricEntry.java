package io.github.profetgit.snappyblocks.platform.fabric;

//? fabric {
import io.github.profetgit.snappyblocks.SnappyBlocks;
import io.github.profetgit.snappyblocks.platform.Platform;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric and Quilt (Quilt loads the Fabric jar). */
public final class FabricEntry implements ClientModInitializer, Platform {
    @Override
    public void onInitializeClient() {
        SnappyBlocks.init(this);
    }

    @Override
    public String loader() {
        return FabricLoader.getInstance().isModLoaded("quilt_loader") ? "quilt" : "fabric";
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public List<ModInfo> mods() {
        return FabricLoader.getInstance().getAllMods().stream()
            .map(m -> new ModInfo(m.getMetadata().getId(), m.getMetadata().getName())).toList();
    }
}
//?}
