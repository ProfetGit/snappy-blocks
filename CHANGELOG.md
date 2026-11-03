# Changelog

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
