# Helpful Workers roadmap: alive NPCs, real movement, gathering automation, better UI

## Context

Helpful Workers (NeoForge 1.21.1, Java 21, `src/main/java/com/example/helpfulworkers/`) already has 10 roles, dialogue, clipboard assignments, blueprints and site prep (0.3.2). The goal is "automation, but with NPCs living in your world": workers that walk, gather, haul and build in a way that looks and feels like a person doing it, with UI that shows what each one is up to. The survey found the main gaps:

- Movement is faked in places: `hopNear`, follow, recall, builder and site prep all teleport; `Jobs.take`, `Jobs.tool`, `Crafting.tryCraft`, `WorkerEquip` read the supply chest from any distance.
- Nothing ambient: blocks break instantly with no swing or sound, whole trees fall in one tick (`Jobs.fell`), no wander, no routine, no speech.
- Bugs: ~55 `worker.status = ...` writes never set `statusDirty` so the dialogue does not update live; passive regen in `Worker.tryEatAndHeal` only fires for entity ids divisible by 20; knocked-out workers never resume; `minePlan` and `treeIndex` are lost on reload; `rebuildGoals` is only called on load/role/companion change so look-around goals get stale.
- Gathering coverage: no sand/gravel/clay quarrying, no sugar cane/bamboo/kelp/berries/cactus, courier has no filters, smelter fuel list hard-coded.
- UI: nameplate shows only name and role; inventory screen is flat rectangles (`worker_inventory.png` unused); roster guesses state from status text; 14 lang keys, every string hard-coded; no sounds; no role outfits (`textures/entity/worker.png` unused).

Each phase below is a shippable release. Order is chosen so every release makes play visibly better, and foundations (status sync, movement) land before features that depend on them.

---

## Phase 1 — Fix the foundations (0.4.0)

Small, high-confidence fixes that everything else builds on.

1. **Status sync.** Replace direct `worker.status = ...` with `worker.setStatus(...)` across `Jobs`, `ExtraJobs`, `Blueprints`, `SitePrep`, `Crafting`, `WorkerCombat`, `Worker.updateCombatStatus`. Make `status` private-ish (package field stays, but add a lint grep to the game tests: none outside `setStatus`). Live dialogue updates come for free via `statusDirty` → `WorkerNetwork.syncStatusToViewers`.
2. **Regen bug.** In `Worker.tryEatAndHeal` use `tickCount % 40 == 0` relative to the 20-tick stagger (i.e. a counter incremented per call), not raw `tickCount`.
3. **Resume after knock-out.** In `WorkerCombat.knockOut`, remember `wasWorking`; when `recoverUntil` passes in `Worker.tick`, restore `working` and set status "Back to work".
4. **Persist transient plans.** Save `minePlan`/`minePlanIndex` (as a `ListTag` of step longs + type) and `idleLevel`; skip `treeIndex` (cheap to rebuild) but seed `treeIndexRefresh` so it rebuilds on first tick.
5. **Goals follow state.** Call `rebuildGoals()` from `WorkerActions.start/pause/toggleWork` and after knock-out recovery.
6. **Perf per role.** Extend `WorkerPerf` to a per-role map (avg/peak ns) so later phases can be measured. `/helpfulworkers perf` prints a table.

Verification: `./gradlew build runGameTestServer` (JAVA_HOME = `~/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.11+10/Contents/Home`). Add tests: status write sets dirty; knock-out then recovery resumes; minePlan survives `addAdditionalSaveData`/`readAdditionalSaveData` round trip.

---

## Phase 2 — Real movement (0.5.0) — done

Make walking honest, keep teleports only as a last resort, and make it visible when a worker is stuck.

1. [x] **Pathfinding setup in `Worker` constructor.** `GroundPathNavigation` with `setCanOpenDoors(true)`, `setCanPassDoors(true)`, `setCanFloat(true)`; malus `DOOR_WOOD_CLOSED=0`, `FENCE=-1`, `LEAVES=4` (so foresters prefer going around canopies), `DANGER_FIRE=16`. `OpenDoorGoal(true)` for all roles.
2. [x] **Reach, don't teleport.**
   - [x] `Jobs.take`/`Jobs.tool`/`Crafting.tryCraft`/`WorkerEquip`: require `approachWithin(chest, 2.5, 2)` before touching the supply chest or crafting table; while travelling set status "Fetching <item> from supply". "Supply has X" cached per 10 s (`Worker.supplyPeek`); peek allowed remotely, take requires presence (an *errand* on `WorkerNav`, walked before the job target; callers get `false` and retry next tick).
   - [x] `hopNear`: only after 3 failed routes **and** 10 s of visible "Can't reach" status, within 16 blocks horizontally and 4 Y. Counted in `WorkerPerf` and per worker (`Worker.hops`).
   - [x] Builder/SitePrep "stand inside block" fix: `WorkerNav.stepAside` walks to the nearest free neighbour within 2, teleports only if there is none.
   - [x] Follow mode: teleport threshold 48, only when the owner is on ground and the worker has had no path/progress for 5 s.
3. [x] **Stuck recovery ladder** (`WorkerNav`): repath → alternate stand spot (8 neighbours at ±1 Y) → place a dirt/cobble step (miner/builder with blocks) → `hopNear`. Each rung sets a distinct "Can't reach … — …" status.
4. [x] **Stand-spot selection for jobs.** Miners/foresters/farmers use `Worker.approachStand`: a free neighbour at the same or +1 Y with solid ground, reach check `distanceToSqr <= 4.5*4.5` from the eyes.
5. [x] **Work-area paths.** `PLACE_TORCH` counts as done when a torch exists at the step (standing or hung), so fast-forward works after reload.

Verification: game tests `walksThroughClosedDoorToChest`, `supplyTakeNeedsProximity`, `flatWalkNeverTeleports`, `standSpotIsBesideTarget`, `torchStepCountsAsDone`. Manual (still open): watch a forester cross a fence line with a gate. Left for later: the smelter still reads smeltables/fuel from the supply chest remotely when loading furnaces.

---

## Phase 3 — Feel alive (0.6.0) — shipped 2026-10-17 (rain shelter and sounds.json skipped; vanilla sounds used)

Visible, audible, paced work plus small life routines. Nothing here changes what jobs *do*.

1. **Block work takes time.** New `WorkAnimator` on `Worker`: a target pos + progress 0..9. Each work tick: `swing(MAIN_HAND)`, `level.destroyBlockProgress(id, pos, stage)`, play the block's hit sound every 4 ticks; break at `destroySpeed`-derived duration (min 10 ticks, max 60) honouring tool tier (`getDestroySpeed` vs `isCorrectToolForDrops`). Apply in `Jobs.breakBlock`, `SitePrep.remove`, `Blueprints.tick` (place = swing + place sound). Config `WorkerConfig.workPace` (0.5–2.0×).
2. **Trees fall log by log.** Rewrite `Jobs.fell` to queue the flood-filled logs on the worker (`fellQueue` list, saved to NBT) and chop 1 per work tick from the bottom with the animator; leaves decay naturally; replant when the queue empties. Keep the 200-log cap.
3. **Daily routine** (`WorkerSchedule`, driven from `Worker.tick`):
   - Morning: wake, "stretch" (arm swing), walk to supply, pick tools.
   - Work: as now.
   - Dusk: deposit to output, walk home, sit on bed edge or sleep (`startSleeping` exists).
   - Rain: foresters/farmers shelter under nearest roof block within 16 (optional, config).
   - Paused workers wander within 8 blocks of bed (`WaterAvoidingRandomStrollGoal` priority 7) and look at players.
4. **Social moments.** When two owned workers are within 3 blocks and both idle, face each other for 3 s (`LookAtMob` on `Worker` class). On player approach (< 4 blocks, looking at them) play a short greeting line via `ToastPayload` once per 60 s: role-flavoured ("Got 14 oak logs for you so far."). Lines live in lang (Phase 5).
5. **Sounds.** Add `sounds.json` with `worker.greet`, `worker.hum` (optional, off by default), reuse vanilla block/tool sounds for work. No custom .ogg required at first; map to villager/player sounds with pitch variation.
6. **Nameplate + overhead status.** Replace `getDisplayName` text with name only; add a second line (custom `WorkerRenderer` text layer) showing a short status glyph + text ("⛏ Mining 12,64,-3", "💤", "⚠ Missing axe") colour-coded (working green, blocked yellow, idle grey). Driven by `WorkerStatusPayload` broadcast to tracking players every 2 s when changed (new `tracking` distributor, not just viewers).
7. **Role outfits.** Use the unused `textures/entity/worker.png` as a base and add per-role overlays (apron, hard hat, quiver) as a second `RenderLayer` with `textures/entity/overlay_<role>.png`. Keep the player-skin base so existing look is preserved.

Verification: game test that a forester with a 6-log tree takes ≥ 6 work ticks to clear it and ends with a sapling; animator progress resets on target change. Manual checks for sound, overhead text, outfits, night routine.

---

## Phase 4 — Gathering automation (0.7.0) — shipped 2026-10-17 (item 6 shared supply chain deferred; courier "keep N" is per pickup chest)

Make the "collect materials for me" promise broad and reliable. Each item reuses `find`/`eligible`/`handleInventory`/`deposit`.

1. [x] **Quarrier mode for miners** (`mode = "quarry"`): dig sand, gravel, clay, dirt, stone in the area down to `digDepth`, never below Y of area min, auto-place cobble floor so the pit stays walkable; silk-touch aware (use picked tool).
2. [x] **Gatherer role** (melons/pumpkins stay with the farmer) (new kit: paper + wooden hoe + sugar cane): sugar cane (keep bottom), bamboo, kelp (from boat-free shoreline, top segments), sweet berries, cactus (top only), cocoa, glow berries, melons/pumpkins if farmer disabled. Reuses farmer plumbing (`cropEnabled` style toggles, `CROPS` page becomes a generic "Resources" page).
3. [x] **Beekeeper tasks for rancher**: harvest honey bottles / comb from hives with campfire check (`BeehiveBlock.getHoneyLevel == 5`, campfire within 5 below) using bottles/shears from bag.
4. [x] **Courier upgrade** (filters by held example item on a Routes page; keep N per pickup; supply → furnaces): per-pickup filters (item tag or example stack set by right-clicking an item into a filter slot in the dialogue), round-robin over pickups, "keep N in output" minimum stocks, delivery to furnaces for smelter (supply → furnaces hop).
5. [x] **Smelter** (walks to the supply chest on an errand): data-driven fuel (`ItemTags`-based: anything with burn time > 0 via `FurnaceBlockEntity.getFuel()`), choose furnace type by recipe (blast furnace for ores, smoker for food), bulk load up to stack size.
6. [x] **Shared supply chain** (0.9.0: claims on shared chests, merging inserts, per-worker reserves and `/worker keep`, cobble from output): `Worker.supply`/`output` may point to the same chest for multiple workers; add a `WorkerStorage` helper that merges stacks and respects per-worker "reserve" counts; miners auto-seal with cobble from output if supply lacks it.
7. [x] **Request board** (any non-combat role tracks its own list; progress is appended to the status line) (dialogue: "What do you need?"): owner picks an item + count; the right worker role (or the courier) tracks "collect 64 oak logs" and reports progress in status and roster; stops or continues per toggle. Stored on entity as `List<Request>`.

Verification: game tests per job on tiny areas (sugar cane column, hive with campfire, courier filter accepts/rejects), status asserts "Request 10/64" — all in `WorkerGameTests` (35 pass). Manual (still open): 3 workers sharing one chest for an hour, perf table flat.

---

## Phase 5 — UI and polish (0.8.0) — shipped 2026-10-18; dialogue strings translatable 2026-10-18 (server status text still English; see item 1)

1. [x] **Translatable strings** (0.9.0: worker status lines are now keys too, sent as codes and translated on the client while NBT keeps English. Earlier: dialogue pages, button labels, tooltips, page help text, greeting lines, `WorkerLines` and the owner toasts are keys with a lint test; the free-text server status string stays English). Every `WorkerDialogueScreen` label, tooltip and help line is a `Component.translatable` key under `gui.helpfulworkers.dialogue.*`, and the 36 role greeting lines are `line.helpfulworkers.<role>.<n>`; `Lang.all()` covers them, including the per-mode, per-crop, per-biome, per-area and per-role families. `ToastPayload` now carries a `Component`, so the greeting and "Worker not found" toasts translate on each client. Page ids and `DialogueActionPayload` strings are unchanged. **Deferred:** `Worker.status` and `ActionResult` text (hundreds of `setStatus("...")` call sites, matched by prefix in `Worker`) stay a plain English `String` in NBT and on the wire; moving them to `Component` with a legacy-string reader is a separate pass. Server-supplied recipe reasons, item names and `VillageCatalog` house labels also stay as sent.
2. [x] **Dialogue redesign** (tab row, vanilla checkboxes, role/item icons, live status strip, wheel-scroll recipes; selection lists not adopted). Tabbed layout (Work / Where / Items / Build / Talk) with real `Checkbox` widgets, icons for roles and items, scrollable lists (`AbstractSelectionList`) for recipes/blueprints/requests, and a live status strip. Keep page ids and `DialogueActionPayload` strings so servers/clients stay compatible.
3. [x] **Inventory screen** (reserve-count tooltips wait for Phase 4 item 6). Use `worker_inventory.png` background, slot highlights, equipment slot icons, tooltips for reserve counts (Phase 4).
4. [x] **Roster → Overview** (request progress rides on the status; "Assign request" is the Talk button → Needs tab). Add per-worker request progress, "blocked" reason, last deposit time, sorted by state; buttons: Teleport-to (creative only), Open dialogue remotely (remote viewer path already exists in `WorkerSessions.openViewer(..., remote=true)`), Assign request.
5. [x] **In-world markers** (coloured boxes and posts instead of billboard icons). Replace particle outline with a thin rendered box (`LevelRenderer.renderLineBox` in a client render event) that persists while sneaking with the clipboard; chest/bed/table icons billboarded above the blocks.
6. [x] **Config screen** (teleport fallback and sounds toggles not added). Expose `WorkerConfig` (caps, pace, teleport fallback on/off, sounds) via NeoForge config screen factory.

Verification: screenshot pass on GUI scales 1–4 (`runClient`), language file lint test (every `translatable` key exists), roster shows a blocked worker in yellow within 2 s of the chest filling.

---

## Phase 6 — Depth (0.9+) — 0.9.0 shipped 2026-10-18: worker levels, personalities and chatter, town hall block, sounds

Ideas to pick from once the above is stable: worker levels (faster breaking / better drops with experience), per-worker names and personalities (favourite lines), a town hall block to spawn/manage from one place, multi-worker builds (several builders on one blueprint sharing `scanCursor` ranges), trading goods to villagers, Jade/WTHIT tooltips, API for other mods to register jobs.

---

## 1.0.0 — one mod (merge of the worker-sites line and 0.9.0) — 2026-10-18

GitHub `main` (0.5.3, worker sites) and this line (0.4–0.9) split after 0.3.1; the 0.9.0 commit had been stacked on 0.5.3 while deleting its files, so 0.5.3 was re-applied with a 3-way cherry-pick (base 0.3.1) rather than `git merge`, which was a no-op.

1. [x] **Both feature sets in one build.** Sites (Site Frame, mining/logging/warehouse/farm/ranch/fishing/smelter/builder/guard cores, construction that costs a core in survival, Instant Build, custom site templates), warehouses with supply delivery, managed trees and ore nodes, ownership markers, names and skins, plus everything from 0.4–0.9. Registry ids and NBT keys of both lines kept; network protocol 6.
2. [x] **Reachable from the 0.9 dialogue.** Talk tab: *Worker sites* / *Work at a site* / *My site*, *Name & look*. Build tab: *Build a worker site…* (catalog and custom site templates). Site workers' Work/Where tabs show *Manage my site* instead of area drawing. The 0.5.3 four-tab menu layer was not kept (its pager rewrote buttons and could not hold checkboxes or translated labels); a light row pager now keeps 0.9 pages usable on short screens.
3. [x] **Shared systems joined.** Drop-offs and warehouse pickups use one export rule (`StorageOps.exportable`) that honours `/worker keep` reserves first; site/construction workers route with `SiteNavigation`, zone workers with `WorkerNav`; skins render under the role outfits.
4. [x] **Large areas.** Up to 512 × 512 columns and 64M blocks (was 32,768 blocks). Areas above 32,768 blocks scan top-down with heightmap skips and empty-layer jumps; foresters keep searching a big forest before resting. `/worker area` and the clipboard accept a far corner that is no longer loaded.
5. [ ] **Follow-ups.** Translate the server-built site menus (`SiteUi`, site statuses) and `ActionResult` toasts; a gatherer site type; multi-worker sharing of one huge area (several foresters on one 300 × 300 forest split by `scanCursor` ranges); in-world play of everything listed under "Not played" in `VERIFICATION.md`.

---

## Working conventions for every phase

- Keep registry ids and NBT keys; new fields default sensibly (follow the `SitePrep` legacy-save pattern).
- All state changes go through `WorkerActions` (ownership/menu validation) and `WorkerActions.sync`.
- Status only via `setStatus`.
- Budget every loop per tick (pattern: `SCAN_BUDGET`/`BLOCK_BUDGET` in `SitePrep`), and check `WorkerPerf` before/after.
- Each phase: bump version in `build.gradle` + `neoforge.mods.toml`, update `README.md` and `VERIFICATION.md`, run `./gradlew build runGameTestServer`, then a short `runClient` play check before calling it done.

## Suggested first session after approval

Phase 1 in full (half a day of work), then Phase 2 items 1–2 (navigation setup, supply-chest proximity) since they change the feel the most for the least risk.
