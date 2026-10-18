# Helpful Workers 0.9.0

Minecraft Java 1.21.1, NeoForge 21.1.235 or later 21.1.x, Java 21. Assign NPC workers to farms, forests, mines, and construction through dialogue, reusable role kits, and a guided assignment clipboard. `/worker` commands remain as an optional fallback.

## Install

Put `helpfulworkers-0.9.0.jar` in the `mods` folder of a matching NeoForge instance. On a server, install it on the server and all clients. Back up worlds before updating. There is no MineColonies dependency. Existing 0.1 workers and saves load with the same registry ids and NBT keys.

## Recruit and talk

Craft a **Recruitment Contract** from two paper, two emeralds, and bread. Use it on a block face to recruit a named worker (the contract is consumed).

Right-click your worker to open dialogue. Ordinary held items no longer gift automatically — use **Let's check your equipment** for transfers. Empty-hand sneak-right-click still starts or pauses work.

Dialogue choices:

- **Let's talk about your work** — role, start, pause, return home, miner mode
- **What do you need?** — the request list: hold an item and ask for 16/64/256 of it; progress shows in the status line and roster; pause-when-done toggle
- **Let's choose crops / what to gather** — farmer crops or gatherer plants (same page for both)
- **Plan my route** (courier) — per-pickup filters, keep N, remove a chest
- **Let me show you where** — assign or clear work area, bed, supply, output, crafting table
- **Let's check your equipment** — 27-slot bag, equipment, and your inventory
- **Let me teach you a recipe** — search crafting recipes, approve or remove
- **Let's build something** — builder capture, library, placement, rotation, preview, materials, start
- **Goodbye** — close

A tab row (Talk, Work, Where, Options, Needs, Build, Recipes; only the ones the role uses) jumps between pages, toggles are real checkboxes, role and item icons sit on their buttons, and a status strip at the bottom shows live what the worker is doing (green working, yellow needs help, grey idle, blue asleep). Unavailable actions explain why. Escape closes the screen.

**Equipment screen:** vanilla-style panel with a live model of the worker, empty-slot outlines and hints for armour and hands, and a card with role, health and the current status.

**Clipboard overview** (right-click the clipboard with no assignment running): every worker sorted with the ones that need help first, a summary line, status with request progress, coordinates, distance and how long since the last drop-off. Buttons: Pause/Resume, Recall, Talk (opens the dialogue from any distance), Come here, Show area, and Go to (creative only).

**Work-area outline:** a thin line box around the area and coloured boxes on the bed (blue), supply (yellow), output (green) and crafting table (orange). It stays up while you sneak holding the clipboard.

**Config:** Mods → Helpful Workers → Config edits the server options (limits, pace) and the new client options: show the overhead status line, its range, and how long outlines stay up.

## Role kits

Reusable, non-stackable shapeless crafts (not consumed when assigning):

| Item | Ingredients |
| --- | --- |
| Farmer's Kit | Paper + wooden hoe + wheat seeds |
| Forester's Kit | Paper + wooden axe + oak sapling |
| Miner's Kit | Paper + wooden pickaxe + cobblestone |
| Builder's Kit | Paper + wooden shovel + bricks |
| Knight's Kit | Paper + iron sword + shield |
| Archer's Kit | Paper + bow + arrow |
| Rancher's Kit | Paper + wheat + lead |
| Fisherman's Kit | Paper + fishing rod + cod |
| Smelter's Kit | Paper + furnace + coal |
| Courier's Kit | Paper + chest + minecart |
| Gatherer's Kit | Paper + wooden hoe + sugar cane |

**Town Hall** (shaped: book on top; plank, emerald, plank in the middle; three stone bricks below). Right-click it to open the worker overview. Right-click with a Recruitment Contract to recruit a worker beside it. Sneak-right-click to make it the gathering point for your workers within 64 blocks that have no bed: Recall and Return home walk them there, and off duty they stay near it.

Use a kit on your worker, then confirm the role. Survival and adventure need the matching kit in inventory; creative is exempt. Role changes pause work and clear transient targets while keeping inventory, location assignments, recipes, and blueprints. Kits do not supply tools or materials.

## Assignment Clipboard

Craft from paper + stick. From dialogue, choose an assignment. If you lack the clipboard, the recipe is shown.

- Beds, chests, and crafting tables: right-click the block
- Work areas: right-click two inclusive corners
- Builder placement: right-click a block face for the adjacent origin

Confirm with a second right-click. Sneak-right-click cancels. Pending sessions are temporary and do not change saved assignments until confirmed. Invalid or cancelled selections leave prior assignments unchanged.

## Jobs

Farmers harvest and replant vanilla crops. Foresters cut logs and replant supported saplings. Miners excavate a volume or a two-block branch pattern (bottom layer kept as floor). Builders place one blueprint block per cycle. An output container receives deposited items.

### Gathering (0.7.0)

- **Miner quarry mode** (`/worker mode quarry` or the Quarry button): digs sand, gravel, clay, dirt and stone in the marked footprint down to the chosen depth, never below the area's lowest Y, and lays a cobblestone floor on that bottom layer (cobble from the bag or supply chest) so the pit stays walkable. Soft blocks are dug with a shovel when the miner has one. Fluids next to a dug block are sealed as in the other modes.
- **Gatherer** (new role): cuts sugar cane and bamboo above the bottom stalk, kelp from the shore (the top segments, the plant regrows), the top block of cactus, ripe sweet berries and cocoa (one bean is replanted), and glow berries from cave vines, then sweeps up what fell. Toggle each plant on the *Let's choose what to gather* page. Needs a work area, bed/supply/output as usual; no tools required.
- **Rancher beekeeping**: a hive or nest in the pen at honey level 5 with a lit campfire within five blocks below is bottled (glass bottles from the bag or supply) or sheared (shears from the bag or supply) into honey bottles or three honeycomb. Hives without a campfire are left alone. Toggle with *Harvest honey* on the ranch page.
- **Courier routes**: pickups are visited in turn (round-robin). On *Plan my route* each pickup chest can have a filter (hold an item and click the chest row to add it; *Any* clears it) and a *Keep N* count (0/8/16/32/64 of each item stay behind). With a supply chest and furnaces assigned, the courier also carries smeltables and fuel from the supply to the furnaces, a stack at a time.
- **Smelter**: fuel is anything with a burn time (data-driven), blast furnaces only get blasting recipes and smokers only smoking recipes, and furnaces are loaded a full stack at a time. The smelter now walks to the supply chest for smeltables and fuel instead of reaching into it from afar; what it carries for the furnaces stays in its bag until a slot frees up.
- **Request list** (every non-combat role): hold an item, open *What do you need?* and ask for 16, 64 or 256 of it (or `/worker request <count>`). Everything the worker collects counts toward the first open request; the status line and roster show `· Request 10/64 Oak Log`. By default the worker pauses when the list is complete; untick *Pause when the list is collected* to keep going. Up to four items per worker.

### Builder ground prep

Before placing blocks, a builder prepares the site. Pick the mode with the **Ground** button on the *Build a village house* page (it applies to custom blueprints too) or with `/worker siteprep`:

| Mode | What the builder does first |
| --- | --- |
| Clear (default) | Cuts trees and removes leaves, grass, flowers, mushrooms, vines and snow on the site. Hills and holes are left alone. |
| Flatten | Levels the site to the marked ground height: everything above ground is removed, holes up to 6 deep are filled with dirt (grass on top). |
| Off | Builds straight away, as before. |

For a village house fitted into a marked plot, the site is the whole plot and the ground is the plot's lowest marked corner. For a custom blueprint the site is only the blueprint's rotated footprint and the ground is one block below the placed origin, so the copied area is never touched. Prep never removes blocks the blueprint itself will place, bedrock, containers or other block entities, and leaves water alone above ground. Drops go into the builder's bag (or output chest); fill dirt is free for village houses and plot flattening, while custom blueprints take dirt, grass blocks, cobblestone or coarse dirt from the bag or supply chest.

**Flatten** on the plot row (or `/worker flatten`) levels the marked plot without building anything, so you can prepare ground for your own builds.

## Command fallbacks

Commands target your nearest owned worker within 12 blocks and use the same validation as the UI (including kit requirements for roles):

| Command | Effect |
| --- | --- |
| `/worker role farmer\|forester\|miner\|builder\|knight\|archer\|rancher\|fisher\|smelter\|courier\|gatherer` | Assign a role (kit required in survival) |
| `/worker area <x y z> <x y z>` | Set work-area corners |
| `/worker bind bed\|supply\|output\|table` | Assign the block you look at (within 8 blocks) |
| `/worker clear area\|bed\|supply\|output\|table\|origin` | Clear an assignment |
| `/worker recipe <id>` / `/worker recipe remove <id>` | Approve or remove a crafting recipe |
| `/worker mode excavate\|branches\|terraform\|quarry\|staircase\|strip\|colony` | Miner pattern (terraform and quarry use `/worker depth`) |
| `/worker depth <n\|-1>` | Dig depth for terraform/quarry (-1 = bedrock) |
| `/worker start\|pause\|home\|status` | Control or inspect |
| `/worker blueprint capture\|list\|load\|place\|preview\|materials` | Builder controls |
| `/worker siteprep clear\|flatten\|none` | Builder ground prep before placing blocks |
| `/worker flatten` | Flatten the marked plot without building |
| `/worker request <count>` / `/worker request clear\|stop` | Ask for N of the held item; clear the list; toggle pause-when-done |
| `/worker keep <count>` | Keep that many of the held item in the worker's bag at drop-off (0 = drop all) |

## Roadmap and changelog

The development roadmap lives in `ROADMAP.md`.

**0.9.0 — depth.** Workers **level up** (1–5) from the work they do: each level breaks blocks 8% faster and adds a heart of health, with a chime, sparkles and a message to you; the level shows on the nameplate, overview, dialogue header and equipment card (with an XP bar). Every worker has a **personality** (cheerful, grumpy, chatty, quiet or curious) that changes half their greetings and their voice pitch, and idle workmates chat with each other now and then. New **sound events** (greet, chatter, a grumble when stuck, level-up) use vanilla villager and player sounds. A **Town Hall** block opens the overview, recruits beside itself and gathers bed-less workers. **Shared chests** work properly: workers announce what they are fetching so two do not walk for the same last items, drop-offs top up existing stacks first, and each worker keeps a bag reserve (items it fetched from the supply, 16 saplings, or what you set with `/worker keep`) instead of handing them straight back; miners take cobblestone from their output chest when the supply has none. **Status lines are translatable**: workers send keys that the client translates, while saves and logic keep English. Off-duty workers with no bed or hall stay near where they stopped instead of wandering off. UI fixes from a screenshot pass at GUI scale 2 and 4: shorter tab and request buttons, and help text no longer hidden behind buttons.

**0.8.0 — UI and polish.** Every role has its own icon and every kit its own item sprite (a satchel with the role symbol and a coloured ribbon). The dialogue gains a tab row, real checkboxes, role and item icons and a live status strip. The equipment screen uses a proper panel with a live worker model, slot outlines and a status card. The clipboard overview sorts workers that need help first, shows server-side state colours, request progress, last drop-off time, and adds Talk-from-afar and creative Go-to. Work areas are drawn as line boxes instead of particles. A config screen and client display options (overhead status on/off and range, outline time) are added. New UI text, role names, kit tooltips and config labels are translation keys in `en_us.json`.

**0.7.0 — gathering automation.** Miners get a **quarry** mode (sand, gravel, clay, dirt, stone down to a chosen depth, cobblestone floor laid on the bottom layer, never below the area). New **Gatherer** role and kit (paper + wooden hoe + sugar cane): sugar cane and bamboo above the root, shoreline kelp, cactus tops, sweet berries, cocoa (replanted) and glow berries, with per-plant toggles on the shared Resources page. **Ranchers keep bees**: full hives over a lit campfire are bottled or sheared with bottles/shears from the bag or supply. **Couriers** visit pickups in turn, filter each pickup by example items, leave *Keep N* behind, and ferry smeltables and fuel from a supply chest to furnaces. **Smelters** use any item with a burn time as fuel, match blast furnaces and smokers to their recipes, load full stacks, and walk to the supply chest instead of reading it remotely. A **request list** ("What do you need?") tracks `collect N of X` per worker with progress on the status line and roster, saved with the worker, pausing when done unless told to keep going. Tool swaps put the previous tool back in the bag instead of losing it.

**0.6.0 — feel alive.** Breaking a block now takes time like it does for a player: the worker faces it, swings, the block cracks and plays hit sounds, and tool and block hardness set the duration (0.5–3 s, scaled by the `workPace` config). Foresters fell trees log by log from the base instead of in one tick; the remaining logs are saved so a reload finishes the tree, and leaves decay naturally. Workers drop off their haul at the output chest before heading to bed, stretch and say good morning on waking, and off-duty workers wander near their bed and look at workmates. Walk up close and look at your worker and it greets you with a role-flavoured line once a minute. The nameplate shows the name only; a second colour-coded line beneath it shows "Role: what I'm doing" (green working, yellow blocked, grey idle, blue sleeping) within 20 blocks. Each role wears a simple outfit over the skin (hard hats, aprons, vests, a quiver). Site prep and village building keep their pace but swing and play place sounds.

**0.5.0 — real movement.** Workers path through wooden doors (opening and closing them), treat fences as walls and prefer going around leaf canopies. Supplies are fetched on foot: `take`/tool pickup, crafting ingredients and armour/weapon upgrades need the worker beside the supply chest or crafting table, with a "Fetching <item> from supply" status on the way (the chest is only peeked at remotely, cached for 10 s, so nobody walks for nothing). Miners, foresters and farmers stand beside a block and work it from vanilla reach instead of walking into its centre. A stuck worker now climbs a recovery ladder — repath, another way in, a placed dirt/cobble step (miners/builders) — and only teleports after three failed routes and 10 s of a visible "Can't reach" status, within 16 blocks and 4 of height; builders step out of the way of a block instead of teleporting; knights and archers only teleport to an owner who is on the ground, more than 48 blocks away, after 5 s without a route. Torch steps in mine plans count as done when a torch is already there.

**0.4.0 — foundations.** Status text now always updates live in open dialogues; passive healing works for every worker (it only fired for some entity ids before); a knocked-out worker goes back to work on its own after recovering; staircase/strip/colony mine plans survive a save and reload; look-around behaviour follows start/pause; `/helpfulworkers perf` shows a per-role table and counts teleport fallbacks.

**0.3.2 — builder ground prep.** Clear / Flatten / Off before building, and Flatten plot only.

## Build

```bash
./gradlew build
```

Use Java 21. The JAR appears under `build/libs/`. First builds need network access for Gradle and NeoForge.

## Limits

Recipe teaching supports ordinary shaped/shapeless crafting-table recipes without crafting remainders. Blueprint capture is capped at 4,096 blocks with unsupported-block checks. Material counts are total blueprint requirements. Navigation, hazards, multiplayer edge cases, and large worker counts still need survival playtesting.
