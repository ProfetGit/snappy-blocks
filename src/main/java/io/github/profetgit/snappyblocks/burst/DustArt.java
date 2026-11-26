package io.github.profetgit.snappyblocks.burst;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.profetgit.snappyblocks.SnappyBlocks;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
//? if >=1.21.2 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * The dust sprites (dev/art/dust): one strip of square frames per sheet. They are read from the jar and registered as
 * textures here, because Fabric without Fabric API loads no mod resources.
 */
public final class DustArt {
    public enum Sheet {
        POOF("poof", 8), WISP("wisp", 4), RING("ring", 8);

        public final String file;
        public final int frames;

        Sheet(String name, int frames) {
            this.file = "textures/particle/dust_" + name + ".png";
            this.frames = frames;
        }

        /** The frame at {@code u} = age over life. */
        public int frame(double u) {
            return Math.max(0, Math.min(frames - 1, (int) (u * frames)));
        }
    }

    private static final Map<Sheet, Boolean> LOADED = new HashMap<>();

    private DustArt() {}

//? if >=1.21.2 {
    public static Identifier id(Sheet s) {
        return Identifier.parse(SnappyBlocks.ID + ":dust/" + s.name().toLowerCase(java.util.Locale.ROOT));
    }
//?} else {
    /*public static ResourceLocation id(Sheet s) {
        return ResourceLocation.tryParse(SnappyBlocks.ID + ":dust/" + s.name().toLowerCase(java.util.Locale.ROOT));
    }
*///?}

    /** Registers the sheet's texture once; false when it could not be read (no dust is drawn then). */
    static boolean ready(Sheet s) {
        Boolean done = LOADED.get(s);
        if (done != null) return done;
        boolean ok = false;
        try (InputStream in = DustArt.class.getResourceAsStream("/assets/" + SnappyBlocks.ID + "/" + s.file)) {
            if (in == null) throw new IOException("not in the jar");
            NativeImage image = NativeImage.read(in);
//? if >=1.21.5 {
            Minecraft.getInstance().getTextureManager().register(id(s), new DynamicTexture(() -> "Snappy Blocks " + s.file, image));
//?} else {
            /*Minecraft.getInstance().getTextureManager().register(id(s), new DynamicTexture(image));
*///?}
            ok = true;
        } catch (IOException | RuntimeException e) {
            SnappyBlocks.LOG.warn("Snappy Blocks: dust texture {} unreadable ({})", s.file, e.toString());
        }
        LOADED.put(s, ok);
        return ok;
    }
}
