package io.github.profetgit.snappyblocks.platform.forge;

//? forge {
/*import io.github.profetgit.snappyblocks.SnappyBlocks;
import io.github.profetgit.snappyblocks.platform.Platform;
import java.nio.file.Path;
import java.util.List;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

// Forge 26.x; Forge 1.21.1 and 1.20.1 build their own entry in forge-<mc>/
@Mod(SnappyBlocks.ID)
public final class ForgeEntry implements Platform {
    public ForgeEntry() {
        if (FMLEnvironment.dist.isClient()) SnappyBlocks.init(this);
    }

    @Override
    public String loader() {
        return "forge";
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.isLoaded(modId);
    }

    @Override
    public List<ModInfo> mods() {
        return ModList.getMods().stream().map(m -> new ModInfo(m.getModId(), m.getDisplayName())).toList();
    }
}
*///?}
