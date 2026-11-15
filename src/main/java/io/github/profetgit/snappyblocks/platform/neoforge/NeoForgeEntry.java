package io.github.profetgit.snappyblocks.platform.neoforge;

//? neoforge {
/*import io.github.profetgit.snappyblocks.SnappyBlocks;
import io.github.profetgit.snappyblocks.platform.Platform;
import java.nio.file.Path;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import io.github.profetgit.snappyblocks.SettingsScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = SnappyBlocks.ID, dist = Dist.CLIENT)
public final class NeoForgeEntry implements Platform {
    public NeoForgeEntry(ModContainer container) {
        SnappyBlocks.init(this);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
    }

    @Override
    public String loader() {
        return "neoforge";
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public List<ModInfo> mods() {
        return ModList.get().getMods().stream().map(m -> new ModInfo(m.getModId(), m.getDisplayName())).toList();
    }
}
*///?}
