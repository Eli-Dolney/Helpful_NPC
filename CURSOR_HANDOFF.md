# Cursor handoff: Helpful Workers 0.2

> Historical implementation brief for 0.2. The project is now 0.3.1; see README.md for current usage. On another computer, open the repository folder containing this file rather than the original machine-specific path below.

## Instructions to Cursor

Implement this plan in the existing project folder:
`/Users/elidolney/Documents/Codex/2026-09-20/so-x20/outputs/helpful-workers-java`

Inspect the existing code and applicable AGENTS.md instructions first. Work directly in this folder; do not create a replacement mod or modify the ChatGPT project's synced sources. This is an implementation request, not a request for another plan. Complete the changes, build the JAR, and verify as described below. Resolve routine implementation choices yourself. Report concrete blockers and never claim gameplay tests were performed unless they actually were.

Keep Minecraft 1.21.1, NeoForge 21.1.235, Java 21, and the existing mod ID `helpfulworkers`. Use the project's Gradle wrapper. Consult official NeoForge documentation for this version when needed. Preserve existing workers and saves.

## Goal and current state

Turn the command-driven prototype into a mod players can configure through NPC dialogue and guided world interactions. Keep `/worker` commands as an optional fallback.

Existing code has four jobs, a Recruitment Contract, ownership, a 27-slot worker bag, equipment, saved assignments, approved crafting recipes, and per-worker blueprint libraries. `Worker` handles interaction and persistence; `WorkerCommands` currently mutates its fields directly. `Jobs`, `Crafting`, and `Blueprints` implement existing behavior. There is no graphical management menu yet.

The user selected:
- Predefined dialogue menus, not typed conversation or an AI service.
- Reusable role kits; recruitment still consumes the existing contract.
- Guided world clicks using an assignment tool.
- New menu and item art, while keeping the existing worker appearance.

## 1. Dialogue and worker management

Normal right-click opens an NPC dialogue screen, including when holding an ordinary item. Replace automatic item gifting with deliberate inventory transfers. Preserve empty-hand sneak-right-click as a quick start/pause shortcut.

Show the worker portrait, name, role, current activity, and any blocking problem. Use short predefined dialogue such as “Where should I work?”

Main choices:
- **Let's talk about your work:** role selection, start, pause, return home, and mining mode.
- **Let me show you where:** assign, inspect, or clear work area, bed, supply container, output container, and crafting table.
- **Let's check your equipment:** existing 27-slot bag, equipment slots, and player inventory, with normal transfers and shift-click.
- **Let me teach you a recipe:** search supported recipes by output name, inspect ingredients, approve or remove recipes.
- **Let's build something:** builder-only capture, library selection, placement, rotation, preview, and material counts.
- **Goodbye:** close the screen.

Provide Back on subpages and Escape to close. Explain unavailable actions instead of silently failing. Use an original Minecraft-style design; do not copy MineColonies code or assets.

## 2. Reusable role kits

Add non-stackable, shapeless-crafted items:

| Item | Ingredients |
| --- | --- |
| Farmer's Kit | Paper + wooden hoe + wheat seeds |
| Forester's Kit | Paper + wooden axe + oak sapling |
| Miner's Kit | Paper + wooden pickaxe + cobblestone |
| Builder's Kit | Paper + wooden shovel + bricks |

Kits have no durability and are not consumed when assigning roles. Recruitment still uses the existing consumed Recruitment Contract.

Using a kit on an owned worker opens dialogue with that role selected; confirm before applying. Dialogue role changes require the matching kit in the player's inventory, validated by the server. Commands follow the same survival requirement; creative players are exempt.

Kits do not supply working tools or materials. A role change pauses work and resets transient job targets while retaining inventory, location assignments, recipes, and blueprint library.

Add original kit textures, role icons, item models, translated names, tooltips, and creative-tab entries. Keep the existing worker appearance.

## 3. Guided assignments

Add a reusable **Assignment Clipboard**, crafted from paper + stick.

Starting an assignment in dialogue selects that exact worker and begins a guided clipboard interaction. If the player lacks the clipboard, show its recipe.

- Beds, containers, and crafting tables: right-click a valid block.
- Work areas: right-click two inclusive corners.
- Builder placement: right-click a block face for the adjacent placement origin.
- Show selected worker, current step, and cancellation hint in the action bar.
- Highlight pending selections and show confirmation before saving.
- Sneak-right-click cancels; starting a new assignment replaces the pending one.

Clipboard interactions must not also open containers or place blocks. Validate clicked targets within eight blocks of the player. Sessions stay tied to the originally selected worker, never a nearest-worker lookup. Cancel on disconnect, dimension change, worker death, or worker unloading. Do not force chunks to load. Invalid or cancelled selections leave saved assignments unchanged.

## 4. Server actions, menus, and synchronization

Introduce a shared server-side action layer used by dialogue, role kits, clipboard interactions, and commands, replacing duplicate direct field mutations.

Register a worker menu, client screens, and NeoForge payload handlers. Use standard server-backed inventory slots. Keep client-only rendering out of dedicated-server initialization.

Validate ownership, worker identity, dimension, current menu or assignment session, and supplied values on every action. Dialogue/inventory menus close when the worker dies, unloads, or moves more than eight blocks from the player. Clipboard sessions can continue beyond that distance while the worker remains loaded.

Synchronize display state and results from the server. Send changes while menus are open rather than repeatedly sending full inventories or blueprint contents. Opening a menu stops navigation and suspends job execution while an authorized viewer remains; closing resumes according to the worker's current start/pause state. Explicit Pause must remain paused after closing.

Preserve existing command syntax and nearest-owned-worker targeting within 12 blocks. Route commands through shared validation. Add fallback commands for clearing assignments and removing approved recipes. Command role changes must use the same kit requirements as dialogue.

## 5. Recipes and builders

Recipes:
- Searchable list with output icons, ingredients, and approval state.
- Support ordinary crafting-table recipes the existing engine can safely execute.
- Unsupported special or crafting-remainder recipes must be unavailable with an explanation. Apply the same restrictions to commands; do not imply broader crafting support.

Builder dialogue:
- Capture the assigned area under a player-entered name.
- Confirm before overwriting an existing name.
- List and load this worker's saved blueprints.
- Choose placement through the clipboard.
- Select 0, 90, 180, or 270 degree rotation.
- Show the existing particle preview and readable material counts.
- Start construction explicitly after placement.

Retain the existing 4,096-block capture limit and unsupported-block restrictions. Label material counts as total blueprint requirements, not remaining or missing counts.

## 6. Compatibility and scope

Preserve registry identifiers and saved-data keys. New persistent fields need backward-compatible defaults. Pending clipboard sessions are temporary.

This update covers interaction and usability. Do not add professions, role outfits, colony simulation, external schematic formats, Bedrock support, or a general worker-AI rewrite. Fix existing behavior where it prevents the promised controls from working correctly.

## Implementation order

1. Shared actions, secure menu opening, status synchronization, dialogue navigation.
2. Inventory/equipment management, role kits, guided clipboard assignments.
3. Recipe teaching and complete builder controls.
4. Textures, tooltips, error messages, documentation, and verification.

## Verification and acceptance

Use automated checks for server-side action validation and state transitions where practical, and actual client/server play for interaction behavior. Do not replace gameplay verification with build success.

- Configure and start a newly recruited worker without commands.
- Verify each of the four existing role work cycles after dialogue setup.
- Verify kits remain reusable and recruitment consumes a contract.
- Ordinary right-click must never accidentally transfer items.
- Verify inventory transfers, shift-click, equipment changes, full inventories, and worker death conserve items.
- Invalid/cancelled clipboard selections must preserve saved assignments.
- Two nearby workers must never receive each other's actions.
- A second player must not modify another player's worker through menus, items, or forged action requests.
- Verify save/restart preserves recipes, assignments, inventories, and blueprints; include loading a 0.1 world.
- Verify builder capture, overwrite confirmation, placement, every rotation, preview, and material display through dialogue.
- Check common GUI scales and dedicated-server startup without client-class errors.
- Verify existing commands produce the same validated results as the UI.

Build with `./gradlew build` using Java 21. If unavailable through the normal Java lookup, check `/opt/homebrew/opt/openjdk@21` before installing anything. First-time dependencies may need network access. Use `runClient` and `runServer` for appropriate checks; never use the user's main world for testing.

## Deliverables

- Version 0.2.0 implemented in this folder.
- Built mod JAR under `build/libs/`, with its exact path reported.
- Updated README covering installation, dialogue, kit recipes, clipboard workflow, and command fallbacks.
- Concise verification report distinguishing automated checks, actual gameplay tests, and untested scenarios.

Do not claim full acceptance if interactive or multiplayer tests remain unperformed. Report what is complete and the exact remaining checks.
