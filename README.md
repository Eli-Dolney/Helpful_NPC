# Helpful Workers 1.1.0

Minecraft Java 1.21.1, NeoForge 21.1.235 or later 21.1.x, Java 21. Assign NPC workers to farms, forests, mines, construction and builder-placed worker sites through dialogue, reusable role kits, and a guided assignment clipboard. `/worker` commands remain as an optional fallback. No MineColonies dependency.

1.0.0 joins the two development lines into one mod: the **worker sites** line (0.5.x on GitHub `main`: builder-placed sites, warehouses, skins, ownership markers) and the **living workers** line (0.4–0.9: real movement, timed work, quarry/terraform/staircase miners, gatherers, beekeeping, request lists, tabbed dialogue, levels, personalities, town hall). Every gathering role works either on a free area you mark or at a site.

## Install

Put `helpfulworkers-1.1.0.jar` in the `mods` folder of a matching NeoForge instance and remove older Helpful Workers JARs. On a server, install it on the server and every client (the network protocol changed, so 0.5.x/0.9.x clients cannot join a 1.0.0 server). Back up worlds before updating. Worlds from 0.1–0.3, 0.5.x and 0.9.x load: registry ids and NBT keys from both lines are kept.

On a standard Windows launcher the mods folder is `%APPDATA%\.minecraft\mods`; launchers with instances use the instance's own `mods` folder. Put the **JAR**, not the source ZIP, in `mods`. This is a Java Edition mod; it cannot be installed on Bedrock.

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
- **Worker sites** (Talk tab; *Work at a site* / *My site* for other roles) — opens the site menu: build a worker site (builder), join or manage the site this worker works at, release it to go back to free areas
- **Name & look** — personal name and skin: original look, match owner, nine Minecraft defaults, or skins from a resource pack
- **Goodbye** — close

A tab row (Talk, Work, Where, Options, Needs, Build, Craft; only the ones the role uses) jumps between pages, toggles are real checkboxes, role and item icons sit on their buttons, and a status strip at the bottom shows live what the worker is doing (green working, yellow needs help, grey idle, blue asleep). Unavailable actions explain why. Escape closes the screen.

**Equipment screen:** vanilla-style panel with a live model of the worker, empty-slot outlines and hints for armour and hands, and a card with role, health and the current status.

**Clipboard overview** (right-click the clipboard with no assignment running): every worker sorted with the ones that need help first, a summary line, status with request progress, coordinates, distance and how long since the last drop-off. Buttons: Pause/Resume, Recall, Talk (opens the dialogue from any distance), Come here, Show area, and Go to (creative only). Loaded workers show their chosen skin's face.

**Work-area outline:** a thin line box around the area and coloured boxes on the bed (blue), supply (yellow), output (green) and crafting table (orange). It stays up while you sneak holding the clipboard.

**Config:** Mods → Helpful Workers → Config edits the server options (limits, pace, worker sites) and the client options: show the overhead status line, its range, and how long outlines stay up.

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

### Large areas (1.0.0)

Work areas can now be up to **512 × 512 columns and 64 million blocks** (the old limit was 32,768 blocks). Mark the two corners with the clipboard, walking to each one; the far corner may already be out of view. Examples:

- **Forester:** clear or manage a 300 × 300 forest. It searches a budget of columns each work tick and shows *Searching the forest for trees (n%)* until it has looked everywhere, and only rests once the whole area was empty.
- **Miner, terraform:** mark length and width, then the height range (or a dig depth) and the miner levels everything above the floor, for example a whole mountain down to the height you chose.
- **Miner, quarry or excavate to bedrock:** choose the footprint (radius) and *Depth: bedrock*.

Large areas are scanned from the top down in budgeted steps: air above the terrain is skipped from the heightmap and empty layers are jumped over, so a big area does not cost more per tick than a small one. Parts of the area in unloaded chunks are skipped until a player is near them; workers never load chunks.

## Jobs

Farmers harvest and replant vanilla crops. Foresters cut logs and replant supported saplings. Miners excavate a volume or a two-block branch pattern (bottom layer kept as floor). Builders place one blueprint block per cycle. An output container receives deposited items.

### Gathering (0.7.0)

- **Miner quarry mode** (`/worker mode quarry` or the Quarry button): digs sand, gravel, clay, dirt and stone in the marked footprint down to the chosen depth, never below the area's lowest Y, and lays a cobblestone floor on that bottom layer (cobble from the bag or supply chest) so the pit stays walkable. Soft blocks are dug with a shovel when the miner has one. Fluids next to a dug block are sealed as in the other modes.
- **Gatherer** (new role): cuts sugar cane and bamboo above the bottom stalk, kelp from the shore (the top segments, the plant regrows), the top block of cactus, ripe sweet berries and cocoa (one bean is replanted), and glow berries from cave vines, then sweeps up what fell. Toggle each plant on the *Let's choose what to gather* page. Needs a work area, bed/supply/output as usual; no tools required.
- **Rancher beekeeping**: a hive or nest in the pen at honey level 5 with a lit campfire within five blocks below is bottled (glass bottles from the bag or supply) or sheared (shears from the bag or supply) into honey bottles or three honeycomb. Hives without a campfire are left alone. Toggle with *Harvest honey* on the ranch page.
- **Courier routes**: pickups are visited in turn (round-robin). On *Plan my route* each pickup chest can have a filter (hold an item and click the chest row to add it; *Any* clears it) and a *Keep N* count (0/8/16/32/64 of each item stay behind). With a supply chest and furnaces assigned, the courier also carries smeltables and fuel from the supply to the furnaces, a stack at a time.
- **Smelter**: fuel is anything with a burn time (data-driven), blast furnaces only get blasting recipes and smokers only smoking recipes, and furnaces are loaded a full stack at a time. The smelter now walks to the supply chest for smeltables and fuel instead of reaching into it from afar; what it carries for the furnaces stays in its bag until a slot frees up.
- **Request list** (every non-combat role): hold an item, open *What do you need?* and ask for 16, 64 or 256 of it (or `/worker request <count>`). Everything the worker collects counts toward the first open request; the status line and roster show `· Request 10/64 Oak Log`. By default the worker pauses when the list is complete; untick *Pause when the list is collected* to keep going. Up to four items per worker.

### Crafts and logistics (1.1.0)

- **Toolsmith** (kit: paper + iron ingot + stick): assign a crafting table, an anvil and/or a grindstone, a supply chest (sticks, planks, cobblestone, iron, diamonds, repair materials) and an output chest. Each work tick, in order: carry a tool to a workmate whose status says *Missing axe* (or who holds one below 15%), repair worn tools from the bag or supply chest at the anvil (repair material, a quarter of the durability per unit) or grindstone (two of a kind become one), shelve finished tools in the output chest, forge whatever the stock is short of at the best tier the supply can pay for (diamond, iron, stone, wood), and scan owned workers within 64 blocks for demand every 10 s. Stock targets per tool (pickaxe, axe, shovel, hoe, shears, fishing rod) cycle 0/1/2/4 on the *Options* tab. Deliveries go into the workmate's supply chest, or straight into its bag when it has none.
- **Stonemason** (kit: paper + stone bricks + cobblestone): assign a stonecutter, supply (raw stone) and output. Order on the *Options* tab with the block in hand (+16/+64/+256) or through the request list; anything a stonecutter makes counts (bricks, slabs, stairs, walls, chiseled, polished…). Inputs are fetched from supply on foot, one output is cut per work tick with the stonecutter sound, products are shelved. *Cut what nearby builders still need* derives orders from owned builders' remaining custom-blueprint materials (minus what their supply already holds) every 10 s; those orders show *(builder)* and are replaced as the build progresses.
- **Cook** (kit: paper + bowl + coal): assign smokers or furnaces (up to 4), supply (raw food, fuel), output and optionally a crafting table. Keeps the smokers loaded with things that cook into food (ore is never touched, even in a plain furnace), shelves cooked food beyond a reserve of 16, and once a minute brings up to 8 cooked meals to an owned worker within 64 blocks whose bag holds fewer than 4 food items. *Bake bread when there is wheat* keeps 16 bread on the shelf. Every worker now eats the most filling food in its bag first.
- **Courier porter mode** (`/worker mode porter` or the mode button on *Plan my route*): instead of assigned pickups, the courier treats every owned worker's output chest within 96 blocks as a pickup (rescanned each minute) and hauls everything to its own drop-off chest. Filters and keep-N still apply per chest when set. `/worker mode zone` switches back.
- Warehouses restock the new trades too (toolsmith: sticks, iron, cobble, string; stonemason: cobble, stone, deepslate; cook: coal, wheat).

### Builder ground prep

Before placing blocks, a builder prepares the site. Pick the mode with the **Ground** button on the *Build a village house* page (it applies to custom blueprints too) or with `/worker siteprep`:

| Mode | What the builder does first |
| --- | --- |
| Clear (default) | Cuts trees and removes leaves, grass, flowers, mushrooms, vines and snow on the site. Hills and holes are left alone. |
| Flatten | Levels the site to the marked ground height: everything above ground is removed, holes up to 6 deep are filled with dirt (grass on top). |
| Off | Builds straight away, as before. |

For a village house fitted into a marked plot, the site is the whole plot and the ground is the plot's lowest marked corner. For a custom blueprint the site is only the blueprint's rotated footprint and the ground is one block below the placed origin, so the copied area is never touched. Prep never removes blocks the blueprint itself will place, bedrock, containers or other block entities, and leaves water alone above ground. Drops go into the builder's bag (or output chest); fill dirt is free for village houses and plot flattening, while custom blueprints take dirt, grass blocks, cobblestone or coarse dirt from the bag or supply chest.

**Flatten** on the plot row (or `/worker flatten`) levels the marked plot without building anything, so you can prepare ground for your own builds.

## Builder-placed worker sites

Builders keep building **vanilla village houses for free** (no materials; see *Build a village house*), so a village can grow quickly. **Worker sites** cost materials in survival: each needs a crafted site core (recipes below), consumed when construction is confirmed.

Sites are optional. A worker can use a site or an ordinary zone; switching back to zones requires releasing its site first. Its bed is assigned separately.

1. Give a worker the builder role. Carry an **Assignment Clipboard** and the matching crafted core.
2. Talk to the builder and choose **Worker sites** (Talk tab) or **Build a worker site…** (Build tab). Select a mining pit, Logging Camp, Warehouse, Crop Farm, Ranch, Fishing Pond, Smelter Workshop, Builder’s Yard, or Guard Tower.
3. Choose **Select in the world**, hold the clipboard, and right-click two opposite plot corners. The corners describe an inclusive rectangle. The corner heights do not choose the finished ground height.
4. Review the centered footprint, rotate in quarter turns, and raise or lower the ground. **View plot in the world** shows the green boundary, yellow center, and blue entrance; click with the clipboard to return. The initial ground level is the median beneath the footprint. Even-sized plots resolve ties toward the lower X/Z corner.
5. **Confirm and build** consumes one matching core in survival. The builder levels and builds progressively without additional materials. Larger plots do not enlarge buildings or production.
6. Resolve any reported obstruction, then use **Resume site construction** or **Start work**. Work survives save/reload without another core payment. The core becomes operational after construction is checked.
7. Open the completed core and assign an available owned worker with the appropriate role, or talk to that worker and choose **Work at a site**. Work starts automatically using the site bounds and storage; no zone drawing or separate Start action is needed. Supply any missing tools. Core menus provide pause, release, settings, and dismantling.

Normal builders clear approved terrain using a suitable pickaxe, shovel, or axe. Put tools in their bag or assigned supply chest; they walk to fetch missing tools and resume automatically once stocked. Warehouse restocking includes one spare of each construction tool. Clearing uses durability and leaves normal block drops. Snow layers and grass can be replaced during placement. Instant Build retains its tool-free fast construction behavior.

Version 0.5.1 fixes false construction blockages from farmland moisture and fence connections, including disconnected posts left by unfinished older builds. After updating, use **Start work** once on a builder already paused by the old error. Real protected obstructions name the block and coordinates.

**Instant Build** is available during preview and for unfinished jobs, with a Yes/No confirmation. It uses the same core payment and protection checks, processes bounded batches without builder travel, and never charges a second core when resumed. Keep people clear of the construction area.

Snow layers and replaceable grass are replaced during placement; already-cleared columns are skipped without walking. Only the footprint and its included entrance paths are leveled. Each column supports at most eight blocks of cutting or filling. Containers, fluids, ores, bedrock, site blocks, and unapproved blocks stop construction. Approved blocks include ordinary dirt/stone terrain, sand, gravel, clay, and common vegetation. Keep workers and other entities clear of the blocks being placed. The whole site must remain loaded; it never loads chunks itself.

### Site recipes

| Item | Recipe |
| --- | --- |
| Site Frame | Chest in the center, surrounded by eight iron ingots |
| Mining Core | Site Frame in the center, surrounded by eight cobblestone |
| Typed Mining Pit Core | Shapeless Mining Core + matching coal, copper, iron, gold, redstone, lapis, emerald, or diamond block |
| Logging Core | Shapeless Site Frame + oak, spruce, birch, jungle, acacia, dark oak, and cherry saplings + mangrove propagule |
| Warehouse Core | Site Frame in the center, surrounded by eight chests |
| Farm / Ranch / Fishing / Smelter / Builder Core | Shapeless Site Frame + matching role kit |
| Guard Core | Shapeless Site Frame + Knight’s Kit or Archer’s Kit |

These items have recipes and entries in the Helpful Workers creative tab. Creative construction does not consume or later refund a core.

### Mining pits

Each **7 × 5 × 7** pit has three managed ore positions, an accessible platform, core, output chest, and entrance. Assign one miner and supply a suitable pickaxe in its main hand or bag.

| Pit | Yield per node | One node regenerates every | Minimum pickaxe |
| --- | --- | --- | --- |
| Coal | 1 coal | Random 5–25 seconds | Wooden |
| Copper | 1 raw copper | Random 5–25 seconds | Stone |
| Iron | 1 raw iron | Random 5–25 seconds | Stone |
| Gold | 1 raw gold | Random 5–25 seconds | Iron |
| Redstone | 4 redstone | Random 5–25 seconds | Iron |
| Lapis | 4 lapis lazuli | Random 5–25 seconds | Stone |
| Emerald | 1 emerald | Random 5–25 seconds | Iron |
| Diamond | 1 diamond | Random 5–25 seconds | Iron |

Intervals apply to the entire pit, with at most three visible pending nodes. The miner walks to ore, uses tool durability, and deposits fixed yields. Fortune, Silk Touch, and XP do not alter site production. Timers advance only during loaded, working operation; missing tools, full storage, damaged functional blocks, and blocked routes stop work. Supply spare tools in the bag, supply chest, or equipment menu. Delays are sampled independently for each regenerated node and saved across restarts. Blocked routes retry automatically after you clear them.

### Logging camp

The **33 × 20 × 33** camp contains eight separated tree plots, paths, shelter, a core, and an output chest. One forester services oak, spruce, birch, jungle, acacia, dark oak, mangrove, and cherry plots. Supply an axe in its main hand or bag.

All species start enabled. The core menu toggles each slot separately. Disabled slots finish their existing tree but are not replanted. Trees use real vanilla saplings, normal random-tick growth, light and space requirements, and normal drops. Dark oak plants four saplings. New camps finish construction with one full-sized vanilla tree in each of the eight original slots. Their supply chest receives **16 of each species’ sapling**, including 16 mangrove propagules, once per camp. These starter trees do not consume that stock. Interrupted construction remembers both tree progress and the starter grant. Later planting uses collected or supplied saplings; completed older camps are not refilled. There is no fixed regrowth timer.

Tree generation is checked before placement against its reserved growing volume. Trees that do not fit wait and report **Needs growing space**. Only tracked tree blocks are harvested by the assigned forester; unrelated trees and player-placed logs are excluded. Natural trees can be harvested by players. Existing 0.4 managed trees finish harvesting before their slots switch to natural planting.

Choose **Add tree slot** in the core menu, select a species, and confirm the proposed location. Clear ground inside the selected plot is tried first, then nearby extensions. Slots reserve 9 × 19 × 9 blocks with separated centers; at most 32 slots belong to one camp. A loaded available builder prepares the planting spot and an accessible path. No extra core is required; supply saplings for new slots. The location must already have clear, level dirt/grass and a walking connection to the camp entrance.

Managed ore and legacy tree blocks remain protected against player harvesting, pistons, explosions, and fire. **Dismantle site** removes remaining managed resources without drops, releases workers, and returns a paid core once. Buildings, stored items, and natural trees remain.

### Other role buildings and custom designs

Crop farms provide farmland and irrigation; supply seeds and a hoe. Ranches require player-supplied animals. Fishing ponds provide shoreline and water; supply a rod. Smelter workshops assign their furnaces and storage automatically; stock smeltable items and fuel. Builder’s yards remain a home base while their builder constructs another site. Guard towers accept either a knight or archer and assign the appropriate station. Equipment and consumable stock are not generated by these buildings.

From the builder’s site catalog choose **Save a custom worker-site template**. Select a site type, then mark opposite corners including the foundation and roof. The clipboard next asks for the core, entrance, supply, output, and role-specific work positions. Click the floor beneath entrances, work positions, tree positions, and ore nodes. Keep growing volumes and work approaches clear. Confirm the validated design to save it.

One personal template is saved per owner and site type, in that dimension. When building that type again, choose your custom template or the starter design. Limits are 64 blocks wide/deep, 32 high, and 32,768 total blocks. Templates copy block states and functional anchors only: inventories are not copied, site identities are fresh, and production is reset. New logging camps still receive their normal one-time starter trees and saplings. Existing placed buildings retain their saved design even after you replace the library template. Ordinary blueprints still work separately.

### Bed and storage markers

Assigned beds, supply chests, and output chests display a floating item icon and worker name for their owner within 32 blocks. Walls hide markers, and shared storage groups names. Site menus have a **Markers** switch: Nearby, Clipboard only, or Off. The preference is saved on your computer. Renaming and assignment changes update nearby labels automatically.

### Warehouses and couriers

The **25 × 7 × 21** warehouse includes **32 double chests**, accessible aisles, a core, an incoming chest, and an overflow chest. Assign multiple owned couriers through the core or their worker dialogue; assignment starts them automatically.

Warehouses automatically connect to their owner’s workers and resource sites in the same dimension. The nearest eligible warehouse is chosen initially; connections remain stable until explicitly changed or removed. Unlinking excludes that source from that warehouse. **Restore automatic connections** clears exclusions. Use the core menu and clipboard to:

- **Link site output:** click an owned resource-site core.
- **Link worker:** click an owned zone or site worker. Couriers collect when the worker requests unloading or uses at least 24 of its 27 bag slots.
- **Register containers:** click additional chests or barrels, including those in a player-built warehouse. The default maximum is 64 storage inventories; a double chest counts once.
- **Configure a registered container:** choose incoming, sorted, or overflow; then set a category or an exact filter using the item held in your main hand. Menus also show capacity, linked sources, courier status, and unlink/release controls.

Sorting chooses exact-item filters first, categories second, and designated overflow last. Within an equal priority it fills matching stacks before empty slots, then prefers nearby reachable storage and a stable position order. Categories are **ores/minerals, wood, food/farming, stone/building, equipment, and miscellaneous**. Extend their item tags through a data pack under `data/helpfulworkers/tags/item/warehouse/`; miscellaneous also catches uncategorized items.

Couriers visit both pickup and destination, carry at most one stack per work action, reserve pickups briefly, and retain carried items if space or a route becomes unavailable. They prioritize full workers, then incoming/site output, then misplaced warehouse contents. Correctly sorted contents stay put. Only registered containers and linked sources are accessed.

Shared export rules preserve equipment, spare tools, role supplies, approved replacement-recipe ingredients, and builder materials. Planting supplies and food retain reserves. Couriers can unload existing zone workers without converting them to sites. Cross-dimension hauling and offline production remain unavailable.

### Supply delivery

Couriers also carry finite warehouse stock to linked workers’ supply chests. Use **Supplies: worker name** in the warehouse menu to enable/disable restocking, cycle item quantities through 0 / 1 / 16 / 64, add an item held in your hand, or restore role defaults. Defaults provide one spare tool, a stack of common role supplies, and 16 bread. Builder material requests follow the remaining custom blueprint. Add smelting inputs or other preferred supplies through the held-item control.

Keep supply and output in separate containers. Couriers never generate stock: missing items must be put in registered sorted/overflow warehouse storage. They retain undelivered supplies when storage is full or a path fails, and saved deliveries resume after loading. Pickup reservations prevent competing couriers from taking the same stack; a recipient receives one supply delivery at a time. Collection and restocking alternate, with full-worker unloading and warehouse corrections continuing through the existing scheduler.

## Names, icons, and skin library

Talk to an owned worker and select **Name & look** on the Talk tab. Enter a personal name (1–32 characters), choose a look, preview it, and press **Save changes**. The worker keeps its profession label. **Cancel** discards unsaved edits. Names and skins persist through restarts and synchronize to other clients; older workers retain their original look.

The picker includes:

- **Original look:** the worker's existing Minecraft default appearance.
- **Match owner:** the owner's current Java skin and arm model, when that player's skin is available on the client. Otherwise, the worker's original look appears.
- **Nine Minecraft defaults:** Steve, Alex, Ari, Efe, Kai, Makena, Noor, Sunny, and Zuri, with classic/slim arm selection.
- **Your skin library:** valid 64×64 PNG skins supplied by enabled resource packs, with classic/slim arm selection and previews. Invalid images are omitted; unavailable saved skins fall back to the original look.

Dialogue portraits and the clipboard overview reflect the selected skin; the role outfit (hard hat, apron, vest, quiver) is drawn over any skin.

### Add downloaded skins to your library

Use Java-compatible **64×64 PNG** skins. The supplied `helpfulworkers-skin-library.zip` is a blank resource-pack template, not a bundle of downloaded skins.

1. Extract the template into your Minecraft instance's `resourcepacks` folder. The extracted folder must contain `pack.mcmeta` directly.
2. Put your PNGs in `assets/helpfulworkers/textures/entity/worker_skins/` inside that folder. Use lowercase filenames with letters, numbers, underscores, or hyphens, such as `iron_miner.png`.
3. Enable the pack under **Options → Resource Packs**. After adding more files, reload resource packs and reopen **Name & appearance**. Valid files appear automatically in the picker.
4. Choose the skin, set classic/slim arms to match it, and save. Share the same pack with other players, or offer it as the server resource pack, so they see the same custom skins.

To create the pack manually, put this in its `pack.mcmeta`:

```json
{"pack":{"pack_format":34,"description":"Helpful Workers skin library"}}
```

Minecraft Marketplace skin packs are for Bedrock. This Java mod uses Java skin PNGs and the owner's Java profile appearance; it does not import Marketplace purchases. See [Minecraft's skin guide](https://www.minecraft.net/en-us/article/what-is-minecraft-skin).

## Command fallbacks

Commands target your nearest owned worker within 12 blocks and use the same validation as the UI (including kit requirements for roles):

| Command | Effect |
| --- | --- |
| `/worker role farmer\|forester\|miner\|builder\|knight\|archer\|rancher\|fisher\|smelter\|courier\|gatherer` | Assign a role (kit required in survival) |
| `/worker area <x y z> <x y z>` | Set work-area corners (up to 512 × 512 columns) |
| `/worker bind bed\|supply\|output\|table` | Assign the block you look at (within 8 blocks) |
| `/worker clear area\|bed\|supply\|output\|table\|origin` | Clear an assignment |
| `/worker recipe <id>` / `/worker recipe remove <id>` | Approve or remove a crafting recipe |
| `/worker mode excavate\|branches\|terraform\|quarry\|staircase\|strip\|colony` | Miner pattern (terraform and quarry use `/worker depth`) |
| `/worker mode zone\|porter` | Courier: assigned pickup chests, or every owned worker's output chest nearby |
| `/worker depth <n\|-1>` | Dig depth for terraform/quarry (-1 = bedrock) |
| `/worker start\|pause\|home\|status` | Control or inspect |
| `/worker blueprint capture\|list\|load\|place\|preview\|materials` | Builder controls |
| `/worker siteprep clear\|flatten\|none` | Builder ground prep before placing blocks |
| `/worker flatten` | Flatten the marked plot without building |
| `/worker request <count>` / `/worker request clear\|stop` | Ask for N of the held item; clear the list; toggle pause-when-done |
| `/worker site` | Site management for your nearest owned worker (within 8 blocks) |
| `/worker warehouse` | Open the owned site core you are looking at (within 8 blocks) |
| `/worker keep <count>` | Keep that many of the held item in the worker's bag at drop-off (0 = drop all) |


Operators can inspect/reset performance counters with `/helpfulworkers perf` and `/helpfulworkers perf reset`.

## Configuration and troubleshooting

Server configuration defaults include 10 workers per player, 25 workers under the world limit, a 20-tick work interval, a scan budget of 64, and a rancher species cap of 8. NeoForge generates these in `helpfulworkers-server.toml`. The `[sites]` section controls `oreMinSeconds` (5), `oreMaxSeconds` (25), `<ore>Yield`, `<ore>Pickaxe` (`wooden`, `stone`, `iron`, `diamond`, or `netherite`), and `warehouseInventories`. Legacy `<ore>Seconds` and `treeSeconds` fields remain readable for compatibility but no longer drive site production. Site timing follows the existing work scheduler; no offline catch-up is applied.

- **Mod does not load:** check Minecraft 1.21.1, NeoForge 21.1.x, and that only one Helpful Workers JAR is installed.
- **Worker will not start or is blocked:** check role kit, tools, supplies, assignments, storage space, and reachable paths; inspect its status.
- **Clipboard opens a roster:** start an assignment from worker dialogue first.
- **Build fails because of Java:** set `JAVA_HOME` to JDK 21 and restart your terminal/editor.

Navigation, mining hazards, inventory edge cases, multiplayer, and larger groups need further gameplay testing.

## Open and develop on a Windows PC

Install a **JDK 21** and set `JAVA_HOME` to that JDK's installation folder. Confirm `java -version` reports 21. You do not need to install Gradle separately; its wrapper is included.

### Download without Git

Download the [source ZIP](https://github.com/Eli-Dolney/Helpful_NPC/archive/refs/heads/main.zip), extract it, and open the extracted folder containing `build.gradle` in Cursor or your preferred Java editor.

### Clone with Git

In PowerShell:

```powershell
git clone https://github.com/Eli-Dolney/Helpful_NPC.git
cd Helpful_NPC
.\gradlew.bat build
```

To launch a development client or server:

```powershell
.\gradlew.bat runClient
.\gradlew.bat runServer
```

The first server launch may require accepting Minecraft's EULA in the generated server run directory before starting again. Use a separate test world.

On macOS/Linux, use `./gradlew build`, `./gradlew runClient`, or `./gradlew runServer` instead. The first build downloads dependencies and requires internet access. The built mod appears at `build/libs/helpfulworkers-1.1.0.jar`.

### Bring changes between computers

Commit and push your work from the first computer. On the other computer, with local changes committed, run `git pull --ff-only` before continuing. If that command reports diverging histories or local changes, resolve them before overwriting files. Test worlds and build caches are intentionally excluded from Git.

A fresh GitHub clone contains only changes that have been pushed to that repository; check `build.gradle` for its version.

## Roadmap and changelog

The development roadmap lives in `ROADMAP.md`; test results per release are in `VERIFICATION.md`.

**1.1.0 — crafts and logistics.** Three trades and a delivery mode: the **toolsmith** forges the best tier the supply can pay for, repairs worn tools at an anvil or grindstone, keeps per-tool stock targets and carries tools to workmates who report *Missing …*; the **stonemason** cuts orders at a stonecutter (hold the block, +16/+64/+256) and can derive orders from nearby builders' blueprints; the **cook** keeps smokers loaded with food, bakes bread and feeds hungry workmates; couriers get **porter mode** (collect from every owned worker's output chest). Requests placed on a stonemason become cutting orders. Workers eat the most filling food first. Crafting can now run any supported vanilla recipe, not only taught ones. Warehouse restock defaults cover the new roles.

**1.0.0 — one mod.** The worker-sites line (0.5.x) and the living-workers line (0.4–0.9) are merged. Sites, site cores, custom site templates, warehouses with supply delivery, managed trees and ore nodes, ownership markers, names and skins all come back, reachable from the 0.9 tabbed dialogue (*Worker sites*, *Name & look*, *Build a worker site…*). Site workers use the site router (visible stand spots, automatic retry); zone workers keep real movement with on-foot errands. Drop-offs use one shared export rule: tools, role supplies and recipe ingredients stay, `/worker keep` reserves win. Work areas can be up to 512 × 512 columns and 64M blocks with budgeted heightmap scans (300 × 300 forests, mountain terraforming, pits to bedrock). Saved blueprints scroll instead of showing three; non-miners no longer see "dig site" buttons; the `[sites]` config entries have labels. Game-test harness: a watchdog re-forces chunks for tests that would otherwise never start.

**0.5.x (worker sites, GitHub main).** 0.5.3: finished sites are assign-and-go, sites for every role, natural tree slots, warehouse restocking, floating ownership markers, redesigned menus. 0.5.2: mature starter trees for all eight species and 16 saplings of each. 0.5.1: no false blockages from farmland moisture or fence connections; builders fetch missing tools on foot.

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

Recipe teaching supports ordinary shaped/shapeless crafting-table recipes without crafting remainders. The toolsmith does not use the smithing table (no netherite upgrades) and the stonemason has no quarry of its own yet; the cook uses smokers and furnaces, not campfires. Blueprint capture is capped at 4,096 blocks and site templates at 64 × 32 × 64 with unsupported-block checks. Material counts are total blueprint requirements. Navigation, hazards, multiplayer edge cases, and large worker counts still need survival playtesting.
