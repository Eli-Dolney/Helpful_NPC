# Helpful Workers — Minecraft NPC Mod

Recruit NPCs and manage their jobs through dialogue, equipment menus, and a reusable Assignment Clipboard. Version **0.3.1** includes farming, forestry, mining, building, protection, ranching, fishing, smelting, and hauling. No MineColonies dependency is required.

This is an experimental Java Edition mod. The 0.3.1 source builds successfully with Java 21; gameplay, multiplayer, and Windows development still need further testing. Back up a world before trying or updating the mod.

## Download and install

- **[Download the 0.3.1 mod JAR](https://github.com/Eli-Dolney/Helpful_NPC/releases/download/v0.3.1/helpfulworkers-0.3.1.jar)**
- **[Release notes and downloads](https://github.com/Eli-Dolney/Helpful_NPC/releases/tag/v0.3.1)**
- **[Download the source ZIP](https://github.com/Eli-Dolney/Helpful_NPC/archive/refs/heads/main.zip)**

| Requirement | Version |
| --- | --- |
| Minecraft | Java Edition 1.21.1 |
| Mod loader | NeoForge 21.1.235 or newer within 21.1.x |
| Java for development/manual launching | JDK 21 |
| Mod installation | Same JAR on client and server |

1. Create a Minecraft **1.21.1 NeoForge** instance in your launcher, or install NeoForge for that version.
2. Download `helpfulworkers-0.3.1.jar` using the link above.
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

All recipes below are shapeless. Role kits assign a profession; they do not provide working tools or materials.

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

Dialogue provides work controls, role selection, equipment, location assignments, recipe teaching, and role-specific settings. Escape closes the screen.

Start a location assignment in dialogue, then hold the clipboard:

- **Work area:** click two inclusive corners. Terraform mining also asks for dig depth.
- **Bed, container, table, furnace, or pickup:** click a valid block, then click the same target again when prompted to confirm.
- **Custom blueprint origin:** click a block face to choose the adjacent origin, then confirm as prompted.
- **Cancel:** sneak-right-click while an assignment is active.

Follow the in-game prompts; area selection saves after the second corner, whereas individual block assignments require confirmation. Targets must be within eight blocks of you. Assignments do not make chunks stay loaded.

When no assignment is active, right-click the clipboard to open the **worker roster**. It provides status, pause/resume, recall to bed, come-here, and work-area display controls for available loaded workers. Workers in unloaded chunks may be absent or unavailable.

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

## Optional chat commands

Commands target your nearest owned worker within 12 blocks. Location binding uses the block you are looking at within eight blocks. Role commands require the same kit as dialogue outside creative mode.

```text
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

On macOS/Linux, use `./gradlew build`, `./gradlew runClient`, or `./gradlew runServer` instead. The first build downloads dependencies and requires internet access. The built mod appears at `build/libs/helpfulworkers-0.3.1.jar`.

### Bring changes between computers

Commit and push your work from the first computer. On the other computer, with local changes committed, run `git pull --ff-only` before continuing. If that command reports diverging histories or local changes, resolve them before overwriting files. Test worlds and build caches are intentionally excluded from Git.

To work from the exact downloadable 0.3.1 release rather than the newest source, clone the repository and run `git switch -c my-work v0.3.1`.

## Configuration and troubleshooting

Server configuration defaults include 10 workers per player, 25 workers under the world limit, a 20-tick work interval, a scan budget of 64, and a rancher species cap of 8. These settings are declared in `WorkerConfig` and generated by NeoForge as server configuration when the mod runs.

- **Mod does not load:** check Minecraft 1.21.1, NeoForge 21.1.x, and that only one Helpful Workers JAR is installed.
- **Worker will not start or is blocked:** check role kit, tools, supplies, assignments, storage space, and reachable paths; inspect its status.
- **Clipboard opens a roster:** start an assignment from worker dialogue first.
- **Build fails because of Java:** set `JAVA_HOME` to JDK 21 and restart your terminal/editor.

Navigation, mining hazards, inventory edge cases, multiplayer, and larger groups need further gameplay testing.

## License

[MIT](LICENSE).
