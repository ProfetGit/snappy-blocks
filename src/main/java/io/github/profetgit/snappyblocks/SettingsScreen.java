package io.github.profetgit.snappyblocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * The settings button in Mod Menu / the loader's mod list (PRD §5.6): the top-level keys of the config file only; the
 * feel numbers stay in the file's "tune" object. Changes apply at once and are written when the screen closes. Labels
 * are plain English: without Fabric API a Fabric mod's language files aren't loaded.
 */
public final class SettingsScreen extends OptionsSubScreen {
    public SettingsScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.literal("Snappy Blocks"));
    }

//? if >=1.21 {
    @Override
    protected void addOptions() {
        list.addSmall(options());
    }
//?} else {
/*    private net.minecraft.client.gui.components.OptionsList list;

    @Override
    protected void init() {
        list = new net.minecraft.client.gui.components.OptionsList(minecraft, width, height, 32, height - 32, 25);
        list.addSmall(options());
        addWidget(list);
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.CommonComponents.GUI_DONE,
            b -> minecraft.setScreen(lastScreen)).bounds(width / 2 - 100, height - 27, 200, 20).build());
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        list.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partial);
    }
*///?}

    static OptionInstance<?>[] options() {
        Config c = Config.get();
        return new OptionInstance<?>[] {
            OptionInstance.createBoolean("Snappy Blocks", OptionInstance.cachedConstantTooltip(Component.literal("Off: every block and item behaves like vanilla.")),
                c.enabled, v -> c.enabled = v),
            new OptionInstance<>("Intensity", OptionInstance.cachedConstantTooltip(Component.literal("How big every movement is.")),
                (caption, v) -> Component.literal("Intensity: " + v * 10 + "%"), new OptionInstance.IntRange(5, 15),
                Math.max(5, Math.min(15, Math.round(c.intensity / 10f))), v -> c.intensity = v * 10),
            OptionInstance.createBoolean("Reduced motion",OptionInstance.cachedConstantTooltip(Component.literal("Reduced motion: smaller movements, no hit-stop, no hop.")),
                c.calm, v -> c.calm = v),
            OptionInstance.createBoolean("Place pop", OptionInstance.cachedConstantTooltip(Component.literal("Placed blocks grow into place with a little overshoot.")),
                c.pop, v -> c.pop = v),
            OptionInstance.createBoolean("Mining pulse", OptionInstance.cachedConstantTooltip(Component.literal("The block bulges with every mining hit.")),
                c.pulse, v -> c.pulse = v),
            OptionInstance.createBoolean("Break burst", OptionInstance.cachedConstantTooltip(Component.literal("A broken block squashes and shrinks into its drop, with chips.")),
                c.burst, v -> c.burst = v),
            OptionInstance.createBoolean("Item drops", OptionInstance.cachedConstantTooltip(Component.literal("Drops spring out, items land with a hop, thrown stacks leave your hand.")),
                c.items, v -> c.items = v),
            OptionInstance.createBoolean("Pickup flight", OptionInstance.cachedConstantTooltip(Component.literal("Picked-up items fly into their hotbar slot.")),
                c.pickup, v -> c.pickup = v)};
    }

    @Override
    public void removed() {
        Config.save();
        super.removed();
    }
}
