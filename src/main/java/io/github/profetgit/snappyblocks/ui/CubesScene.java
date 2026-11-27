package io.github.profetgit.snappyblocks.ui;

import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.Tune;
import io.github.profetgit.snappyblocks.burst.Bursts;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

// 3D block particles: the axe takes three hits at an oak log, it breaks, and its bits tumble out and settle, as cubes
// or flat. The bits show whatever the Snappy chips switch says, since the setting also covers vanilla's own crumbs.
final class CubesScene extends SnappyScene {
    private final Tool tool = Tool.axe();
    private static final float PERIOD = 3200, EVERY = 200, BREAK = 950, HIT1 = BREAK - 3 * EVERY, RETURN = 2900;
    private static final int HITS = 3;

    @Override
    public void render(Gfx g, Ctx c, int x, int y, int w, int h) {
        float time = time(c, PERIOD);
        Identifier bark = tex("oak_log");
        Diorama.Look look = new Diorama.Look(tex("oak_log_top"), bark, bark);
        int ox = x + (w - Diorama.W) / 2;
        float cx = ox + Diorama.AX, floor = y + Diorama.AY;
        Config k = Config.get();
        String off = off("cubes", k.cubes, false);
        Diorama.backdrop(g, ox, y);
        double[] whole = {1, 1, 1};
        boolean burst = k.enabled && k.burst, back = time >= RETURN;
        float t = (time - BREAK) / MS, gone = (RETURN - BREAK) / MS;
        int bits = (int) Math.round(Tune.get("burst.bits"));
        boolean flying = !back && t >= 0;
        if (flying) burstChips(g, cx, floor, bark, bits, t, gone, false);
        double[] shape = burst && t >= 0 && t < Bursts.burstTicks() ? Bursts.shape(false, t) : null;
        if (back) {
            Diorama.curtain(g, x, y, w, h, 0);
            Diorama.block(g, cx, floor, look, whole, -1);
        } else if (t < 0) {
            int hit = time < HIT1 ? -1 : Math.min(HITS - 1, (int) ((time - HIT1) / EVERY));
            Diorama.block(g, cx, floor, look, whole, hit < 0 ? -1 : 2 + hit * 3);
        } else if (shape != null) {
            Diorama.block(g, cx, floor, look, shape, -1);
        }
        tool.draw(g, ox, y, swing(time, HIT1, BREAK, true));
        if (flying) burstChips(g, cx, floor, bark, bits, t, gone, true);
        if (off != null) Diorama.note(g, x, y, w, h, off);
        key(g, c, Minecraft.getInstance().options.keyAttack, x + w / 2f, y + 120, time >= HIT1 && time < BREAK + 60);
    }
}
