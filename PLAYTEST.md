# Play-test checklist — Helpful Workers 1.0.0

Install `build/libs/helpfulworkers-1.0.0.jar` in a NeoForge 21.1.235+ instance (Minecraft 1.21.1, Java 21). Fresh world or a backed-up copy. Tick each line, and note anything odd in the **Notes** column with coordinates and what the overhead status line said.

| # | Check | Expected | Notes |
|---|-------|----------|-------|
| 1 | Craft a Recruitment Contract (2 paper, 2 emeralds, bread); place a **Town Hall**; right-click it with the contract | Worker spawns beside the hall, named, idle | |
| 2 | Right-click the hall empty-handed | Roster/overview opens, worker listed | |
| 3 | Craft a **Forester's Kit**, use it on the worker, confirm | Role changes, outfit (green cap, vest) appears, status line turns "Forester: …" | |
| 4 | Clipboard: assign a work area over a few trees, a bed, a supply chest with an iron axe, an output chest; **Start** | Walks to supply on foot, fetches axe, stands at a trunk, chops log by log with swings and cracks; leaves decay; sapling replanted; logs land in the output chest | |
| 5 | Walk up within 4 blocks and look at the worker | A greeting toast once a minute, villager sound | |
| 6 | Let night fall | Drops haul at output, walks to bed, sleeps; status line blue; "Good morning" at dawn | |
| 7 | Miner's Kit → mode **staircase**, target Y, mark the stair start and direction; Start | Digs stairs, places torches every 8 steps; quit to menu and reload mid-mine: continues without walking back up | |
| 8 | Miner mode **quarry** on a sand/gravel patch | Clears to depth, lays cobblestone floor, sand in output | |
| 9 | Builder's Kit → Build tab → Style, **Ground: Clear/Flatten**, mark a 12×12 plot with trees on it → Build on it | Site prep first (trees/grass removed, or plot levelled), then the village house goes up; house stands on flat ground | |
| 10 | Builder **Flatten** button on a sloped plot | Plot levelled to the lowest corner, holes filled with dirt/grass, nothing built | |
| 11 | Farmer, Gatherer, Rancher (honey with campfire under hive), Fisher, Smelter (supply with coal + raw iron) | Each completes one full cycle (fetch → work → deposit) | |
| 12 | Courier: two pickup chests + drop-off; set a filter by clicking a row while holding an item; Keep 16 | Only filtered items hauled; 16 left behind; round-robin between chests | |
| 13 | Needs tab on a forester: add request "64 oak logs" | Status shows "… request 12/64"; stops when done if the toggle is on | |
| 14 | Build the **Warehouse** site with a builder; assign a courier to it | Courier sorts incoming chests by category; a linked miner's supply chest is restocked with a pickaxe and bread | |
| 15 | Knight with a sword in supply, **follow** mode; walk 60 blocks away | Follows on foot; only teleports when far, on ground, and stuck for 5 s | |
| 16 | Block a worker's path with a fence; watch | Status "Can't reach …", tries another way, places a step (miner/builder) before any teleport | |
| 17 | Open the dialogue while a worker is mid-job | Status strip updates live; close → resumes | |
| 18 | Hit a worker until it would die | Knocked out, recovers at bed for 60 s, then resumes the job on its own | |
| 19 | GUI scale 2 and 4: dialogue tabs, Where page, inventory screen, roster | Nothing overlaps or clips | |
| 20 | `/helpfulworkers perf` after 10 minutes with 5+ workers | Per-role table; teleport fallbacks stay low (ideally 0) | |

**Known limits in 1.0.0:** no stonemason, toolsmith or cook yet (coming in 1.1.0); cross-dimension hauling unavailable; couriers never create stock; blueprint capture max 4,096 blocks; recipe teaching is shaped/shapeless only.
