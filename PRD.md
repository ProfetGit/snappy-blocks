# Snappy Blocks — PRD

Status: draft (2026-10-29). Name final: Snappy Blocks. Mod id `snappy_blocks`, package `io.github.profetgit.snappyblocks`. Tracker:
`STATUS.md`. Draft: `docs/draft-v1.md`.

## 1. Summary
Promise: nothing in Minecraft blinks in or out anymore. Blocks pop into place, burst into their drops, and the drops
bounce, settle and fly to your hotbar.

The moment (a muted 5-second clip):
- A pickaxe hits diamond ore three times, and the ore pulses with each hit sound.
- The ore cracks open, the shell bursts into mini blocks and the diamond pops out.
- The diamond bounces once, then zips into its hotbar slot, which bumps.

Edge: competitors each animate one action. A Good Place (2.81M downloads) animates placement only; ItemPhysic Lite
(3.54M) lays items flat; Pickup Animations (48K) animates pickups. No mod links the chain from block to pieces to item
to hotbar, and no Java mod bursts drops out of a broken block.

Platform: Minecraft 26.3, Fabric only, client only. It works on any server.

## 2. Changes from the draft
- Defect: the draft had no edge, and three mods already animate placement. Fix: the edge is the unbroken chain from
  block to item to hotbar (§5), not one more pop curve.
- Defect: "item drop" was ambiguous: block drops, thrown items, mob loot, chest contents. Fix: every item entity gets
  the landing, merge, removal and pickup effects. Only block breaks burst (§5.3). Mob deaths stay Overreacting Mobs'
  job.
- Defect: level event 2001 ("block destroyed") does not mean a player broke a block. It also fires when a sheep turns
  grass into dirt, when golems and withers are built, for pistons and for `/setblock destroy`. Fix: a break is 2001
  plus the spot becoming air or fluid (§5.3).
- Defect: shrinking a placed block below full size means hiding the real block from the chunk mesh. Nothing in this
  workspace has done that, and the face under the block is culled too, so hiding leaves a hole. Fix: the hide path is
  the M1 spike, and the fallback is a bulge that needs no hiding (§12).
- Defect: the draft's timing assumed the client plays each mining hit. It doesn't: the client plays only the first
  hit. The server then sends level event 2020 every 4 ticks to everyone, the miner included. Fix: the pulse follows
  2020 and lags by one round trip (§5.2).
- Defect: on a dedicated server the client has no loot tables, and drops arrive at least one round trip after the
  break. Fix: the break starts with a crack that doesn't need the drops; the burst look is chosen when the first
  item arrives (§5.3).

## 3. Decisions
| Topic | Choice | Date |
|---|---|---|
| Variant | "Nothing teleports": continuity between block, pieces, item and hotbar | 2026-10-29 |
| Placement | Pop at the spot; flying from the hand moves to the roadmap | 2026-10-29 |
| Drops | All item entities; burst only for block breaks | 2026-10-29 |
| Mining | A hit pulse on each swing, a hit-stop on the break | 2026-10-29 |
| Name | Snappy Blocks, id `snappy_blocks`, slug `snappy-blocks` | 2026-10-29 |

My calls, open to veto:
- Client only, Fabric 26.3 only for 1.0 (workspace default).
- No Fabric API dependency, like Seamless Displays and Having a Blast. Optional Mod Menu entry.
- Vanilla sounds only, plus one soft landing sound made from vanilla step sounds.
- No camera kick or shake. The hit-stop freezes only the pieces, never the view.
- Breaks not caused by a player (pistons, `/setblock destroy`) burst too, within the same budget.
- The pickup count updates at once. The slot bump plays when the flying item arrives.
- When a mod that does the same job is installed, we stand aside for that one feature.

## 4. Pillars
1. **Never late.** The world state is never delayed: collision, inventory and block updates stay vanilla.
   - Every effect starts on the frame that handles the client's `setBlock` or level event.
   - The probe logs that frame and the frame the mesh is uploaded.
2. **No flicker.** Scene `actions100` (100 mixed actions at 60 fps) shows 0 one-frame pops in `wb img flicker`.
   - Run with Fabric API (Indigo), Sodium, and Iris + Complementary.
   - The threshold is set in M1 and written here.
   - Under Iris, the hand-off frame also passes a pixel diff.
3. **Cheap.**
   - Mean ≤ 0.25 ms per frame in `actions100`; worst case ≤ 2 ms in a 64-block Veinminer chain.
   - ≤ 2 extra section rebuilds per placement; p95 mesh upload ≤ 3 frames.
   - No allocations per frame once warm.
   - (guess, measured in M1 and M4)
4. **Nothing hidden for good.** A hidden item shows within 6 ticks and a hidden block is remeshed within 20 ticks,
   both timed by frames.

## 5. Features (1.0)
Clock and lifecycle:
- The clock is our own client tick counter plus the partial tick, evaluated every frame. It stops when the game is
  paused and under `/tick freeze`. It never uses the level's game time (time packets make it jump) or the wall clock.
- Watchdogs run on frame time.
- All state is cleared on a level change, a respawn or a dimension change, and per chunk when that chunk unloads.
- Every timing below is a guess (tune at feel checks 1 and 2). Each is a config value and a dev command.

### 5.1 Place pop
Triggers:
- The local player's predicted placement.
- Another player's placement: a block update and a place sound at the same spot within 2 ticks. The spot held air, a
  fluid or a replaceable block (grass, a snow layer). Only within 16 blocks.
- That second rule also covers Flashback replays.

Look:
- The block grows 70% → 104% → 100% in 5 ticks with one settle.
- The pivot is the centre of the face it was placed against: a torch grows from its wall, a carpet from the floor.
- The selection outline scales with the block while it pops.

Hide path:
- While the block animates, it is left out of its section's mesh. Its neighbours are meshed as if it were air, so the
  face under it shows (no hole).
- The animated copy stays drawn until the section holding the real block has been uploaded: at least 2 ticks, as in
  Having a Blast's hand-off.
- Under Iris, the copy is also drawn in the shadow pass.
- Watchdog: a forced remesh after 20 ticks.

Blocks that can't take the hide path:
- The bulge is the fallback: a copy at ≥ 1.002 scale with a polygon offset, drawn only within 24 blocks.
- Translucent blocks (glass, ice, slime, honey) take the hide path or no pop at all: a copy laid over them doubles
  their alpha.
- Blocks drawn by a block-entity renderer (chest, bed, sign, banner, skull, shulker box) bulge through a scaled
  renderer pose.
- Meshed blocks that have a block entity (furnace, barrel, hopper) take the normal path. The render path decides,
  not whether there is a block entity.
- Both halves of a door or bed animate together.
- In-place changes only bulge: slab to double slab, candles, sea pickles, snow layers.

Edge cases:
- The server denies the placement (spawn protection, a claim). We see it in `ClientLevel.syncBlockState` for a
  predicted spot: the copy shrinks out in 3 ticks.
- In a section where a client `setBlock` burst happened without prediction (another mod, Continuity), hide is off.
- Continuity's connected textures show unconnected for up to 5 ticks. Accepted.
- Pops overlap freely. Bridging at 10 blocks per second never queues.

### 5.2 Mining pulse
- Follows level event 2020: the first hit is local, every later one comes from the server every 4 ticks. Remote
  miners pulse the same way.
- Look: an outward pulse to 103% for 2 ticks (the bulge path), and 1-2 chips cut from the block's own texture
  (solid texels).
- The crack overlay follows the pulse.
- Instant breaks (creative mode, insta-mining) skip the pulse.
- If mining is cancelled, the block springs back in 3 ticks.
- Level event 1019 (a zombie bashing a door) does nothing in 1.0.

### 5.3 Break burst
What counts as a break:
- Level event 2001, plus the spot turning to air or fluid within 1 tick.
- When 2001 carries air as its data (a door a zombie broke), the client's previous state is the broken block.
- 2001 with a change to any other state (grass to dirt) does nothing.

Frame 0, crack and hold:
- The block splits into 4-8 mini copies of itself, at 0.3-0.5 scale, using Having a Blast's ballistic debris.
- The pieces hold for 1 tick in the break pose (the hit-stop, about 50 ms), then fly for 8-14 ticks and crumble.
- Vanilla's crumb particles stay.

Choosing the look: within the measured round trip + 4 ticks, the first item entity of age 0 within 0.6 blocks of the
centre decides it.
- The drop is the block's own item (dirt, silk touch): the pieces pull together into the item.
- The drop is a different item (stone to cobblestone, ore to gem): the item pops out of the centre of the burst.
- No item arrives in time (creative, glass, `block_drops` off): pieces only.
- Until the item arrives, the guess is made from the held tool (silk touch) and the block's `asItem()`.

Many drops:
- Fortune and multiple drops fan out.
- Containers fan out 8 stacks at most; the rest pop out plainly.
- A break claims only as many items as it should drop.

Materials, chosen by the block's sound type:
- Glass breaks into flat shards.
- Leaves flutter.
- Sand and gravel crumble sooner.
- Wool flies soft, with a short arc.

Edge cases:
- Denied break: seen in `syncBlockState`. The pieces rewind into the block over 4 ticks, and no drop is claimed.
- Budget per tick: full, lite or none, across all bursts. It covers `/fill … destroy`, vein chains and piston
  arrays. Lite means crumbs and the pull-together only.
- Distance: full effect up to 32 blocks, lite up to 64, nothing beyond.
- With the particle setting at Minimal: 2 pieces; at Decreased: half.

### 5.4 Item life
These are offsets on the render only; the entity's position is never changed.

- **Claimed drop:** hidden until its burst slot pops, 6 ticks at most. The server's pickup delay is 10 ticks, so
  nobody picks up an item they never saw. Its offset springs to the true position in 6 ticks.
- **Thrown with Q:** starts at the hand, but only if the entity is ≤ 1 tick old when it arrives (low ping).
  Otherwise it is vanilla. Creative-inventory drops are vanilla.
- **Landing:**
  - The first landing after ≥ 6 ticks in the air squashes the item (height 0.8, width 1.1, for 2 ticks) and hops it
    0.15 blocks.
  - At most once per 20 ticks per item.
  - Never in water, on hoppers, in item streams, or where more than 16 items share a block.
  - Sound: the step sound of the block it lands on, at 0.15 volume and pitch 1.6, at most 4 per tick.
- **Merge:** an item that disappears within 1.5 blocks of a stack of the same item that grew that tick slides into
  it over 3 ticks.
- **Removed without a take packet** (despawn, lava, cactus):
  - It shrinks out over 3 ticks where it was.
  - Only within the tracking range minus 8 blocks.
  - Never on a tick when the level changed or the player teleported.
- **Scale:** effects run on the 64 nearest items only, from pooled objects. `/kill @e[type=item]` is capped the
  same way.

### 5.5 Pickup flight
- A world arc to the picker's chest replaces vanilla's straight pickup particle. Other players' pickups arc too.
- Local player, first person, HUD shown, no screen open:
  - At the near plane the arc hands over to a GUI item, which flies to its slot in 4 ticks.
  - The slot is read from the next inventory change (≤ 2 ticks). If none comes, only the world arc plays.
- A partial pickup flies a split copy.
- The count updates at once (honest); the bump plays when the item arrives. The number may lead the flight by up to
  4 ticks.
- Third person, F1, or an open screen: the world arc only. Spectators: nothing. XP orbs: vanilla.

### 5.6 Config
- A JSON file in `config/`, re-read when it changes.
- A Mod Menu screen when Mod Menu is installed.
- A toggle per feature (5.1-5.5) and an intensity from 50 to 150%.
- **Calm**: amplitudes ×0.3, no hit-stop, no hop (reduced motion).
- Dev commands (client): `/snappyblocks tune <key> <value>`, so feel notes become numbers without a rebuild.

## 6. Controls
| Input | Context | Action |
|---|---|---|
| none | — | Everything follows vanilla actions; the mod adds no keys |

Free letters in the profile, if one is ever needed: t, u, 0.

## 7. Multiplayer and security
- Client only. Nothing is sent; the server needs nothing and can't tell the mod is installed.
- Other players' placements, mining, breaks and pickups animate from vanilla packets (§5.1-§5.5).
- It gives no gameplay information that vanilla doesn't already show. Hidden items reveal before anyone could pick
  them up (§5.4).
- Players without the mod see vanilla.

## 8. Data and persistence
- Only the config file is saved. Nothing goes into worlds.
- Removing the mod leaves nothing behind.
- When the config format changes, unknown keys are kept and missing keys take their defaults.

## 9. Compatibility
| Mod | Expected | Checked by | Milestone |
|---|---|---|---|
| Sodium 0.9.2 | Hide path through Sodium's chunk builder, hooks checked by descriptor | `actions100` with `PROFILE=1` | M1 |
| Fabric API alone (Indigo) | Debris drawn through the parts fallback (a Having a Blast lesson) | `actions100` with `MODS=` fabric-api | M1 |
| Iris + Complementary | Copy drawn in the shadow pass, no pop at hand-off | Pixel diff of the hand-off frame | M1 |
| Continuity | Unconnected for ≤ 5 ticks; no hide after bursts | Glass-pane scene | M4 |
| EntityCulling | Copies are not culled | `actions100` in the profile | M4 |
| Punchy | Untouched; the pulse follows 2020, not the swing | Profile run | M4 |
| Veinminer | A 64-block chain stays within budget | Scene `vein64`, frame ms | M4 |
| Timber, Seamless Displays | No 2001, so no burst | Timber fell scene | M4 |
| Having a Blast | Its hidden drops are left alone; explosions aren't ours | Creeper scene with both | M4 |
| Harvest Sweep | Crop breaks (`#crops`) are left to it | Sweep scene with both | M4 |
| Build Buddy | Auto-place pops stay within budget | 200-block auto-place | M4 |
| Flashback | A re-shoot shows the effects | One replay re-shoot | M4 |
| A Good Place, Stupid Block Animations, fresh block animations, Block Motion | Our placement and mining stand aside | Class check, logged once | M4 |
| ItemPhysic Lite | Our landing stands aside | Class check | M4 |
| Pickup Animations, Tiny Pickup Animation | Our pickup stands aside | Class check | M4 |

## 10. Performance
- Budgets as in pillar 3.
- Caps: 256 live pieces, 64 animated items, a burst budget per tick, distance LOD (§5.3).
- Scenes:
  - `actions100`: normal play.
  - `vein64`: worst case.
  - `farm1000`: 1,000 items on hoppers.
  - `kill_items`: `/kill` of 1,000 items.
- Numbers come from the probe's per-frame ms and section rebuild count. Spark only to confirm.

## 11. Technical design
Templates:
- The build comes from `mods/SeamlessDisplays` (client-only Fabric 26.3, no Fabric API).
- The mixin plugin with descriptor checks comes from
  `mods/BeltLantern/src/main/java/io/github/profetgit/travelerslantern/mixin/MixinPlugin.java`.
- Debris, drop hiding and hand-off patterns are copied from
  `mods/HavingABlast/src/main/java/io/github/profetgit/havingablast/client/` (`Debris`, `DebrisRenderer`, `Drops`,
  `Blasts`). They are copied, not shared.
- The Mod Menu entry follows Having a Blast's `ModMenuEntry`.

Packages: `pop` (5.1), `pulse` (5.2), `burst` (5.3), `items` (5.4), `pickup` (5.5), `render` (copies and pieces, the
Sodium path), `mesh` (the hide set and remesh), `dev` (probe, scenes, tune command).

Vanilla paths, checked in `wb src` 26.3 on 2026-10-29:
- `MultiPlayerGameMode.startPrediction`: predicted placement and break.
- `ClientLevel.syncBlockState` and `endPredictionsUpTo`: acks and denials.
- `ServerPlayerGameMode.tick`: level events 2020/2019 every 4 ticks, source null.
- `LevelEventHandler` 2001 → `ClientLevel.addDestroyBlockEffect`.
- `BreakDoorGoal`: `removeBlock` and then 2001 carrying air.
- `ItemPickupParticle`.
- `SectionCompiler`: the vanilla mesh loop to hook.

Mixins:
- The section compile: skip hidden positions and treat them as air for culling (vanilla and Sodium).
- `LevelEventHandler` for 2001 and 2020.
- `syncBlockState` for denials.
- The item entity render-state extraction, for the offsets.
- The pickup particle.
- The hotbar slot draw, for the bump.

## 12. Risks and spikes
| Risk | Spike | Milestone | Fallback |
|---|---|---|---|
| Hiding a block from the mesh flickers, leaves holes or costs too much on Sodium/Iris | Hide path on 3 renderers, 50 placements each | M1 | Bulge-only look (no shrink) |
| At 200 ms ping, drops can't come out of the burst without a visible pop | Dedicated server with 200 ms simulated latency | M1 | Pieces only, and drops spring from the centre |
| Iris draws the copy with different materials (no waving, no emissive ores) | Hand-off pixel diff | M1 | Shorter hand-off; emissive blocks bulge only |
| The HUD hand-off pops at the near plane | Screen-space handover test | M3 | World arc only |
| Remote placement pairing misfires | Two clients, mixed placing | M2 | Remote pops off by default |

## 13. Milestones
Each milestone: build green, version bump, installed in the Fabric 26.3 profile, committed.

| | Milestone | Exit check |
|---|---|---|
| M0 | Scaffold | Builds; `mt.py SnappyBlocks quick` boots; the mixin audit is clean; the `mp.py` harness starts a server and 2 clients |
| M1 | Spike + core loop (5.1 hide path, 5.3 crack, 5.4 claimed drop) | Flicker 0 over 50 placements on each of Indigo, Sodium and Iris; no ground hole; Iris hand-off diff passes; ≤ 0.25 ms per frame; drops burst out at 200 ms ping. **Go:** all pass. **Switch:** hide flickers or costs > 1 ms on Sodium → bulge look. **Stop:** drops pop at 200 ms → rethink 5.3. |
| M2 | Breaking and mining complete (5.2, rest of 5.3) | Scenes `drops` (silk, fortune, chest, glass, creative) and `deny` (rewinds) pass; on `mp.py` a watcher sees pulses and bursts; 2001 cases (sheep, golem, piston) assert |
| M3 | Item life + pickup (5.4, 5.5) | Scenes `landing`, `merge`, `despawn`, `farm1000` (cap holds) and `pickup` (slot bump on the right slot) pass; HUD hand-off has flicker 0 or is switched off |
| M4 | Compatibility and scale | The §9 matrix is green; `vein64` ≤ 2 ms; `/tick freeze` and pause leave nothing hidden; dimension change clears state; Flashback re-shoot shows the effects |
| M5 | Config + polish | Calm mode and tunables work; `polish` pass; `mt.py release` green |
| M6 | Release kit | `modrinth_check_slug` and a CurseForge search pass for the final name; `wb img flicker` clean on every WebP clip; the user's verdict on icon, banner and clips; the user's OK before any upload |

Feel check 1 (end of M1, ~3 minutes, Fabric 26.3 profile, a fresh survival world):
1. Place 20 blocks of mixed kinds: dirt, planks, a torch, glass, a chest.
2. Bridge out 10 blocks as fast as you can: does the pop ever feel slow?
3. Mine dirt, stone and an ore: do the pieces and the drop read as one motion?
4. Break glass and a leaf block.
Judge: the size of the pop, the hold before the pieces fly, where the drop comes from.
Tunables: `/snappyblocks tune pop.start|pop.ticks|burst.hold|burst.pieces <value>`.

Feel check 2 (end of M3, ~3 minutes, sound on):
1. Mine 10 coal ore and walk over the drops.
2. Throw 5 items with Q onto grass, stone and wool.
3. Leave a pile to merge; break a full chest.
Judge: the landing hop and its sound, the flight to the hotbar, the chest fan.
Tunables: `/snappyblocks tune land.hop|land.volume|pickup.ticks <value>`.

Parallel work: in M2, 5.2 (pulse) and the rest of 5.3 own different packages and can go to two builders. M1 stays
with the main session.

## 14. Release
- The release checklist in the mod-design platform notes: name, `polish`, icon and banner, 60 fps framed WebP clips,
  a Description Kit README, Ko-fi and BisectHosting banners, AI disclosure.
- GitHub repo and store drafts only after the user's OK.
- Clips: ore crack-open, a bridging run, a chest break fan, a pickup into the hotbar.
- Video idea: the moment in §1 as a Short, vanilla against the mod side by side.

## 15. Roadmap
- 1.1: placement flying from the hand (toggle); an inward mining dent once the hide path is proven; zombies' door
  pulse (1019).
- 1.2: Snappy Pistons folded in as a module (needs the user's OK); a small claim API for the author's mods.
- Later: XP orb flight, effects when entities break, ports to 26.2 and NeoForge.

## 16. Cut
- Camera kick and screen shake: the user's taste (micro-stutters).
- Custom break sounds: vanilla sounds first.
- A resource-pack animation language: that is A Good Place's angle, and it would be a wall of config.

## 17. Prior art
Checked 2026-10-29. Modrinth numbers are from the API. CurseForge numbers come from the web (no Core API key).

| Mod | Downloads | Versions, loaders | Does | Gap |
|---|---|---|---|---|
| A Good Place | 2.81M | 1.20-26.3, Fabric/Forge/NeoForge | Placement, curves in resource-pack JSON | Placement only |
| Stupid Block Animations | 101K | 1.20.1-26.3, Fabric | Placement tilt, mining wobble | No breaks, no drops |
| fresh block animations | 42K | 26.1.2-26.3, Fabric | Placement pop/slide/fade, small interactions | Placement-centric |
| Block Motion | 4K | 26.2-26.3, Fabric | Springy placement, mining, shrinks on break | No drops |
| ItemPhysic Lite | 3.54M | 1.18-26.3 | Items lie flat | No motion story |
| Pickup Animations | 48K | 1.21.11-26.2 | Pickup animation in the inventory | Stops at 26.2 |
| Tiny Pickup Animation | 13K | 1.20-26.2, Fabric | HUD pickups | Stops at 26.2 |
| Boing Boing Items (CurseForge) | — | — | Slime-bouncy items | Not checked in depth |
| Subtle Effects | 13.3M | 1.20.1-26.3 | Ambient particles | Different job |

Name check: the slugs `snappy-blocks`, `thunk` and `heft` are free on Modrinth. A web search found no CurseForge mod
with those names.

## 18. Coverage
| Dimension | Answered in |
|---|---|
| Sides | §1, §7 |
| Multiplayer | §5.1-§5.5 remote rules, §7 |
| Persistence | §8 |
| Data safety | §4 pillar 4, §5.4 (render offsets only, reveal watchdog) |
| Uninstall | §8 |
| Updates | §8 (config keys); N/A for world data, none is stored |
| Config | §5.6 |
| Performance | §4 pillar 3, §10 |
| Compatibility | §9 |
| Versions | §1 (26.3 Fabric), §15 (ports) |
| Controls | §6 (no keys) |
| Discoverability | N/A: every effect plays on vanilla actions |
| Accessibility | §5.6 Calm; no flashing; no new sounds without vanilla subtitles |
| Localisation | §5.6: config strings in en_us |
| Game modes | Survival: all effects. Creative: instant breaks skip the pulse, no drops, so pieces only. Adventure: can_break denials rewind via ack. Spectator: no prediction, so only remote effects. Hardcore: as survival. |
| Dimensions | §5 lifecycle: state cleared on dimension change; same rules everywhere |
| Command bypass | `/setblock` and `/fill` don't pop (no place sound); `/setblock destroy` and `/fill destroy` burst within the budget (§5.3) |

## 19. Open questions
- Competitor jars for `wb src jar` study (A Good Place, MIT; Stupid Block Animations): default until answered is no
  download, and M1 builds on our own Having a Blast patterns.
