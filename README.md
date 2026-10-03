# Helpful Workers — Minecraft NPC Mod

Recruit NPCs and manage their jobs through dialogue, equipment menus, and a reusable Assignment Clipboard. Version **0.5.3** makes finished sites assign-and-go, adds sites for every role, natural tree slots, warehouse restocking, and floating ownership markers. Existing zone jobs, village buildings, custom blueprints, and courier routes remain available. No MineColonies dependency is required.

This is an experimental Java Edition mod for **Minecraft 1.21.1, NeoForge 21.1.235, and Java 21**. Back up your world before updating. Automated server checks are described below; full survival and multiplayer playtesting is still needed.

## Download and install

Build the 0.5.3 source with the included Gradle wrapper, or use the supplied `helpfulworkers-0.5.3.jar`. GitHub downloads are available on the [releases page](https://github.com/Eli-Dolney/Helpful_NPC/releases); check the version on the asset before downloading. This README does not imply that a 0.5.3 release has been published.

| Requirement | Version |
| --- | --- |
| Minecraft | Java Edition 1.21.1 |
| Mod loader | NeoForge 21.1.235 (tested) |
| Java for development/manual launching | JDK 21 |
| Mod installation | Same JAR on client and server |

1. Create a Minecraft **1.21.1 NeoForge** instance in your launcher, or install NeoForge for that version.
2. Obtain `helpfulworkers-0.5.3.jar` from the supplied build or build it using the instructions below.
3. Open that instance's folder and place the JAR in its `mods` folder. Remove older Helpful Workers JARs from that same folder.
4. Launch the NeoForge instance. For multiplayer, install the same mod version on the server and every player's client.

For a standard Windows launcher installation, the mods folder is usually `%APPDATA%\.minecraft\mods`. Launchers with separate instances use the selected instance's own `mods` folder.

The source ZIP is for development; put the **JAR**, not the ZIP, in `mods`. This build is for Java Edition and cannot be installed as a Bedrock or console add-on.

## Get your first worker running

1. Craft a **Recruitment Contract**: paper at top-middle and bottom-middle, with emerald–bread–emerald across the middle row. Use it on a block face to recruit a worker. The contract is consumed outside creative mode.
2. Craft the role kit you want and an Assignment Clipboard using the recipes below.
3. Right-click your worker to talk. Choose **Let's talk about your work → Change job**, or use a role kit on the worker and confirm. Keep the matching kit in your inventory when changing roles in survival; kits are reusable and are not consumed.
4. Open **Let's check your equipment** to transfer tools, armor, and supplies into the equipment slots or 27-slot bag. Right-clicking with an ordinary item opens dialogue rather than gifting it automatically.
5. Choose **Let me show you where**, then use the clipboard to assign locations. For a farmer, assign a farm area, supply chest, and output chest; give it a hoe and planting supplies. Assign a bed for a home/rest location.
6. Use **Start work**. Read the worker's status if supplies are missing or a route is blocked. Marking a non-builder work area can start work automatically, so prepare equipment and storage first.

Empty-hand sneak-right-click toggles work. Role changes pause the worker. Other players cannot manage workers you own.

## Items and recipes

The role-kit and clipboard recipes in this table are shapeless. Role kits assign a profession; they do not provide working tools or materials.

| Item | Ingredients |
| --- | --- |
| Assignment Clipboard | Paper + stick |
| Farmer's Kit | Paper + wooden hoe + wheat seeds |
| Forester's Kit | Paper + wooden axe + oak sapling |
| Miner's Kit | Paper + wooden pickaxe + cobblestone |
| Builder's Kit | Paper + wooden shovel + bricks block |
| Knight's Kit | Paper + iron sword + shield |
| Archer's Kit | Paper + bow + arrow |
| Rancher's Kit | Paper + wheat + lead |
| Fisherman's Kit | Paper + fishing rod + cod |
| Smelter's Kit | Paper + furnace + coal |
| Courier's Kit | Paper + chest + minecart |

These items also appear in the **Helpful Workers** creative tab.

## Dialogue, assignments, and roster

Version 0.5.3 organizes worker dialogue into four visible tabs:

- **Overview:** assign or manage the worker’s site, open equipment, and pause/resume.
- **Mining / Logging / Farming / Building / Ranching / Fishing / Smelting / Deliveries / Protection:** controls specific to that profession. Only miners see mining modes and dig depth. Workers assigned to sites use their automatic bounds rather than zone-drawing prompts.
- **Locations:** assign beds, supply/output chests, work areas, furnaces or courier pickups as appropriate.
- **Manage:** name and appearance, profession changes, crafting recipes and return home.

Menus use inventory-style borders, Minecraft item icons, highlighted selections and readable status tooltips. Longer sections show **Page X / Y** with Previous/Next controls; the mouse wheel also changes pages. Unavailable navigation is disabled. Recipe and blueprint lists no longer hide entries behind fixed display limits. Tab/arrow-key focus and Enter activation remain available; Escape or the top-right close button exits.

Start a location assignment in dialogue, then hold the clipboard:

- **Work area:** click two inclusive corners. Terraform mining also asks for dig depth.
- **Bed, container, table, furnace, or pickup:** click a valid block, then click the same target again when prompted to confirm.
- **Custom blueprint origin:** click a block face to choose the adjacent origin, then confirm as prompted.
- **Cancel:** sneak-right-click while an assignment is active.

Follow the in-game prompts; area selection saves after the second corner, whereas individual block assignments require confirmation. Targets must be within eight blocks of you. Assignments do not make chunks stay loaded.

When no assignment is active, right-click the clipboard to open the **worker roster**. It provides status, pause/resume, recall to bed, come-here, and work-area display controls for available loaded workers. Workers in unloaded chunks may be absent or unavailable.

## Names, icons, and skin library

Talk to an owned worker and select **Manage → Name & appearance**. Enter a personal name (1–32 characters), choose a look, preview it, and press **Save changes**. The worker keeps its profession label. **Cancel** discards unsaved edits. Names and skins persist through restarts and synchronize to other clients; older workers retain their original look.

The picker includes:

- **Original look:** the worker's existing Minecraft default appearance.
- **Match owner:** the owner's current Java skin and arm model, when that player's skin is available on the client. Otherwise, the worker's original look appears.
- **Nine Minecraft defaults:** Steve, Alex, Ari, Efe, Kai, Makena, Noor, Sunny, and Zuri, with classic/slim arm selection.
- **Your skin library:** valid 64×64 PNG skins supplied by enabled resource packs, with classic/slim arm selection and previews. Invalid images are omitted; unavailable saved skins fall back to the original look.

Worker menus use vanilla item artwork and consistent bed, incoming-supply and outgoing-output symbols. Dialogue portraits and the clipboard roster reflect the selected skin.

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

## Worker roles

| Role | Controls and behavior in this build |
| --- | --- |
| Farmer | Harvest/replant supported crops; crop selection and bone-meal toggle |
| Forester | Harvest logs in an assigned area and replant supported saplings |
| Miner | Excavate, branches, terraform, staircase, strip, and colony modes; depth/target-Y controls where applicable |
| Builder | Custom blueprints and village-house options, placement, rotation, preview, and material lists |
| Knight | Melee protection with follow, guard-area, and stay controls |
| Archer | Ranged protection with follow, guard-area, and stay controls |
| Rancher | Animal selection, breeding, sheep shearing, milking, egg collection, and herd-thinning controls |
| Fisherman | Fish near suitable water in an assigned area using a fishing rod |
| Smelter | Supply assigned furnaces with inputs/fuel and collect outputs; assign furnaces, supply, and output storage |
| Courier | Carry items from assigned pickup containers to an assigned drop-off container |

Supply real tools and materials and use status messages to resolve blocked work. The presence of a role in the source does not mean every survival or multiplayer scenario has been tested.

### Recipes and building

**Let me teach you a recipe** searches crafting-table recipes and lets you approve/remove them. Supported recipes are ordinary shaped/shapeless recipes without crafting remainders; an assigned crafting table and ingredients are needed for replacement-tool crafting.

For a builder, use **Build a village house** for the preset workflow or **Custom blueprints** to mark a source area, capture it under a name, load it, choose an origin, rotate, preview, and start building with supplied materials. Follow dialogue prompts for plot size and placement. Custom capture is limited to 4,096 blocks and rejects unsupported blocks. Material counts represent total blueprint requirements.

## Builder-placed worker sites

Sites are optional. A worker can use a site or an ordinary zone; switching back to zones requires releasing its site first. Its bed is assigned separately.

1. Give a worker the builder role. Carry an **Assignment Clipboard** and the matching crafted core.
2. Talk to the builder and choose **Building → Build a worker site**. Select a mining pit, Logging Camp, Warehouse, Crop Farm, Ranch, Fishing Pond, Smelter Workshop, Builder’s Yard, or Guard Tower.
3. Choose **Select in the world**, hold the clipboard, and right-click two opposite plot corners. The corners describe an inclusive rectangle. The corner heights do not choose the finished ground height.
4. Review the centered footprint, rotate in quarter turns, and raise or lower the ground. **View plot in the world** shows the green boundary, yellow center, and blue entrance; click with the clipboard to return. The initial ground level is the median beneath the footprint. Even-sized plots resolve ties toward the lower X/Z corner.
5. **Confirm and build** consumes one matching core in survival. The builder levels and builds progressively without additional materials. Larger plots do not enlarge buildings or production.
6. Resolve any reported obstruction, then use **Resume site construction** or **Start work**. Work survives save/reload without another core payment. The core becomes operational after construction is checked.
7. Open the completed core and assign an available owned worker with the appropriate role. Work starts automatically using the site bounds and storage; no zone drawing or separate Start action is needed. Supply any missing tools. Core menus provide pause, release, settings, and dismantling.

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

## Optional chat commands

Commands target your nearest owned worker within 12 blocks. Location binding uses the block you are looking at within eight blocks. Role commands require the same kit as dialogue outside creative mode.

```text
/worker site
/worker warehouse
/worker status
/worker start
/worker pause
/worker home
/worker role farmer
/worker area <x1> <y1> <z1> <x2> <y2> <z2>
/worker bind supply
/worker clear area
/worker recipe <namespace:id>
/worker recipe remove <namespace:id>
/worker mode excavate
/worker depth <depth>
/worker blueprint capture <name>
/worker blueprint list
/worker blueprint load <name>
/worker blueprint place <rotation>
/worker blueprint preview
/worker blueprint materials
```

`/worker site` opens site management for your nearest owned worker (stand within eight blocks). `/worker warehouse` opens the owned core you are looking at within eight blocks. Both use the same validated menus as dialogue.

Role values: `farmer`, `forester`, `miner`, `builder`, `knight`, `archer`, `rancher`, `fisher`, `smelter`, `courier`.

Bind values: `bed`, `supply`, `output`, `table`. Clear values: those four plus `area` and `origin`. Blueprint rotation is `0`–`3` quarter turns. Depth accepts `-1`–`256`; prefer dialogue for mode-specific depth choices. Use command autocomplete for supported modes and arguments.

Operators can inspect/reset performance counters using `/helpfulworkers perf` and `/helpfulworkers perf reset`.

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

On macOS/Linux, use `./gradlew build`, `./gradlew runClient`, or `./gradlew runServer` instead. The first build downloads dependencies and requires internet access. The built mod appears at `build/libs/helpfulworkers-0.5.3.jar`.

### Bring changes between computers

Commit and push your work from the first computer. On the other computer, with local changes committed, run `git pull --ff-only` before continuing. If that command reports diverging histories or local changes, resolve them before overwriting files. Test worlds and build caches are intentionally excluded from Git.

Use the supplied source folder for this 0.5.3 build. A fresh GitHub clone contains only changes that have been pushed to that repository; check `build.gradle` for its version.

## Configuration and troubleshooting

Server configuration defaults include 10 workers per player, 25 workers under the world limit, a 20-tick work interval, a scan budget of 64, and a rancher species cap of 8. NeoForge generates these in `helpfulworkers-server.toml`. The `[sites]` section controls `oreMinSeconds` (5), `oreMaxSeconds` (25), `<ore>Yield`, `<ore>Pickaxe` (`wooden`, `stone`, `iron`, `diamond`, or `netherite`), and `warehouseInventories`. Legacy `<ore>Seconds` and `treeSeconds` fields remain readable for compatibility but no longer drive site production. Site timing follows the existing work scheduler; no offline catch-up is applied.

- **Mod does not load:** check Minecraft 1.21.1, NeoForge 21.1.x, and that only one Helpful Workers JAR is installed.
- **Worker will not start or is blocked:** check role kit, tools, supplies, assignments, storage space, and reachable paths; inspect its status.
- **Clipboard opens a roster:** start an assignment from worker dialogue first.
- **Build fails because of Java:** set `JAVA_HOME` to JDK 21 and restart your terminal/editor.

Navigation, mining hazards, inventory edge cases, multiplayer, and larger groups need further gameplay testing.

## Verification

Run the automated Minecraft server checks with `./gradlew runGameTestServer`, or `.\gradlew.bat runGameTestServer` on Windows. Run `build` to produce the distributable JAR.

**Verified locally:** `build` succeeded and all **47 required GameTests passed** using Java 21, Minecraft 1.21.1, and NeoForge 21.1.235. Packaged metadata, JSON resources, core recipes/icons, and site/warehouse classes were checked in the JAR.

The 0.5.2 checks cover mature starter trees for all eight species, exactly 16 planting items per species, restart-safe starter grants, and full-chest item conservation. The 0.5.1 checks cover farmland moisture, repair of disconnected fences, a builder walking to fetch a missing tool and back to clear stone, tool durability and full-bag swaps, and protected containers. These are automated server checks; the fix has not been visually verified in the player’s world.

The verification suite also includes actual miner and courier walking, repeated ore harvesting and deposit, finite-stock resupply, all eight vanilla tree species and containment, saved tree-slot extensions, snow-covered Instant Build, role-site operation, template anchor rotation/persistence, and the prior ownership, filtering, item-conservation, blueprint, and zone-job regressions.

The 0.5.3 client check passed **116 screen cases across GUI scales 1–4**, including page navigation, keyboard focus, persistent recipe search, all ten professions, and access to the last entries in long recipe/blueprint lists. Representative screenshots were reviewed for sharp text and layout. This used test menu data in an isolated client; it is not a multiplayer or player-world gameplay test.

The optional `./gradlew runUiTest` opens an isolated client in `run/uitest`, checks worker/menu layouts and keyboard focus at GUI scales 1–4, captures screenshots, and closes itself without opening a player world. Screenshots are saved under `run/uitest/screenshots`. A development client startup also loads the mod and its recipes. Automated server tests do not establish visual correctness at every GUI scale or compatibility with every shader pack. The release report states the actual completed checks; keep a world backup when upgrading from 0.4.x.
