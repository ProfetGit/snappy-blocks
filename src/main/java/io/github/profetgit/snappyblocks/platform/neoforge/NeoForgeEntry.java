package io.github.profetgit.snappyblocks.platform.neoforge;

//? neoforge {
/*import io.github.profetgit.snappyblocks.SnappyBlocks;
import io.github.profetgit.snappyblocks.platform.Platform;
import java.nio.file.Path;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

@Mod(value = SnappyBlocks.ID, dist = Dist.CLIENT)
public final class NeoForgeEntry implements Platform {
    public NeoForgeEntry() {
        SnappyBlocks.init(this);
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
