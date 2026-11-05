# Changelog

## 0.0.25 — 2026-11-05

- **Embeddium:** blocks now pop into place with Embeddium too (1.21.1 NeoForge and 1.20.1 Forge), and a broken block no longer blinks out before its break animation on 1.21.1.
- **Oculus shaders (1.20.1 Forge):** Snappy Blocks now notices an Oculus shader pack, so growing blocks are lit like placed ones (no lighting pop at the end) and cast their shadow, as with Iris.
- **Fix: modded plants and other cutout blocks** (Forge and NeoForge, 1.21.1 and 1.20.1) no longer show solid planes behind their leaves for a moment when they break, place or pulse. Their model sets the cutout layer itself, and the animation now reads it from there.

## 0.0.24 — 2026-11-04

- **New: Minecraft 26.2** on Fabric, Quilt, NeoForge and Forge. On 26.2 the mining pulse follows your own hits only (the game sends no hit events for other players), and the place pop hands off to the new block when its chunk mesh is rebuilt. Works with Sodium 0.9 and Iris 1.11 on 26.2, including the growing block's shadow under shaders.

## 0.0.23 — 2026-11-04

- **Fix: works with Get It Together, Drops!** (and any server that merges dropped items over a wider area). Items that merge into a stack farther away now slide into it instead of shrinking away.

## 0.0.22 — 2026-11-04

- **New: Minecraft 26.3 on NeoForge and Forge.** Same effects as the Fabric build: place pop, mining pulse, break burst and item effects.

## 0.0.21 — 2026-11-04

Everything since 0.0.15, for all versions:

- **New: Minecraft 1.20.1** on Fabric, Quilt, Forge and NeoForge (NeoForge 47.1 loads the Forge jar).
- **New: the 26.3 build is updated** with every fix below (CurseForge had 0.0.13 for 26.3).
- **Sodium:** blocks now pop into place and pulse under the pickaxe with Sodium installed (Sodium 0.8 on 1.21.1, Sodium 0.5 on 1.20.1). Embeddium and Rubidium: only the place pop stands aside.
- **Shaders (Iris):** no more lighting pop when a block finishes placing, and growing blocks cast their shadow while they grow (26.3, 1.21.1, 1.20.1; tested with Complementary and BSL). The mining pulse no longer flashes the shaded side of a block.
- Fixed: with Sodium or Embeddium, a broken block blinked out for a moment before its break animation.
- Fixed: picked-up items flew off to the side while walking or sprinting (1.21.1).
- Fixed (Forge): the crack overlay now grows with the mining pulse.
- `pulse.shaded` in the `tune` section of `config/snappy_blocks.json` (default 1) turns the pulse bulge off under shaders if you prefer. An older config may still have it at 0 from 0.0.18: set it to 1 for the bulge.

## 0.0.20 — 2026-11-04

- Fixed (1.21.1 with Iris shaders): the lighting no longer pops when a block finishes placing. Shader packs that ask for ambient occlusion in the vertex alpha (Complementary, BSL) got the real block with its soft corner shading, but the growing copy without it, so the sides and bottom looked lighter until the block landed. The copy now carries the same shading. The same applies to the mining pulse and the break burst.

## 0.0.19 — 2026-11-04

- Fixed (1.21.1 with Iris shaders): a block growing in now casts its shadow while it grows. Before, the shadow popped in only once the block had finished placing, because Iris draws its shadow map from the chunk mesh and never saw the growing copy.
- Fixed (shader packs): the mining pulse made the shaded side of a block flash lighter on every hit, because shaders lit the pulsing copy brighter than the real block. Block copies (pulse, place pop, break burst) are now drawn through Iris's terrain shader, the one the chunk mesh uses, so the flash is gone and the bulge stays. `pulse.shaded` in the `tune` section of `config/snappy_blocks.json` (default 1) turns the bulge off under shaders if you prefer.

## 0.0.17 — 2026-11-04

- Added (1.21.1 with Sodium 0.8): blocks now pop into place and pulse under the pickaxe with Sodium installed. Before, Sodium switched both off on 1.21.1. Sodium builds the mod has no hook for (older 1.21.1 Sodium) still switch only the place pop off; the mining pulse, break burst and item effects stay on.
- Embeddium and Rubidium: only the place pop stands aside now; the mining pulse stays.

## 0.0.16 — 2026-11-04

- Fixed (1.21.1 with Sodium or Embeddium): a broken block blinked out for about a tenth of a second and then reappeared to play its break animation. Sodium never told the mod when its new mesh was ready, so the animation waited out a fixed timer; it now follows Sodium's own mesh upload.
- Fixed (1.21.1): while walking or sprinting, picked-up items did not fly into their hotbar slot but swung off to the side and under the screen. The flight now moves with you, and its end follows the walking bob, so it lands on the slot.

## 0.0.15 — 2026-11-03

- Fixed: on NeoForge and Forge 1.21.1 the game crashed at launch next to Sodium ("Modules sodium and snappy_blocks export package net.caffeinemc.mods.sodium.client.render.chunk"). The jar carried Sodium placeholder classes it only needs while compiling; they are gone from every jar now.

## 0.0.14 — 2026-11-03

Snappy Blocks now runs on **Minecraft 1.21.1** too, on Fabric, Quilt, NeoForge and Forge. The Fabric 26.3 build is not part of this release.

- Same place pop, mining pulse, break burst, item landing, Q-throw, merge, despawn and pickup flight as on 26.3.
- Chests and banners pop and burst like other blocks.
- Not on 1.21.1: other players' mining pulses (the server doesn't send their hits there), and a burst for blocks a piston destroys (vanilla sends no event for them).
- Sodium and Iris are not supported on 1.21.1 yet: with Sodium installed, the place pop and the mining pulse stand aside.

## 0.0.13 — 2026-11-02

First public build. Blocks, drops and pickups move instead of blinking in and out. Client-side only: it works on any server.

- **Placing** a block pops it into place with a small overshoot.
- **Mining** pulses the block on every hit; the crack overlay grows with it.
- **Breaking** punches the block, shrinks it away and throws material chips. The drop springs out of the block's centre.
- **Drops** squash and hop when they land, with a soft step sound. Merging stacks slide together, and despawning items shrink out.
- **Q-throw** starts at your hand and grows out of it.
- **Pickups** arc into your chest, or fly into your hotbar slot in first person.
- **Denied** placements and breaks (spawn protection, world border) regrow or shrink back instead of snapping.
- **Config** in `config/snappy_blocks.json`: a switch per effect, intensity from 50 to 150, and **Calm** for reduced motion (smaller movement, no hit-stop, no hop). Every timing is a number in the file and changes while the game runs.
- Plays along with Sodium, Iris shaders, Continuity, Flashback re-shoots and Build Buddy. Mods that already animate the same thing (A Good Place, ItemPhysic Lite, Tiny Pickup Animation, Block Motion) make that effect stand aside.
