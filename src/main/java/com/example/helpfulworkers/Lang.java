package com.example.helpfulworkers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Translation keys used by the UI. Every key here must exist in en_us.json; the
 * {@code langKeysExist} game test checks that, so add new keys here rather than inline.
 */
final class Lang {
    private Lang() {}

    // Dialogue tabs and status strip
    static final String TAB_TALK = "gui.helpfulworkers.tab.talk";
    static final String TAB_WORK = "gui.helpfulworkers.tab.work";
    static final String TAB_WHERE = "gui.helpfulworkers.tab.where";
    static final String TAB_OPTIONS = "gui.helpfulworkers.tab.options";
    static final String TAB_NEEDS = "gui.helpfulworkers.tab.needs";
    static final String TAB_CRAFT = "gui.helpfulworkers.tab.craft";
    static final String TAB_BUILD = "gui.helpfulworkers.tab.build";
    static final String STATE_WORKING = "gui.helpfulworkers.state.working";
    static final String STATE_BLOCKED = "gui.helpfulworkers.state.blocked";
    static final String STATE_IDLE = "gui.helpfulworkers.state.idle";
    static final String STATE_SLEEPING = "gui.helpfulworkers.state.sleeping";

    // Inventory screen
    static final String INV_HEAD = "gui.helpfulworkers.inventory.head";
    static final String INV_CHEST = "gui.helpfulworkers.inventory.chest";
    static final String INV_LEGS = "gui.helpfulworkers.inventory.legs";
    static final String INV_FEET = "gui.helpfulworkers.inventory.feet";
    static final String INV_MAINHAND = "gui.helpfulworkers.inventory.mainhand";
    static final String INV_OFFHAND = "gui.helpfulworkers.inventory.offhand";
    static final String INV_HEALTH = "gui.helpfulworkers.inventory.health";
    static final String INV_TITLE = "gui.helpfulworkers.inventory.title";

    // Roster / overview
    static final String ROSTER_TITLE = "gui.helpfulworkers.roster.title";
    static final String ROSTER_UPDATES = "gui.helpfulworkers.roster.updates";
    static final String ROSTER_EMPTY = "gui.helpfulworkers.roster.empty";
    static final String ROSTER_PAUSE = "gui.helpfulworkers.roster.pause";
    static final String ROSTER_RESUME = "gui.helpfulworkers.roster.resume";
    static final String ROSTER_RECALL = "gui.helpfulworkers.roster.recall";
    static final String ROSTER_NO_BED = "gui.helpfulworkers.roster.no_bed";
    static final String ROSTER_COME = "gui.helpfulworkers.roster.come";
    static final String ROSTER_AREA = "gui.helpfulworkers.roster.area";
    static final String ROSTER_TALK = "gui.helpfulworkers.roster.talk";
    static final String ROSTER_TELEPORT = "gui.helpfulworkers.roster.teleport";
    static final String ROSTER_REFRESH = "gui.helpfulworkers.roster.refresh";
    static final String ROSTER_CLOSE = "gui.helpfulworkers.roster.close";
    static final String ROSTER_WHERE = "gui.helpfulworkers.roster.where";
    static final String ROSTER_LAST_DROP = "gui.helpfulworkers.roster.last_drop";
    static final String ROSTER_NEVER_DROPPED = "gui.helpfulworkers.roster.never_dropped";
    static final String ROSTER_SUMMARY = "gui.helpfulworkers.roster.summary";
    static final String ROSTER_RANGE = "gui.helpfulworkers.roster.range";

    // Worker status lines (server sets them with Worker.setStatusKey; clients translate)
    static final String STATUS_REQUEST = "status.helpfulworkers.request";
    static final String STATUS_REQUESTS_DONE = "status.helpfulworkers.requests_done";
    static final String STATUS_FULL = "status.helpfulworkers.out_of_space";
    static final String STATUS_CANT_REACH_SUPPLY = "status.helpfulworkers.cant_reach_supply";
    static final String STATUS_SUPPLY_CHEST_MISSING = "status.helpfulworkers.supply_chest_missing";
    static final String STATUS_SET_PEN_AREA = "status.helpfulworkers.set_pen_area";
    static final String STATUS_SET_FISHING_AREA = "status.helpfulworkers.set_fishing_area";
    static final String STATUS_SET_WORK_CORNERS = "status.helpfulworkers.set_work_corners";
    static final String STATUS_NO_REACHABLE_WORK = "status.helpfulworkers.no_reachable_work";
    static final String STATUS_CHOOSE_ROLE = "status.helpfulworkers.choose_role";
    static final String STATUS_FOREST_QUIET = "status.helpfulworkers.forest_quiet";
    static final String STATUS_SEARCHING_FOREST = "status.helpfulworkers.searching_forest";
    static final String STATUS_BONE_MEALED_SAPLING = "status.helpfulworkers.bone_mealed_sapling";
    static final String STATUS_AREA_TOO_LARGE = "status.helpfulworkers.area_too_large";
    static final String STATUS_TREE_CHUNK_UNLOADED = "status.helpfulworkers.tree_chunk_unloaded";
    static final String STATUS_NEED_AXE = "status.helpfulworkers.need_axe";
    static final String STATUS_FELLED_TREE = "status.helpfulworkers.felled_tree";
    static final String STATUS_MINE_CHUNK_UNLOADED = "status.helpfulworkers.mine_chunk_unloaded";
    static final String STATUS_NO_TORCHES = "status.helpfulworkers.no_torches";
    static final String STATUS_OUTPUT_CHUNK_UNLOADED = "status.helpfulworkers.output_chunk_unloaded";
    static final String STATUS_RETURNING_LOOT = "status.helpfulworkers.returning_loot";
    static final String STATUS_OUTPUT_CHEST_MISSING = "status.helpfulworkers.output_chest_missing";
    static final String STATUS_OUTPUT_STORAGE_FULL = "status.helpfulworkers.output_storage_full";
    static final String STATUS_DROPPED_OFF_LOOT = "status.helpfulworkers.dropped_off_loot";
    static final String STATUS_PAUSED_ERROR = "status.helpfulworkers.paused_error";
    static final String STATUS_GATHERED = "status.helpfulworkers.gathered";
    static final String STATUS_FULL_RETURNING = "status.helpfulworkers.full_returning";
    static final String STATUS_PICKED_SWEET_BERRIES = "status.helpfulworkers.picked_sweet_berries";
    static final String STATUS_PICKED_GLOW_BERRIES = "status.helpfulworkers.picked_glow_berries";
    static final String STATUS_HARVESTED_COCOA = "status.helpfulworkers.harvested_cocoa";
    static final String STATUS_LAID_QUARRY_FLOOR = "status.helpfulworkers.laid_quarry_floor";
    static final String STATUS_APPLIED_BONE_MEAL = "status.helpfulworkers.applied_bone_meal";
    static final String STATUS_HARVESTED_FRUIT = "status.helpfulworkers.harvested_fruit";
    static final String STATUS_HARVESTED_NETHER_WART = "status.helpfulworkers.harvested_nether_wart";
    static final String STATUS_SKIPPING_HAZARD = "status.helpfulworkers.skipping_hazard";
    static final String STATUS_SKIPPING_LAVA = "status.helpfulworkers.skipping_lava";
    static final String STATUS_CUT = "status.helpfulworkers.cut";
    static final String STATUS_QUARRIED = "status.helpfulworkers.quarried";
    static final String STATUS_PICKAXE_TOO_WEAK = "status.helpfulworkers.pickaxe_too_weak";
    static final String STATUS_HARVESTED_REPLANTED = "status.helpfulworkers.harvested_replanted";
    static final String STATUS_TILLED = "status.helpfulworkers.tilled";
    static final String STATUS_PLANTED = "status.helpfulworkers.planted";
    static final String STATUS_PLANTED_NETHER_WART = "status.helpfulworkers.planted_nether_wart";
    static final String STATUS_FELLING_TREE = "status.helpfulworkers.felling_tree";
    static final String STATUS_CHOPPING = "status.helpfulworkers.chopping";
    static final String STATUS_MINED = "status.helpfulworkers.mined";
    static final String STATUS_NO_MINE_PLAN = "status.helpfulworkers.no_mine_plan";
    static final String STATUS_MINE_PLAN_COMPLETE = "status.helpfulworkers.mine_plan_complete";
    static final String STATUS_MINED_ORE = "status.helpfulworkers.mined_ore";
    static final String STATUS_WORKING_ON = "status.helpfulworkers.working_on";
    static final String STATUS_MISSING = "status.helpfulworkers.missing";
    static final String STATUS_BAG_FULL_CARRY = "status.helpfulworkers.bag_full_carry";
    static final String STATUS_SUPPLY_HAS_NO = "status.helpfulworkers.supply_has_no";
    static final String STATUS_PICKED_UP = "status.helpfulworkers.picked_up";
    static final String STATUS_HERD_THINNED = "status.helpfulworkers.herd_thinned";
    static final String STATUS_COLLECTED_EGGS = "status.helpfulworkers.collected_eggs";
    static final String STATUS_SHEARED_SHEEP = "status.helpfulworkers.sheared_sheep";
    static final String STATUS_MILKED_COW = "status.helpfulworkers.milked_cow";
    static final String STATUS_PEN_QUIET = "status.helpfulworkers.pen_quiet";
    static final String STATUS_NEED_BOTTLES_OR_SHEARS = "status.helpfulworkers.need_bottles_or_shears";
    static final String STATUS_NO_SHORE_WATER = "status.helpfulworkers.no_shore_water";
    static final String STATUS_MISSING_FISHING_ROD = "status.helpfulworkers.missing_fishing_rod";
    static final String STATUS_WAITING_RECAST = "status.helpfulworkers.waiting_recast";
    static final String STATUS_ASSIGN_FURNACES = "status.helpfulworkers.assign_furnaces";
    static final String STATUS_ASSIGN_SUPPLY = "status.helpfulworkers.assign_supply";
    static final String STATUS_FURNACES_IDLE = "status.helpfulworkers.furnaces_idle";
    static final String STATUS_ASSIGN_WORKSHOP = "status.helpfulworkers.assign_workshop";
    static final String STATUS_WORKSHOP_STOCKED = "status.helpfulworkers.workshop_stocked";
    static final String STATUS_REPAIRING = "status.helpfulworkers.repairing";
    static final String STATUS_REPAIRED = "status.helpfulworkers.repaired";
    static final String STATUS_BRINGING_TOOL = "status.helpfulworkers.bringing_tool";
    static final String STATUS_DELIVERED_TOOL = "status.helpfulworkers.delivered_tool";
    static final String STATUS_NEED_TOOL_MATERIALS = "status.helpfulworkers.need_tool_materials";
    static final String STATUS_NEED_REPAIR_MATERIAL = "status.helpfulworkers.need_repair_material";
    static final String STATUS_ASSIGN_STONECUTTER = "status.helpfulworkers.assign_stonecutter";
    static final String STATUS_CUTTING = "status.helpfulworkers.cutting";
    static final String STATUS_NEED_STONE = "status.helpfulworkers.need_stone";
    static final String STATUS_CANT_CUT = "status.helpfulworkers.cant_cut";
    static final String STATUS_ORDERS_DONE = "status.helpfulworkers.orders_done";
    static final String STATUS_NO_ORDERS = "status.helpfulworkers.no_orders";
    static final String STATUS_ASSIGN_SMOKER = "status.helpfulworkers.assign_smoker";
    static final String STATUS_FEEDING = "status.helpfulworkers.feeding";
    static final String STATUS_FED = "status.helpfulworkers.fed";
    static final String STATUS_NO_COOKED_FOOD = "status.helpfulworkers.no_cooked_food";
    static final String STATUS_PORTER_NO_WORKERS = "status.helpfulworkers.porter_no_workers";
    static final String STATUS_ASSIGN_PICKUPS = "status.helpfulworkers.assign_pickups";
    static final String STATUS_ASSIGN_DROPOFF = "status.helpfulworkers.assign_dropoff";
    static final String STATUS_ASSIGN_SUPPLY_FURNACES = "status.helpfulworkers.assign_supply_furnaces";
    static final String STATUS_ROUTE_CLEAR = "status.helpfulworkers.route_clear";
    static final String STATUS_BOTTLED_HONEY = "status.helpfulworkers.bottled_honey";
    static final String STATUS_COLLECTED_HONEYCOMB = "status.helpfulworkers.collected_honeycomb";
    static final String STATUS_CULLING = "status.helpfulworkers.culling";
    static final String STATUS_BREEDING = "status.helpfulworkers.breeding";
    static final String STATUS_CAUGHT_FISH = "status.helpfulworkers.caught_fish";
    static final String STATUS_COLLECTED = "status.helpfulworkers.collected";
    static final String STATUS_TARGET_CHUNK_UNLOADED = "status.helpfulworkers.target_chunk_unloaded";
    static final String STATUS_CANT_REACH_RETRY = "status.helpfulworkers.cant_reach_retry";
    static final String STATUS_CANT_REACH_ANOTHER_WAY = "status.helpfulworkers.cant_reach_another_way";
    static final String STATUS_CANT_REACH_PLACING_STEP = "status.helpfulworkers.cant_reach_placing_step";
    static final String STATUS_CANT_REACH_ANOTHER_SPOT = "status.helpfulworkers.cant_reach_another_spot";
    static final String STATUS_GOT_UNSTUCK = "status.helpfulworkers.got_unstuck";
    static final String STATUS_STEPPING_ASIDE = "status.helpfulworkers.stepping_aside";
    static final String STATUS_CANT_REACH_SKIPPED = "status.helpfulworkers.cant_reach_skipped";
    static final String STATUS_WORKING = "status.helpfulworkers.working";
    static final String STATUS_PAUSED_BY_OWNER = "status.helpfulworkers.paused_by_owner";
    static final String STATUS_GOING_HOME = "status.helpfulworkers.going_home";
    static final String STATUS_NO_BED = "status.helpfulworkers.no_bed";
    static final String STATUS_FLATTENING_PLOT = "status.helpfulworkers.flattening_plot";
    static final String STATUS_DIG_SITE_CLEARED = "status.helpfulworkers.dig_site_cleared";
    static final String STATUS_WAITING_DIG_SITE = "status.helpfulworkers.waiting_dig_site";
    static final String STATUS_OUTPUT_READY = "status.helpfulworkers.output_ready";
    static final String STATUS_THINNING_HERD = "status.helpfulworkers.thinning_herd";
    static final String STATUS_ASSIGNED_AS = "status.helpfulworkers.assigned_as";
    static final String STATUS_BUILDING = "status.helpfulworkers.building";
    static final String STATUS_MARK_PLOT = "status.helpfulworkers.mark_plot";
    static final String STATUS_PLOT_FLATTENED = "status.helpfulworkers.plot_flattened";
    static final String STATUS_SET_BLUEPRINT_PLACEMENT = "status.helpfulworkers.set_blueprint_placement";
    static final String STATUS_BUILD_CHUNK_UNLOADED = "status.helpfulworkers.build_chunk_unloaded";
    static final String STATUS_BLUEPRINT_COMPLETE = "status.helpfulworkers.blueprint_complete";
    static final String STATUS_CONFLICTING_BLOCK = "status.helpfulworkers.conflicting_block";
    static final String STATUS_BUILDING_PROGRESS = "status.helpfulworkers.building_progress";
    static final String STATUS_BAG_FULL_MATERIALS = "status.helpfulworkers.bag_full_materials";
    static final String STATUS_MISSING_MATERIALS = "status.helpfulworkers.missing_materials";
    static final String STATUS_CRAFTED = "status.helpfulworkers.crafted";
    static final String STATUS_SITE_CHUNK_UNLOADED = "status.helpfulworkers.site_chunk_unloaded";
    static final String STATUS_NEED_DIRT = "status.helpfulworkers.need_dirt";
    static final String STATUS_RECOVERING_HOME = "status.helpfulworkers.recovering_home";
    static final String STATUS_ALL_COLLECTED = "status.helpfulworkers.all_collected";
    static final String STATUS_GOOD_MORNING = "status.helpfulworkers.good_morning";
    static final String STATUS_BACK_TO_WORK = "status.helpfulworkers.back_to_work";
    static final String STATUS_BED_CHUNK_UNLOADED = "status.helpfulworkers.bed_chunk_unloaded";
    static final String STATUS_RESTING_AT_BED = "status.helpfulworkers.resting_at_bed";
    static final String STATUS_EATING = "status.helpfulworkers.eating";
    static final String STATUS_CAME_TO_YOU = "status.helpfulworkers.came_to_you";
    static final String STATUS_COMING_TO_YOU = "status.helpfulworkers.coming_to_you";
    static final String STATUS_INVENTORY_FULL_DROPOFF = "status.helpfulworkers.inventory_full_dropoff";
    static final String STATUS_OUT_OF_ARROWS = "status.helpfulworkers.out_of_arrows";
    static final String STATUS_RECOVERING_TIMER = "status.helpfulworkers.recovering_timer";
    static final String STATUS_WAITING_TREES = "status.helpfulworkers.waiting_trees";
    static final String STATUS_FIGHTING = "status.helpfulworkers.fighting";
    static final String STATUS_BACK_AT_BED_PAUSED = "status.helpfulworkers.back_at_bed_paused";
    static final String STATUS_PICKED_UP_MATERIALS = "status.helpfulworkers.picked_up_materials";
    static final String STATUS_SUPPLY_NO_MATERIALS = "status.helpfulworkers.supply_no_materials";
    static final String STATUS_SITE_FLATTENED = "status.helpfulworkers.site_flattened";
    static final String STATUS_SITE_CLEARED = "status.helpfulworkers.site_cleared";
    static final String STATUS_FLATTENING_SITE = "status.helpfulworkers.flattening_site";
    static final String STATUS_CLEARING_SITE = "status.helpfulworkers.clearing_site";
    static final String STATUS_DIGGING_DOWN = "status.helpfulworkers.digging_down";
    static final String STATUS_LOADED_FURNACE = "status.helpfulworkers.loaded_furnace";
    static final String STATUS_FUELLED_FURNACE = "status.helpfulworkers.fuelled_furnace";
    static final String STATUS_PICKED_UP_FROM_CHEST = "status.helpfulworkers.picked_up_from_chest";
    static final String STATUS_FOLLOWING = "status.helpfulworkers.following";
    static final String STATUS_GUARDING = "status.helpfulworkers.guarding";
    static final String STATUS_HOLDING = "status.helpfulworkers.holding";
    static final String STATUS_TRAVELING_TO = "status.helpfulworkers.traveling_to";

    // Kits
    static final String KIT_REUSABLE = "item.helpfulworkers.kit.reusable";

    // Dialogue pages (keys under gui.helpfulworkers.dialogue)
    static final String D_BACK = "gui.helpfulworkers.dialogue.back";
    static final String D_PREVIOUS = "gui.helpfulworkers.dialogue.previous";
    static final String D_NEXT = "gui.helpfulworkers.dialogue.next";
    static final String D_CANCEL = "gui.helpfulworkers.dialogue.cancel";
    static final String D_CLEAR = "gui.helpfulworkers.dialogue.clear";
    static final String D_ANY = "gui.helpfulworkers.dialogue.any";
    static final String D_UP = "gui.helpfulworkers.dialogue.up";
    static final String D_DOWN = "gui.helpfulworkers.dialogue.down";
    static final String D_TITLE = "gui.helpfulworkers.dialogue.title";
    static final String D_SEARCH = "gui.helpfulworkers.dialogue.search";
    static final String D_NAME = "gui.helpfulworkers.dialogue.name";
    static final String MAIN_WORK = "gui.helpfulworkers.dialogue.main.work";
    static final String MAIN_PROTECT = "gui.helpfulworkers.dialogue.main.protect";
    static final String MAIN_CROPS = "gui.helpfulworkers.dialogue.main.crops";
    static final String MAIN_GATHER = "gui.helpfulworkers.dialogue.main.gather";
    static final String MAIN_ROUTE = "gui.helpfulworkers.dialogue.main.route";
    static final String MAIN_RANCH = "gui.helpfulworkers.dialogue.main.ranch";
    static final String MAIN_CULL = "gui.helpfulworkers.dialogue.main.cull";
    static final String MAIN_REQUESTS = "gui.helpfulworkers.dialogue.main.requests";
    static final String MAIN_WHERE = "gui.helpfulworkers.dialogue.main.where";
    static final String MAIN_OUTLINE = "gui.helpfulworkers.dialogue.main.outline";
    static final String MAIN_EQUIP = "gui.helpfulworkers.dialogue.main.equip";
    static final String MAIN_EQUIP_SHORT = "gui.helpfulworkers.dialogue.main.equip_short";
    static final String MAIN_RECIPE = "gui.helpfulworkers.dialogue.main.recipe";
    static final String MAIN_HOUSE = "gui.helpfulworkers.dialogue.main.house";
    static final String MAIN_BLUEPRINTS = "gui.helpfulworkers.dialogue.main.blueprints";
    static final String MAIN_BYE = "gui.helpfulworkers.dialogue.main.bye";
    static final String MAIN_SITE_BUILD = "gui.helpfulworkers.dialogue.main.site_build";
    static final String MAIN_SITE_MANAGE = "gui.helpfulworkers.dialogue.main.site_manage";
    static final String MAIN_SITE_JOIN = "gui.helpfulworkers.dialogue.main.site_join";
    static final String MAIN_SITE_TIP = "gui.helpfulworkers.dialogue.main.site_tip";
    static final String MAIN_APPEARANCE = "gui.helpfulworkers.dialogue.main.appearance";
    static final String WORK_SITE_MANAGE = "gui.helpfulworkers.dialogue.work.site_manage";
    static final String WORK_SITE_TIP = "gui.helpfulworkers.dialogue.work.site_tip";
    static final String LOC_SITE = "gui.helpfulworkers.dialogue.loc.site";
    static final String BUILD_SITES = "gui.helpfulworkers.dialogue.build.sites";
    static final String BUILD_SITES_TIP = "gui.helpfulworkers.dialogue.build.sites_tip";
    static final String WORK_PAUSE = "gui.helpfulworkers.dialogue.work.pause";
    static final String WORK_START = "gui.helpfulworkers.dialogue.work.start";
    static final String WORK_HOME = "gui.helpfulworkers.dialogue.work.home";
    static final String WORK_CANCEL_DIG = "gui.helpfulworkers.dialogue.work.cancel_dig";
    static final String WORK_REDRAW_DIG = "gui.helpfulworkers.dialogue.work.redraw_dig";
    static final String WORK_CLEAR_AREA = "gui.helpfulworkers.dialogue.work.clear_area";
    static final String WORK_REDRAW_AREA = "gui.helpfulworkers.dialogue.work.redraw_area";
    static final String WORK_CHANGE_JOB = "gui.helpfulworkers.dialogue.work.change_job";
    static final String DEPTH_BEDROCK = "gui.helpfulworkers.dialogue.work.depth_bedrock";
    static final String DEPTH_BLOCKS = "gui.helpfulworkers.dialogue.work.depth_blocks";
    static final String DEPTH_SET = "gui.helpfulworkers.dialogue.work.depth_set";
    static final String TARGET_Y_SET = "gui.helpfulworkers.dialogue.work.target_y_set";
    static final String PROTECT_FOLLOW = "gui.helpfulworkers.dialogue.protect.follow";
    static final String PROTECT_GUARD = "gui.helpfulworkers.dialogue.protect.guard";
    static final String PROTECT_STAY = "gui.helpfulworkers.dialogue.protect.stay";
    static final String BONEMEAL = "gui.helpfulworkers.dialogue.crops.bonemeal";
    static final String RANCH_SHEEP = "gui.helpfulworkers.dialogue.ranch.sheep";
    static final String RANCH_COW = "gui.helpfulworkers.dialogue.ranch.cow";
    static final String RANCH_PIG = "gui.helpfulworkers.dialogue.ranch.pig";
    static final String RANCH_CHICKEN = "gui.helpfulworkers.dialogue.ranch.chicken";
    static final String RANCH_BREED = "gui.helpfulworkers.dialogue.ranch.breed";
    static final String RANCH_SHEAR = "gui.helpfulworkers.dialogue.ranch.shear";
    static final String RANCH_MILK = "gui.helpfulworkers.dialogue.ranch.milk";
    static final String RANCH_EGGS = "gui.helpfulworkers.dialogue.ranch.eggs";
    static final String RANCH_HONEY = "gui.helpfulworkers.dialogue.ranch.honey";
    static final String ROUTE_ANYTHING = "gui.helpfulworkers.dialogue.route.anything";
    static final String ROUTE_CHEST_TIP = "gui.helpfulworkers.dialogue.route.chest_tip";
    static final String ROUTE_CLEAR_TIP = "gui.helpfulworkers.dialogue.route.clear_tip";
    static final String ROUTE_KEEP = "gui.helpfulworkers.dialogue.route.keep";
    static final String ROUTE_KEEP_TIP = "gui.helpfulworkers.dialogue.route.keep_tip";
    static final String ROUTE_REMOVE_TIP = "gui.helpfulworkers.dialogue.route.remove_tip";
    static final String ROUTE_ADD = "gui.helpfulworkers.dialogue.route.add";
    static final String ROUTE_NONE = "gui.helpfulworkers.dialogue.route.none";
    static final String ROUTE_VISIT = "gui.helpfulworkers.dialogue.route.visit";
    static final String REQ_COLLECTED_TIP = "gui.helpfulworkers.dialogue.requests.collected_tip";
    static final String REQ_ADD = "gui.helpfulworkers.dialogue.requests.add";
    static final String REQ_ADD_TIP = "gui.helpfulworkers.dialogue.requests.add_tip";
    static final String REQ_STOP = "gui.helpfulworkers.dialogue.requests.stop";
    static final String REQ_CLEAR = "gui.helpfulworkers.dialogue.requests.clear";
    static final String REQ_NOTE = "gui.helpfulworkers.dialogue.requests.note";
    static final String CULL_SWORD = "gui.helpfulworkers.dialogue.cull.sword";
    static final String CULL_NO_SWORD = "gui.helpfulworkers.dialogue.cull.no_sword";
    static final String CULL_TEXT = "gui.helpfulworkers.dialogue.cull.text";
    static final String TARGET_Y_OPTION = "gui.helpfulworkers.dialogue.target_y.option";
    static final String LOC_GUARD = "gui.helpfulworkers.dialogue.loc.guard";
    static final String LOC_BED = "gui.helpfulworkers.dialogue.loc.bed";
    static final String LOC_FURNACE = "gui.helpfulworkers.dialogue.loc.furnace";
    static final String LOC_CLEAR_FURNACES = "gui.helpfulworkers.dialogue.loc.clear_furnaces";
    static final String LOC_SUPPLY = "gui.helpfulworkers.dialogue.loc.supply";
    static final String LOC_OUTPUT = "gui.helpfulworkers.dialogue.loc.output";
    static final String LOC_PICKUP = "gui.helpfulworkers.dialogue.loc.pickup";
    static final String LOC_CLEAR_PICKUPS = "gui.helpfulworkers.dialogue.loc.clear_pickups";
    static final String LOC_DROPOFF = "gui.helpfulworkers.dialogue.loc.dropoff";
    static final String LOC_SUPPLY_FURNACE = "gui.helpfulworkers.dialogue.loc.supply_furnace";
    static final String LOC_FEED = "gui.helpfulworkers.dialogue.loc.feed";
    static final String LOC_TABLE = "gui.helpfulworkers.dialogue.loc.table";
    static final String LOC_ANVIL = "gui.helpfulworkers.dialogue.loc.anvil";
    static final String LOC_GRINDSTONE = "gui.helpfulworkers.dialogue.loc.grindstone";
    static final String LOC_STONECUTTER = "gui.helpfulworkers.dialogue.loc.stonecutter";
    // Toolsmith stock page
    static final String TOOLS_KEEP = "gui.helpfulworkers.dialogue.tools.keep";
    static final String TOOLS_TIP = "gui.helpfulworkers.dialogue.tools.tip";
    static final String TOOLS_INFO = "gui.helpfulworkers.dialogue.tools.info";
    // Stonemason orders page
    static final String ORDERS_ADD = "gui.helpfulworkers.dialogue.orders.add";
    static final String ORDERS_ADD_TIP = "gui.helpfulworkers.dialogue.orders.add_tip";
    static final String ORDERS_CUT_TIP = "gui.helpfulworkers.dialogue.orders.cut_tip";
    static final String ORDERS_AUTO = "gui.helpfulworkers.dialogue.orders.auto";
    static final String ORDERS_SUPPLY_BUILDERS = "gui.helpfulworkers.dialogue.orders.supply_builders";
    static final String ORDERS_CLEAR = "gui.helpfulworkers.dialogue.orders.clear";
    static final String ORDERS_NOTE = "gui.helpfulworkers.dialogue.orders.note";
    // Cook options and courier modes
    static final String COOK_FEED = "gui.helpfulworkers.dialogue.cook.feed";
    static final String COOK_MEALS = "gui.helpfulworkers.dialogue.cook.meals";
    static final String COOK_NOTE = "gui.helpfulworkers.dialogue.cook.note";
    static final String LOC_SMOKER = "gui.helpfulworkers.dialogue.loc.smoker";
    static final String ROUTE_MODE_ZONE = "gui.helpfulworkers.dialogue.route.mode_zone";
    static final String ROUTE_MODE_PORTER = "gui.helpfulworkers.dialogue.route.mode_porter";
    static final String ROUTE_MODE_TIP = "gui.helpfulworkers.dialogue.route.mode_tip";
    static final String ROUTE_PORTER_NOTE = "gui.helpfulworkers.dialogue.route.porter_note";
    static final String LOC_CHANGE = "gui.helpfulworkers.dialogue.loc.change";
    static final String LOC_ASSIGN = "gui.helpfulworkers.dialogue.loc.assign";
    static final String LOC_NOT_ASSIGNED = "gui.helpfulworkers.dialogue.loc.not_assigned";
    static final String LOC_LEGEND = "gui.helpfulworkers.dialogue.loc.legend";
    static final String RECIPES_HINT = "gui.helpfulworkers.dialogue.recipes.hint";
    static final String RECIPES_CANNOT = "gui.helpfulworkers.dialogue.recipes.cannot";
    static final String RECIPES_LEGEND = "gui.helpfulworkers.dialogue.recipes.legend";
    static final String BUILD_STYLE = "gui.helpfulworkers.dialogue.build.style";
    static final String BUILD_GROUND_FLATTEN = "gui.helpfulworkers.dialogue.build.ground_flatten";
    static final String BUILD_GROUND_OFF = "gui.helpfulworkers.dialogue.build.ground_off";
    static final String BUILD_GROUND_CLEAR = "gui.helpfulworkers.dialogue.build.ground_clear";
    static final String BUILD_TIP_FLATTEN = "gui.helpfulworkers.dialogue.build.tip_flatten";
    static final String BUILD_TIP_OFF = "gui.helpfulworkers.dialogue.build.tip_off";
    static final String BUILD_TIP_CLEAR = "gui.helpfulworkers.dialogue.build.tip_clear";
    static final String BUILD_FLATTENING = "gui.helpfulworkers.dialogue.build.flattening";
    static final String BUILD_PLOT_MARKED = "gui.helpfulworkers.dialogue.build.plot_marked";
    static final String BUILD_MARK_PLOT = "gui.helpfulworkers.dialogue.build.mark_plot";
    static final String BUILD_REMAKE = "gui.helpfulworkers.dialogue.build.remake";
    static final String BUILD_CLEAR_PLOT = "gui.helpfulworkers.dialogue.build.clear_plot";
    static final String BUILD_FLATTEN = "gui.helpfulworkers.dialogue.build.flatten";
    static final String BUILD_FLATTEN_TIP = "gui.helpfulworkers.dialogue.build.flatten_tip";
    static final String BUILD_AUTO = "gui.helpfulworkers.dialogue.build.auto";
    static final String BUILD_TEXT = "gui.helpfulworkers.dialogue.build.text";
    static final String BP_NAME = "gui.helpfulworkers.dialogue.bp.name";
    static final String BP_STEP1 = "gui.helpfulworkers.dialogue.bp.step1";
    static final String BP_STEP2 = "gui.helpfulworkers.dialogue.bp.step2";
    static final String BP_MATERIALS = "gui.helpfulworkers.dialogue.bp.materials";
    static final String BP_PREVIEW = "gui.helpfulworkers.dialogue.bp.preview";
    static final String BP_STEP3 = "gui.helpfulworkers.dialogue.bp.step3";
    static final String BP_CLEAR_SPOT = "gui.helpfulworkers.dialogue.bp.clear_spot";
    static final String BP_STEP4 = "gui.helpfulworkers.dialogue.bp.step4";
    static final String BP_LOAD = "gui.helpfulworkers.dialogue.bp.load";
    static final String BP_NEED_NAME = "gui.helpfulworkers.dialogue.bp.need_name";
    static final String BP_NEED_AREA = "gui.helpfulworkers.dialogue.bp.need_area";
    static final String BP_NEED_CUSTOM = "gui.helpfulworkers.dialogue.bp.need_custom";
    static final String BP_NEED_SPOT = "gui.helpfulworkers.dialogue.bp.need_spot";
    static final String BP_TEXT = "gui.helpfulworkers.dialogue.bp.text";
    static final String DEPTH_OPT_BEDROCK = "gui.helpfulworkers.dialogue.depth.bedrock";
    static final String DEPTH_OPT_BLOCKS = "gui.helpfulworkers.dialogue.depth.blocks";
    static final String DEPTH_TEXT = "gui.helpfulworkers.dialogue.depth.text";
    static final String DEPTH_CHOOSE = "gui.helpfulworkers.dialogue.depth.choose";
    static final String CONFIRM_ROLE = "gui.helpfulworkers.dialogue.confirm.role";
    static final String CONFIRM_ROLE_TEXT = "gui.helpfulworkers.dialogue.confirm.role_text";
    static final String CONFIRM_OVERWRITE = "gui.helpfulworkers.dialogue.confirm.overwrite";
    static final String CONFIRM_OVERWRITE_TEXT = "gui.helpfulworkers.dialogue.confirm.overwrite_text";
    static final String ROLE_TEXT = "gui.helpfulworkers.dialogue.role.text";
    static final String DUTY_WORKING = "gui.helpfulworkers.dialogue.duty.working";
    static final String DUTY_PAUSED = "gui.helpfulworkers.dialogue.duty.paused";

    // Server-side toasts and status lines that travel as translatable components
    static final String TOAST_NOT_FOUND = "gui.helpfulworkers.toast.not_found";
    static final String TOAST_NOT_LOADED = "gui.helpfulworkers.toast.not_loaded";
    static final String GREETING_FORMAT = "gui.helpfulworkers.greeting.format";
    static final String STATUS_OUT_OF_SPACE = "gui.helpfulworkers.status.out_of_space";

    static final List<String> MINER_MODES = List.of("excavate", "branches", "terraform", "quarry", "staircase", "strip", "colony");
    static final List<String> CROP_IDS = List.of("sugar_cane", "bamboo", "kelp", "sweet_berries", "cactus", "cocoa", "glow_berries");
    static final List<String> BIOMES = List.of("plains", "desert", "savanna", "taiga", "snowy");
    static final List<String> AREA_ROLES = List.of("forester", "farmer", "miner", "rancher", "fisher", "gatherer");

    static String modeKey(String mode) { return "gui.helpfulworkers.dialogue.mode." + mode; }
    static String toolKey(String tool) { return "gui.helpfulworkers.dialogue.tools." + tool; }

    static String modeTipKey(String mode) { return modeKey(mode) + ".tip"; }

    /** Null for an id without a label; the caller then shows the raw id. */
    static String cropKey(String id) { return CROP_IDS.contains(id) ? "gui.helpfulworkers.dialogue.crop." + id : null; }

    static String biomeKey(String biome) { return "gui.helpfulworkers.dialogue.biome." + biome; }

    static String areaKey(String role) { return "gui.helpfulworkers.dialogue.area." + (AREA_ROLES.contains(role) ? role : "work"); }

    /** What the worker says at the top of the dialogue, in character. */
    static String askKey(String role) { return "gui.helpfulworkers.dialogue.ask." + (WorkerActions.isRole(role) ? role : "idle"); }

    /** One of the three greeting lines a role says when the owner walks up (n is 1 to 3). */
    // Town hall, levels, personalities (0.9.0)
    static final String HALL_NO_ROOM = "block.helpfulworkers.town_hall.no_room";
    static final String HALL_RECRUITED = "block.helpfulworkers.town_hall.recruited";
    static final String HALL_ASSIGNED = "block.helpfulworkers.town_hall.assigned";
    static final String STATUS_GOING_TO_HALL = "status.helpfulworkers.going_to_hall";
    static final String LEVEL_UP = "gui.helpfulworkers.level_up";
    static final String CHATTER_FORMAT = "gui.helpfulworkers.chatter.format";
    static final String LEVEL_SHORT = "gui.helpfulworkers.level_short";
    static final String INV_LEVEL = "gui.helpfulworkers.inventory.level";
    static final String INV_LEVEL_MAX = "gui.helpfulworkers.inventory.level_max";
    static final String INV_KEPT = "gui.helpfulworkers.inventory.kept";
    static final int TRAIT_LINES = 3;
    static final int CHATTER_LINES = 6;

    static String traitKey(String trait) {
        return "trait.helpfulworkers." + trait;
    }

    static String traitLineKey(String trait, int n) {
        return "line.helpfulworkers.trait." + trait + "." + n;
    }

    static String chatterKey(int n) {
        return "line.helpfulworkers.chatter." + n;
    }

    static String lineKey(String role, int n) {
        return "line.helpfulworkers." + (WorkerActions.isRole(role) ? role : "idle") + "." + n;
    }

    static String roleKey(String role) {
        return "role.helpfulworkers." + (role == null || role.isBlank() || !WorkerActions.isRole(role) ? "idle" : role);
    }

    static String kitDescKey(String role) {
        return "item.helpfulworkers.kit." + role + ".desc";
    }

    /** Every key the UI may ask for: the constants above plus one role name and kit line per role. */
    static List<String> all() {
        List<String> keys = new ArrayList<>();
        for (Field field : Lang.class.getDeclaredFields()) {
            if (field.getType() != String.class || !Modifier.isStatic(field.getModifiers())) continue;
            try { keys.add((String) field.get(null)); } catch (IllegalAccessException ignored) {}
        }
        keys.add(roleKey("idle"));
        for (String role : WorkerActions.ROLES) {
            keys.add(roleKey(role));
            keys.add(kitDescKey(role));
        }
        for (String mode : MINER_MODES) { keys.add(modeKey(mode)); keys.add(modeTipKey(mode)); }
        for (String tool : ToolsmithJobs.TOOL_KEYS) keys.add(toolKey(tool));
        for (String id : CROP_IDS) keys.add(cropKey(id));
        for (String biome : BIOMES) keys.add(biomeKey(biome));
        for (String role : AREA_ROLES) keys.add(areaKey(role));
        keys.add(areaKey("work"));
        keys.add(askKey("idle"));
        for (String role : WorkerActions.ROLES) keys.add(askKey(role));
        for (String role : WorkerActions.ROLES) for (int n = 1; n <= 3; n++) keys.add(lineKey(role, n));
        for (int n = 1; n <= 3; n++) keys.add(lineKey("idle", n));
        for (String trait : Worker.TRAITS) {
            keys.add(traitKey(trait));
            for (int n = 1; n <= TRAIT_LINES; n++) keys.add(traitLineKey(trait, n));
        }
        for (int n = 1; n <= CHATTER_LINES; n++) keys.add(chatterKey(n));
        for (String path : WorkerConfig.CONFIG_PATHS) keys.add(HelpfulWorkers.ID + ".configuration." + path);
        for (String path : SiteSettings.configPaths()) keys.add(HelpfulWorkers.ID + ".configuration." + path);
        return keys;
    }
}
