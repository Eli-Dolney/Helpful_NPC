package com.example.helpfulworkers;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;

public final class Worker extends PathfinderMob implements RangedAttackMob {
    /** Short status line, state colour and role, synced to clients for the overhead text and outfit. */
    static final EntityDataAccessor<String> DATA_STATUS = SynchedEntityData.defineId(Worker.class, EntityDataSerializers.STRING);
    static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(Worker.class, EntityDataSerializers.BYTE);
    static final EntityDataAccessor<String> DATA_ROLE = SynchedEntityData.defineId(Worker.class, EntityDataSerializers.STRING);
    static final EntityDataAccessor<Byte> DATA_LEVEL = SynchedEntityData.defineId(Worker.class, EntityDataSerializers.BYTE);
    static final EntityDataAccessor<Integer> DATA_XP = SynchedEntityData.defineId(Worker.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<String> DATA_TRAIT = SynchedEntityData.defineId(Worker.class, EntityDataSerializers.STRING);
    /** "item_id=count,..." of the bag reserves, for the equipment screen tooltip. */
    static final EntityDataAccessor<String> DATA_RESERVES = SynchedEntityData.defineId(Worker.class, EntityDataSerializers.STRING);

    /** Experience needed for levels 1..5. */
    static final int[] LEVEL_XP = {0, 100, 300, 700, 1500};
    static final List<String> TRAITS = List.of("cheerful", "grumpy", "chatty", "quiet", "curious");
    private static final net.minecraft.resources.ResourceLocation LEVEL_HEALTH_ID =
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(HelpfulWorkers.ID, "level_health");
    /** Ticks between two chats of the same worker with a workmate. */
    static final long CHATTER_COOLDOWN = 2400;
    static final byte STATE_IDLE = 0, STATE_WORKING = 1, STATE_BLOCKED = 2, STATE_SLEEPING = 3;
    static final int STATUS_LINE_MAX = 40;

    private static final net.minecraft.network.syncher.EntityDataAccessor<String> APPEARANCE_NAME = net.minecraft.network.syncher.SynchedEntityData.defineId(Worker.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> APPEARANCE_SKIN = net.minecraft.network.syncher.SynchedEntityData.defineId(Worker.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> APPEARANCE_SLIM = net.minecraft.network.syncher.SynchedEntityData.defineId(Worker.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<java.util.Optional<UUID>> APPEARANCE_OWNER = net.minecraft.network.syncher.SynchedEntityData.defineId(Worker.class, net.minecraft.network.syncher.EntityDataSerializers.OPTIONAL_UUID);
    UUID skinOwner() { return entityData.get(APPEARANCE_OWNER).orElse(null); }
    String personalName() { return entityData.get(APPEARANCE_NAME); }
    String skinKey() { return entityData.get(APPEARANCE_SKIN); }
    boolean slimSkin() { return entityData.get(APPEARANCE_SLIM); }
    void setAppearance(String key, boolean slim) {
        entityData.set(APPEARANCE_SKIN, WorkerAppearance.validSkin(key) ? key : "auto");
        entityData.set(APPEARANCE_SLIM, slim);
    }
    UUID owner;
    UUID siteId, constructionId, supplyDeliveryWorker;
    BlockPos supplyDelivery;
    String role = "idle";
    String mode = "excavate";
    String status = "Waiting for an assignment";
    String baseName = "";
    /** follow | guard | stay — used by knight/archer. */
    String companionMode = "follow";
    boolean working;
    boolean useBoneMeal;
    boolean needsUnload;
    BlockPos first, second, bed, supply, output, craftingTable, buildOrigin;
    /** Town hall this worker gathers at when it has no bed (recall, go home). */
    BlockPos hall;
    /** Personality: one of {@link #TRAITS}; empty until picked. Sets greeting lines and voice pitch. */
    String trait = "";
    /** Experience from work; {@link #workerLevel()} is derived from it. */
    int xp;
    long lastChatter;
    /** Item id → how many to keep in the bag when depositing (owner-set or remembered from supply trips). */
    final java.util.Map<String, Integer> reserves = new java.util.HashMap<>();
    /** Extra assignment lists for smelter furnaces / courier pickups (NBT-saved). */
    final java.util.List<BlockPos> furnaces = new ArrayList<>();
    final java.util.List<BlockPos> pickups = new ArrayList<>();
    /** Courier: per pickup chest (packed pos) the item ids it may take (empty = anything) and how many to leave behind. */
    final java.util.Map<Long, List<String>> pickupFilters = new java.util.HashMap<>();
    final java.util.Map<Long, Integer> pickupKeep = new java.util.HashMap<>();
    /** Courier: which pickup chest the next round starts at (round-robin). */
    int courierCursor;
    /** Smelter/courier: item ids fetched for the furnaces; deposit leaves these in the bag. */
    final List<String> furnaceHaul = new ArrayList<>();
    /** The request board: "collect {count} of {item}", with how many have been collected so far. Saved. */
    record Request(String item, int count, int done) {
        boolean finished() { return done >= count; }
    }
    final List<Request> requests = new ArrayList<>();
    /** Pause once every request is met; off, the worker keeps collecting past the count. */
    boolean requestsStop = true;
    static final int MAX_REQUESTS = 4;
    /** The job's own words, before the request progress is appended. */
    String statusBase = status;
    /** What clients get: {@link StatusText} code for the full status line (base + request progress). */
    String statusCode = status;
    /** The job's own part of {@link #statusCode}, kept so request progress can be re-appended. */
    String statusBaseCode = status;
    /** Enabled ranch species: sheep, cow, pig, chicken. */
    final List<String> ranchAnimals = new ArrayList<>(List.of("sheep", "cow", "pig", "chicken"));
    boolean ranchShear = true;
    boolean ranchMilk = true;
    boolean ranchEggs = true;
    boolean ranchBreed = true;
    /** Bottle or shear full hives that have a campfire smoking them from below. */
    boolean ranchHoney = true;
    /** One-shot cull-to-2 in progress. */
    boolean culling;
    ItemStack borrowedSword = ItemStack.EMPTY;
    UUID swordLender;
    ItemStack stashedMainHand = ItemStack.EMPTY;
    /** 0 = clicked height; >0 terraform depth; -1 bedrock. Also reused as mine target offset context. */
    int digDepth;
    int targetY = 16;
    long lastUnloadGameTime;
    long nextWorkTick;
    int idleLevel;
    long recoverUntil;
    /** Set by a knock-out while working; the worker picks its job back up once recovered. */
    boolean resumeAfterRecovery;
    int regenCounter;
    long idleUntil;
    /** Game time of the last drop-off at the output chest (0 = never); shown on the roster. */
    long lastDepositTime;
    boolean statusDirty;
    boolean equipDirty = true;
    String blueprint = "";
    ListTag blueprintBlocks = new ListTag();
    ListTag blueprintLibrary = new ListTag();
    int buildRotation;
    /** Ground preparation before building: {@link SitePrep#NONE}, {@link SitePrep#CLEAR} or {@link SitePrep#FLATTEN}. */
    String sitePrep = SitePrep.CLEAR;
    boolean sitePrepDone;
    int prepCursor;
    /** Flatten the marked plot without building anything on it. */
    boolean prepOnly;
    java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> prepPlanned;
    BlockPos prepPlannedOrigin;
    int prepPlannedRotation;
    int prepPlannedSize;
    final SimpleContainer bag = new SimpleContainer(27);
    final List<String> recipes = new ArrayList<>();
    final List<String> enabledCrops = new ArrayList<>(List.of("wheat", "carrots", "potatoes", "beetroots"));
    int scanCursor;
    /** Large-area scan: highest terrain seen in the top layer of this pass (MAX = not known yet), and its running max. */
    transient int scanPeak = Integer.MAX_VALUE, scanPeakAcc = Integer.MIN_VALUE;
    /** Forester columns searched since the forest was last found empty (large areas keep looking instead of resting). */
    transient int treeColumnsScanned;
    BlockPos target;
    /** Routes, stuck recovery and errands (fetch from supply, craft at table); not saved. */
    final WorkerNav nav = new WorkerNav(this);
    /** Swing, cracks and hit sounds while a block is being worked; not saved. */
    final WorkAnimator animator = new WorkAnimator(this);
    /** Logs of the tree being felled, bottom first; saved so a reload finishes the tree. */
    final List<BlockPos> fellQueue = new ArrayList<>();
    BlockPos fellBase;
    String fellSapling = "";
    long lastGreet;
    boolean greetedSleep;
    /** Teleport fallbacks this worker has taken since it was loaded (tests and tuning). */
    int hops;
    /** Remote "does the supply chest hold X" answers, cached for 10 s so nobody walks for nothing. */
    final java.util.Map<String, Jobs.Peek> supplyPeek = new java.util.HashMap<>();
    /** Block positions this worker recently failed to reach, mapped to the game time they can be retried. */
    final java.util.Map<Long, Long> unreachable = new java.util.HashMap<>();
    /** Cached mine plan steps for staircase/strip/colony modes. */
    List<MinePlanner.Step> minePlan;
    int minePlanIndex;
    /** Forester trunk cache: long packed BlockPos → last refresh game time. */
    final java.util.ArrayList<BlockPos> treeIndex = new java.util.ArrayList<>();
    long treeIndexRefresh;
    /** Farmer water-near column cache: packed xz → boolean. */
    final java.util.Map<Long, Boolean> waterCache = new java.util.HashMap<>();

    public Worker(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0.0f);
        bag.addListener(c -> equipDirty = true);
        setPathfindingMalus(PathType.WATER, 8.0f);
        setPathfindingMalus(PathType.WATER_BORDER, 4.0f);
        setPathfindingMalus(PathType.LAVA, -1.0f);
        // Doors are free, fences are walls, canopies are a detour, fire is a last resort.
        setPathfindingMalus(PathType.DOOR_WOOD_CLOSED, 0.0f);
        setPathfindingMalus(PathType.FENCE, -1.0f);
        setPathfindingMalus(PathType.LEAVES, 4.0f);
        setPathfindingMalus(PathType.DANGER_FIRE, 16.0f);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATUS, "");
        builder.define(DATA_STATE, STATE_IDLE);
        builder.define(DATA_ROLE, "idle");
        builder.define(DATA_LEVEL, (byte) 1);
        builder.define(DATA_XP, 0);
        builder.define(DATA_TRAIT, "");
        builder.define(DATA_RESERVES, "");
        builder.define(APPEARANCE_NAME, "Worker");
        builder.define(APPEARANCE_OWNER, java.util.Optional.empty());
        builder.define(APPEARANCE_SKIN, "auto");
        builder.define(APPEARANCE_SLIM, false);
    }

    /** Push the overhead line, state colour and role to tracking clients (sends only on change). */
    void pushClientData() {
        // The code is translated (and cut to the line length) on the client.
        String line = statusCode == null ? "" : statusCode;
        if (!line.equals(entityData.get(DATA_STATUS))) entityData.set(DATA_STATUS, line);
        byte state = clientState();
        if (state != entityData.get(DATA_STATE)) {
            // A short "hmm" the moment the worker gets stuck on something.
            if (state == STATE_BLOCKED && level() instanceof ServerLevel) playSound(WorkerSounds.HMM.get(), 0.4f, voicePitch());
            entityData.set(DATA_STATE, state);
        }
        byte lvl = (byte) workerLevel();
        if (lvl != entityData.get(DATA_LEVEL)) entityData.set(DATA_LEVEL, lvl);
        if (xp != entityData.get(DATA_XP)) entityData.set(DATA_XP, xp);
        String t = trait == null ? "" : trait;
        if (!t.equals(entityData.get(DATA_TRAIT))) entityData.set(DATA_TRAIT, t);
        String kept = reservesLine();
        if (!kept.equals(entityData.get(DATA_RESERVES))) entityData.set(DATA_RESERVES, kept);
        String r = role == null || role.isBlank() ? "idle" : role;
        if (!r.equals(entityData.get(DATA_ROLE))) entityData.set(DATA_ROLE, r);
    }

    byte clientState() {
        if (isSleeping()) return STATE_SLEEPING;
        String s = status == null ? "" : status;
        if (s.startsWith("Missing") || s.startsWith("Need ") || s.startsWith("No ") || s.startsWith("Can't")
            || s.startsWith("Out of") || s.startsWith("Paused:") || s.startsWith("Skipping")
            || s.startsWith("Conflicting") || s.startsWith("Recovering") || s.contains("unloaded")
            || s.startsWith(Jobs.OUT_OF_SPACE.substring(0, 12))) return STATE_BLOCKED;
        return working ? STATE_WORKING : STATE_IDLE;
    }

    @Override protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanOpenDoors(true);
        navigation.setCanPassDoors(true);
        navigation.setCanFloat(true);
        return navigation;
    }

    /** Set what the worker is doing; an open request's progress ("· Request 10/64 Oak Log") rides along. */
    void setStatus(String s) {
        applyStatus(StatusText.literal(s));
    }

    /** Set a translatable status: {@code key} from en_us.json, with {@link StatusText.Name} args for block/item names. */
    void setStatusKey(String key, Object... args) {
        applyStatus(StatusText.encode(key, args));
    }

    /** Re-apply the current job status (after the request list changed). */
    void refreshStatus() {
        applyStatus(statusBaseCode);
    }

    private void applyStatus(String baseCode) {
        if (baseCode == null) baseCode = "";
        statusBaseCode = baseCode;
        statusBase = StatusText.english(baseCode);
        String code = StatusText.join(baseCode, requestSuffix());
        String english = StatusText.english(code);
        if (!english.equals(status) || !code.equals(statusCode)) {
            status = english;
            statusCode = code;
            statusDirty = true;
        }
    }

    // ---------------------------------------------------------------- levels, traits, reserves

    static int levelFor(int xp) {
        int level = 1;
        for (int i = 1; i < LEVEL_XP.length; i++) if (xp >= LEVEL_XP[i]) level = i + 1;
        return level;
    }

    int workerLevel() {
        return levelFor(xp);
    }

    /** XP needed for the next level, or -1 at the top level. */
    static int nextLevelXp(int xp) {
        int level = levelFor(xp);
        return level >= LEVEL_XP.length ? -1 : LEVEL_XP[level];
    }

    void gainXp(int amount) {
        if (amount <= 0) return;
        int before = workerLevel();
        xp = Math.min(Integer.MAX_VALUE - amount, xp) + amount;
        int after = workerLevel();
        if (after > before) onLevelUp(after);
    }

    private void onLevelUp(int level) {
        applyLevelHealth();
        heal(2.0f * (level - 1));
        playSound(WorkerSounds.LEVEL_UP.get(), 0.8f, 1.0f);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.2, getZ(),
                20, 0.4, 0.6, 0.4, 0.05);
            if (findOwner() instanceof ServerPlayer owner) {
                WorkerNetwork.toast(owner, Component.translatable(Lang.LEVEL_UP, getName(), level));
            }
        }
    }

    /** +2 max health per level above 1, as a fixed-id modifier (re-applied on load and level-up). */
    void applyLevelHealth() {
        var health = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (health == null) return;
        health.removeModifier(LEVEL_HEALTH_ID);
        int level = workerLevel();
        if (level > 1) {
            health.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(LEVEL_HEALTH_ID,
                2.0 * (level - 1), net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        }
    }

    void pickTraitIfMissing() {
        if (trait == null || !TRAITS.contains(trait)) trait = TRAITS.get(random.nextInt(TRAITS.size()));
    }

    float voicePitch() {
        float base = switch (trait == null ? "" : trait) {
            case "cheerful" -> 1.25f;
            case "grumpy" -> 0.8f;
            case "chatty" -> 1.15f;
            case "quiet" -> 0.95f;
            case "curious" -> 1.1f;
            default -> 1.0f;
        };
        return base + (random.nextFloat() - 0.5f) * 0.1f;
    }

    /** How many of this item stay in the bag on drop-off: the owner's/remembered reserve, else 16 saplings. */
    int reserveFor(ItemStack stack) {
        Integer r = reserves.get(ExtraJobs.itemId(stack));
        if (r != null) return r;
        return stack.is(net.minecraft.tags.ItemTags.SAPLINGS) ? 16 : 0;
    }

    private String reservesLine() {
        if (reserves.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (var e : new java.util.TreeMap<>(reserves).entrySet()) {
            if (b.length() > 0) b.append(',');
            b.append(e.getKey()).append('=').append(e.getValue());
        }
        return b.toString();
    }

    /** Two idle workers of the same owner standing close swap a line now and then; the owner hears it nearby. */
    private void maybeChatter(ServerLevel level) {
        long now = level.getGameTime();
        if (now - lastChatter < CHATTER_COOLDOWN || random.nextInt(8) != 0 || owner == null) return;
        for (Worker other : level.getEntitiesOfClass(Worker.class, getBoundingBox().inflate(4),
                w -> w != this && owner.equals(w.owner) && !w.working && now - w.lastChatter >= CHATTER_COOLDOWN)) {
            lastChatter = now;
            other.lastChatter = now;
            getLookControl().setLookAt(other, 30f, 30f);
            other.getLookControl().setLookAt(this, 30f, 30f);
            playSound(WorkerSounds.CHATTER.get(), 0.5f, voicePitch());
            if (findOwner() instanceof ServerPlayer p && p.distanceToSqr(this) < 16 * 16) {
                WorkerNetwork.toast(p, Component.translatable(Lang.CHATTER_FORMAT, getName(),
                    Component.translatable(Lang.chatterKey(1 + random.nextInt(Lang.CHATTER_LINES)), other.getName())));
            }
            return;
        }
    }

    /** Request progress as a status code segment ("" without requests). */
    String requestSuffix() {
        if (requests.isEmpty()) return "";
        for (Request r : requests) {
            if (!r.finished()) return StatusText.encode(Lang.STATUS_REQUEST, r.done(), r.count(), requestItemName(r.item()));
        }
        return StatusText.encode(Lang.STATUS_REQUESTS_DONE);
    }

    static Object requestItemName(String itemId) {
        var key = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        var item = key == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        return item == null ? itemId : StatusText.name(item);
    }

    static String requestName(String itemId) {
        var key = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        var item = key == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        return item == null ? itemId : new ItemStack(item).getHoverName().getString();
    }

    /** Count a collected stack against the open request for that item; pause when the board is done and asked to. */
    void noteRequestProgress(ItemStack stack) {
        if (requests.isEmpty() || stack.isEmpty()) return;
        String id = ExtraJobs.itemId(stack);
        boolean changed = false;
        for (int i = 0; i < requests.size(); i++) {
            Request r = requests.get(i);
            if (r.finished() || !r.item().equals(id)) continue;
            requests.set(i, new Request(r.item(), r.count(), Math.min(r.count(), r.done() + stack.getCount())));
            changed = true;
            break;
        }
        if (!changed) return;
        boolean allDone = requests.stream().allMatch(Request::finished);
        if (allDone && requestsStop && working) {
            working = false;
            target = null;
            getNavigation().stop();
            setStatusKey(Lang.STATUS_ALL_COLLECTED);
            rebuildGoals();
            WorkerActions.sync(this);
            return;
        }
        refreshStatus();
    }

    void markWorkFound() {
        idleLevel = 0;
        if (level() instanceof ServerLevel sl) {
            nextWorkTick = sl.getGameTime() + WorkerConfig.workInterval();
        }
    }

    void markIdle() {
        // A pending supply or crafting trip is work: keep the one-second cadence until it is done.
        if (nav.hasErrand()) { markWorkFound(); return; }
        idleLevel = Math.min(idleLevel + 1, 3);
        int[] backoff = {100, 300, 600, 1200}; // 5s, 15s, 30s, 60s
        if (level() instanceof ServerLevel sl) {
            nextWorkTick = sl.getGameTime() + backoff[idleLevel];
        }
    }

    /** Flags synced to the dialogue UI for checkbox state. */
    List<String> collectFlags() {
        List<String> flags = new ArrayList<>();
        if (siteId != null) flags.add("site");
        for (String crop : enabledCrops) flags.add("crop:" + crop);
        if (useBoneMeal) flags.add("bonemeal");
        for (String animal : ranchAnimals) flags.add("rancher:" + animal);
        for (int i = 0; i < requests.size(); i++) {
            Request r = requests.get(i);
            flags.add("req:" + i + "|" + r.item() + "|" + r.done() + "|" + r.count());
        }
        if (requestsStop) flags.add("reqstop");
        if (ranchBreed) flags.add("breed");
        if (ranchShear) flags.add("shear");
        if (ranchMilk) flags.add("milk");
        if (ranchEggs) flags.add("eggs");
        if (ranchHoney) flags.add("honey");
        if (culling) flags.add("culling");
        flags.add("siteprep:" + (sitePrep == null ? SitePrep.CLEAR : sitePrep));
        if (prepOnly) flags.add("flattening");
        flags.add("level:" + workerLevel() + "|" + xp + "|" + nextLevelXp(xp));
        if (trait != null && !trait.isEmpty()) flags.add("trait:" + trait);
        for (int i = 0; i < pickups.size(); i++) {
            BlockPos p = pickups.get(i);
            flags.add("pickup:" + i + "|" + p.getX() + "," + p.getY() + "," + p.getZ() + "|"
                + pickupKeep.getOrDefault(p.asLong(), 0) + "|"
                + String.join(",", pickupFilters.getOrDefault(p.asLong(), List.of())));
        }
        return flags;
    }

    public void setOwner(UUID owner) { this.owner = owner; entityData.set(APPEARANCE_OWNER, java.util.Optional.ofNullable(owner)); }
    public boolean owns(Player player) { return owner != null && owner.equals(player.getUUID()); }
    @Override public boolean removeWhenFarAway(double distanceToClosestPlayer) { return false; }

    void setBaseName(String name) {
        this.baseName = name == null ? "" : name.trim();
        refreshDisplayName();
    }

    void refreshDisplayName() {
        String base = baseName == null || baseName.isBlank() ? "Worker" : baseName;
        // The role now lives on the overhead status line drawn by WorkerRenderer.
        entityData.set(APPEARANCE_NAME, base);
        setCustomName(Component.literal(base));
        setCustomNameVisible(true);
        pushClientData();
    }

    static String stripRoleSuffix(String name) {
        if (name == null || name.isBlank()) return "Worker";
        return name.replaceAll(" \\[[^\\]]*\\]$", "").replaceAll(" \\([^)]*\\)$", "").trim();
    }

    void rebuildGoals() {
        goalSelector.removeAllGoals(g -> true);
        targetSelector.removeAllGoals(g -> true);
        goalSelector.addGoal(0, new FloatGoal(this));
        // Every role opens wooden doors on its path and closes them behind itself.
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        if (WorkerActions.isCombatRole(role)) {
            if ("archer".equals(role)) {
                goalSelector.addGoal(2, new RangedBowAttackGoal<>(this, 1.0, 20, 15.0f));
            } else {
                goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
            }
            goalSelector.addGoal(3, new CompanionGoal(this));
            targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
            targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
            targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                living -> !(living instanceof Creeper) || "archer".equals(role)));
        } else {
            // Lightweight flee: check once via goal only when monsters are close is expensive;
            // keep AvoidEntityGoal but drop look-around while working (rebuilt when role/work changes).
            goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Monster.class, 8.0f, 1.1, 1.25));
        }
        if (!working || WorkerActions.isCombatRole(role)) {
            goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6));
            goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        }
        if (!working && !WorkerActions.isCombatRole(role)) {
            // Off duty: potter about near the bed and look at workmates instead of standing frozen.
            if (bed != null) restrictTo(bed, 8);
            else if (hall != null) restrictTo(hall, 10);
            else clearRestriction();
            // Off-duty pottering never interrupts a walk with a purpose (errand, recall, fetch).
            goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6) {
                @Override public boolean canUse() {
                    return !nav.walkedRecently() && super.canUse();
                }
            });
            goalSelector.addGoal(8, new LookAtPlayerGoal(this, Worker.class, 3));
        } else {
            clearRestriction();
        }
    }

    @Override protected void registerGoals() {
        rebuildGoals();
    }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!owns(player)) {
            if (!level().isClientSide) player.displayClientMessage(Component.literal("This worker belongs to someone else."), false);
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof RoleKitItem) {
            return held.getItem().interactLivingEntity(held, player, this, hand);
        }
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer && player.isShiftKeyDown()
            && WorkerSessions.getClipboard(serverPlayer) != null) {
            WorkerSessions.clearClipboard(serverPlayer);
            player.displayClientMessage(Component.literal("Assignment cancelled"), false);
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown() && held.isEmpty()) {
            if (player instanceof ServerPlayer serverPlayer) {
                ActionResult result = WorkerActions.toggleWork(serverPlayer, this);
                player.displayClientMessage(result.message(), true);
            }
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer serverPlayer && owns(serverPlayer)) {
            WorkerNetwork.openDialogue(serverPlayer, this, "");
            WorkerNetwork.sendAreaOutline(serverPlayer, this, 200);
        }
        return InteractionResult.CONSUME;
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        animator.tick(level);
        // Stagger: only every 20 ticks, offset by entity id so workers don't sync.
        if ((tickCount + getId()) % 20 != 0) return;
        long t0 = System.nanoTime();
        try {
            tickWork(level);
        } finally {
            WorkerPerf.record(role, System.nanoTime() - t0);
        }
    }

    void tickWork(ServerLevel level) {
        if (isSleeping() && !level.isNight()) {
            stopSleeping();
            swing(InteractionHand.MAIN_HAND, true);
            setStatusKey(Lang.STATUS_GOOD_MORNING);
            greetedSleep = false;
        }
        if (WorkerSessions.isSuspended(this)) {
            SiteJobs.suspend(this);
            getNavigation().stop();
            animator.clear(level);
            pushClientData();
            return;
        }
        greetNearbyOwner(level);
        if (recoverUntil > level.getGameTime()) {
            SiteJobs.suspend(this);
            getNavigation().stop();
            setStatusKey(Lang.STATUS_RECOVERING_TIMER, (recoverUntil - level.getGameTime()) / 20);
            flushStatus();
            return;
        }
        if (recoverUntil != 0) {
            recoverUntil = 0;
            if (resumeAfterRecovery) {
                resumeAfterRecovery = false;
                working = true;
                target = null;
                setStatusKey(Lang.STATUS_BACK_TO_WORK);
                rebuildGoals();
            }
        }
        tryEatAndHeal(level);
        WorkerEquip.tick(level, this);
        if (WorkerActions.isCombatRole(role)) {
            // Gear fetched from the supply chest: walk it before falling back in with the owner.
            if (nav.hasErrand()) { nav.tickErrand(); flushStatus(); return; }
            if (siteId != null) {
                var site = SiteData.get(level).sites.get(siteId);
                if (site == null || !getUUID().equals(site.worker) || !owner.equals(site.owner)) { SiteActions.release(level, this); return; }
                if (site.paused || !working || !level.hasChunkAt(site.core()) || !level.getBlockState(site.core()).is(SiteBlocks.CORE.get())) {
                    getNavigation().stop(); setTarget(null); setStatus("Guard site paused or unavailable"); flushStatus(); return;
                }
            } else if (!working) working = true;
            updateCombatStatus();
            flushStatus();
            return;
        }
        if (!working) {
            animator.clear(level);
            // No bed or hall to potter around: stay near the spot where work stopped instead of drifting off.
            if (bed == null && hall == null && !hasRestriction() && !WorkerActions.isCombatRole(role)) restrictTo(blockPosition(), 6);
            maybeChatter(level);
            SiteJobs.suspend(this);
            if (needsUnload && output == null && bed != null && level.isLoaded(bed)) {
                if (!approachWithin(bed, 2, 2)) setStatusKey(Lang.STATUS_FULL_RETURNING);
                else { getNavigation().stop(); setStatusKey(Lang.STATUS_FULL); }
            } else if (nav.hasErrand()) {
                nav.tickErrand();
            }
            flushStatus();
            return;
        }
        if (level.getGameTime() < nextWorkTick) {
            // Errands keep walking through idle back-off so a fetch never crawls.
            if (nav.hasErrand()) nav.tickErrand();
            flushStatus();
            return;
        }
        if (level.isNight() && bed != null) {
            animator.clear(level);
            if (!level.isLoaded(bed)) { setStatusKey(Lang.STATUS_BED_CHUNK_UNLOADED); flushStatus(); return; }
            // Knock off for the day: drop the haul at the output chest before heading home.
            if (output != null && level.isLoaded(output) && Jobs.hasDepositableLoot(this) && !greetedSleep) {
                if (!Jobs.depositPublic(level, this)) { flushStatus(); return; }
                greetedSleep = true;
            }
            SiteJobs.suspend(this);
            if (!level.isLoaded(bed)) { setStatus("Bed chunk unloaded"); flushStatus(); return; }
            if (!approachWithin(bed, 2, 2)) {
                if (status.startsWith("Traveling")) setStatusKey(Lang.STATUS_GOING_HOME);
            } else {
                getNavigation().stop();
                if (!isSleeping()) startSleeping(bed);
                setStatusKey(Lang.STATUS_RESTING_AT_BED);
            }
            flushStatus();
            return;
        }
        if (siteId == null && idleUntil > level.getGameTime() && "forester".equals(role)) {
            if (nav.hasErrand()) nav.tickErrand();
            else setStatusKey(Lang.STATUS_WAITING_TREES, (idleUntil - level.getGameTime()) / 20);
            flushStatus();
            return;
        }
        Jobs.tick(level, this);
        flushStatus();
    }

    void flushStatus() {
        if (statusDirty) {
            statusDirty = false;
            WorkerActions.sync(this);
        }
        pushClientData();
    }

    /** Say hello (once a minute) to the owner when they come up close and look at the worker. */
    private void greetNearbyOwner(ServerLevel level) {
        if (level.getGameTime() - lastGreet < 1200) return;
        Player owner = findOwner();
        if (owner == null || !(owner instanceof ServerPlayer player)) return;
        if (distanceToSqr(owner) > 4 * 4 || !owner.hasLineOfSight(this)) return;
        var toMe = position().subtract(owner.position()).normalize();
        if (owner.getViewVector(1.0f).dot(toMe) < 0.8) return;
        lastGreet = level.getGameTime();
        getLookControl().setLookAt(owner, 30f, 30f);
        WorkerNetwork.toast(player, Component.translatable(Lang.GREETING_FORMAT, getName(), WorkerLines.greeting(this, random)));
        playSound(WorkerSounds.GREET.get(), 0.6f, voicePitch());
    }

    private void updateCombatStatus() {
        if (getTarget() != null) {
            setStatusKey(Lang.STATUS_FIGHTING, getTarget().getName().getString());
            return;
        }
        setStatusKey(switch (companionMode == null ? "follow" : companionMode) {
            case "guard" -> Lang.STATUS_GUARDING;
            case "stay" -> Lang.STATUS_HOLDING;
            default -> Lang.STATUS_FOLLOWING;
        });
    }

    private void tryEatAndHeal(ServerLevel level) {
        if (getHealth() >= getMaxHealth() - 0.5f) return;
        // Called once per work tick (every 20 game ticks); heal half a heart every other call.
        if (++regenCounter % 2 == 0 && getHealth() < getMaxHealth()) {
            heal(0.5f);
        }
        if (getHealth() > getMaxHealth() * 0.5f) return;
        for (int i = 0; i < bag.getContainerSize(); i++) {
            ItemStack stack = bag.getItem(i);
            FoodProperties food = stack.getFoodProperties(this);
            if (food == null) continue;
            stack.shrink(1);
            heal(Math.max(2f, food.nutrition()));
            playSound(SoundEvents.GENERIC_EAT, 0.6f, 1.0f);
            setStatusKey(Lang.STATUS_EATING);
            return;
        }
    }

    Player findOwner() {
        if (owner == null || !(level() instanceof ServerLevel level)) return null;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        return player != null && player.level() == level ? player : null;
    }

    boolean approach(BlockPos pos) {
        return approachWithin(pos, 3, 3);
    }

    /**
     * Walk until within {@code horizontal} blocks sideways and {@code vertical} blocks up/down of pos.
     * Called once per second. See {@link WorkerNav} for stuck recovery; a pending errand (supply
     * fetch, crafting trip) is walked first and makes this return false until it is done.
     */
    boolean approachWithin(BlockPos pos, double horizontal, double vertical) {
        // Site and site-construction workers use the site router (visible stand spots, automatic
        // retry, never a teleport); everyone else uses WorkerNav with errands and stuck recovery.
        if (siteId != null || constructionId != null) return SiteNavigation.approach(this, pos, horizontal, vertical);
        return nav.approachWithin(pos, horizontal, vertical, null);
    }

    /** Approach a job block from a free stand spot beside it; true once it is within vanilla reach. */
    boolean approachStand(BlockPos target) {
        if (siteId != null || constructionId != null) return SiteNavigation.approach(this, target, 3, 3);
        return nav.approachStand(target);
    }

    boolean isUnreachable(BlockPos pos) {
        Long until = unreachable.get(pos.asLong());
        if (until == null) return false;
        if (level().getGameTime() >= until) { unreachable.remove(pos.asLong()); return false; }
        return true;
    }

    /** Teleport fallback beside pos, limited to 16 blocks sideways and 4 up/down; see {@link WorkerNav#hopNear}. */
    boolean hopNear(BlockPos pos) {
        return nav.hopNear(pos);
    }

    static boolean canStandAt(Level level, BlockPos p) {
        if (!level.isLoaded(p)) return false;
        BlockPos below = p.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) return false;
        if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
        if (!level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) return false;
        return level.getFluidState(p).isEmpty() && level.getFluidState(p.above()).isEmpty()
            && level.getFluidState(below).isEmpty();
    }

    /** Stop work and head for the owner, teleporting when they are too far to path to. */
    void comeTo(Player player) {
        working = false;
        target = null;
        getNavigation().stop();
        if (distanceToSqr(player) > 40 * 40) {
            teleportTo(player.getX(), player.getY(), player.getZ());
            setStatusKey(Lang.STATUS_CAME_TO_YOU);
        } else {
            getNavigation().moveTo(player, 1.1);
            setStatusKey(Lang.STATUS_COMING_TO_YOU);
        }
    }

    boolean contains(BlockPos pos) {
        AreaBounds box = areaBounds();
        if (box == null) return false;
        return pos.getX() >= box.minX && pos.getX() <= box.maxX
            && pos.getY() >= box.minY && pos.getY() <= box.maxY
            && pos.getZ() >= box.minZ && pos.getZ() <= box.maxZ;
    }

    AreaBounds areaBounds() {
        if (first == null || second == null) return null;
        int minX = Math.min(first.getX(), second.getX());
        int maxX = Math.max(first.getX(), second.getX());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxZ = Math.max(first.getZ(), second.getZ());
        int top = Math.max(first.getY(), second.getY());
        int bottom = Math.min(first.getY(), second.getY());
        if (digDepth > 0) bottom = top - digDepth + 1;
        else if (digDepth < 0) bottom = level().getMinBuildHeight();
        if (bottom < level().getMinBuildHeight()) bottom = level().getMinBuildHeight();
        return new AreaBounds(minX, bottom, minZ, maxX, top, maxZ);
    }

    /** Work areas up to 512 x 512 columns and 64M blocks (a 300 x 300 forest, a mountain to level, a pit to bedrock). */
    static final int MAX_AREA_SIDE = 512;
    static final long MAX_AREA_VOLUME = 64_000_000L;
    static final long LARGE_AREA = 32768;

    record AreaBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int dx() { return maxX - minX + 1; }
        int dy() { return maxY - minY + 1; }
        int dz() { return maxZ - minZ + 1; }
        long volume() { return (long) dx() * dy() * dz(); }
        /** Above the old 32768-block limit: scanned with heightmap skips instead of block by block. */
        boolean large() { return volume() > LARGE_AREA; }
    }

    void collect(ItemStack stack) {
        noteRequestProgress(stack);
        gainXp(1);
        ItemStack remaining = bag.addItem(stack);
        if (!remaining.isEmpty()) {
            spawnAtLocation(remaining);
            needsUnload = true;
            if (output != null) setStatusKey(Lang.STATUS_INVENTORY_FULL_DROPOFF);
            else {
                setStatusKey(Lang.STATUS_FULL);
                working = false;
                target = null;
                getNavigation().stop();
            }
        }
    }

    @Override public void performRangedAttack(LivingEntity target, float distanceFactor) {
        ItemStack bow = getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, item -> item instanceof BowItem));
        if (bow.isEmpty()) bow = getMainHandItem();
        ItemStack arrow = findArrow();
        if (arrow.isEmpty()) { setStatusKey(Lang.STATUS_OUT_OF_ARROWS); return; }
        AbstractArrow projectile = ProjectileUtil.getMobArrow(this, arrow.copyWithCount(1), distanceFactor, bow);
        double dx = target.getX() - getX();
        double dy = target.getY(0.333) - projectile.getY();
        double dz = target.getZ() - getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        projectile.shoot(dx, dy + horiz * 0.2, dz, 1.6f, 8 - level().getDifficulty().getId() * 2);
        playSound(SoundEvents.SKELETON_SHOOT, 1.0f, 1.0f / (getRandom().nextFloat() * 0.4f + 0.8f));
        level().addFreshEntity(projectile);
        arrow.shrink(1);
    }

    private ItemStack findArrow() {
        for (int i = 0; i < bag.getContainerSize(); i++) {
            ItemStack stack = bag.getItem(i);
            if (stack.is(Items.ARROW) || stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.TIPPED_ARROW)) return stack;
        }
        if (getOffhandItem().is(Items.ARROW)) return getOffhandItem();
        return ItemStack.EMPTY;
    }

    @Override public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) {
        return weapon instanceof BowItem && !findArrow().isEmpty();
    }

    @Override protected void dropCustomDeathLoot(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (int i = 0; i < bag.getContainerSize(); i++) {
            ItemStack stack = bag.getItem(i);
            if (!stack.isEmpty()) spawnAtLocation(stack.copy());
        }
        bag.clearContent();
        WorkerSessions.cancelClipboardForWorker(getId());
    }

    @Override public void remove(RemovalReason reason) {
        WorkerSessions.cancelClipboardForWorker(getId());
        super.remove(reason);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        if (siteId != null) tag.putUUID("SiteId", siteId);
        if (supplyDelivery != null) tag.putLong("SupplyDelivery", supplyDelivery.asLong());
        if (supplyDeliveryWorker != null) tag.putUUID("SupplyDeliveryWorker",supplyDeliveryWorker);
        if (constructionId != null) tag.putUUID("ConstructionId", constructionId);
        if (baseName != null && !baseName.isBlank()) tag.putString("BaseName", baseName);
        tag.putString("WorkerSkin", skinKey());
        tag.putBoolean("WorkerSkinSlim", slimSkin());
        tag.putString("Role", role); tag.putString("Mode", mode); tag.putString("Status", status);
        tag.putString("CompanionMode", companionMode == null ? "follow" : companionMode);
        tag.putBoolean("Working", working); tag.putBoolean("UseBoneMeal", useBoneMeal);
        tag.putInt("Cursor", scanCursor); tag.putString("Blueprint", blueprint);
        tag.putInt("DigDepth", digDepth); tag.putInt("TargetY", targetY);
        tag.putLong("LastUnload", lastUnloadGameTime);
        tag.putLong("RecoverUntil", recoverUntil);
        tag.putLong("IdleUntil", idleUntil);
        tag.putLong("LastDeposit", lastDepositTime);
        tag.putInt("Xp", xp);
        tag.putString("Trait", trait == null ? "" : trait);
        savePos(tag, "Hall", hall);
        if (!reserves.isEmpty()) {
            CompoundTag kept = new CompoundTag();
            reserves.forEach(kept::putInt);
            tag.put("Reserves", kept);
        }
        tag.putBoolean("RanchShear", ranchShear);
        tag.putBoolean("RanchMilk", ranchMilk);
        tag.putBoolean("RanchEggs", ranchEggs);
        tag.putBoolean("RanchBreed", ranchBreed);
        tag.putBoolean("RanchHoney", ranchHoney);
        tag.putBoolean("RequestsStop", requestsStop);
        if (!requests.isEmpty()) {
            ListTag board = new ListTag();
            for (Request r : requests) {
                CompoundTag e = new CompoundTag();
                e.putString("Item", r.item());
                e.putInt("Count", r.count());
                e.putInt("Done", r.done());
                board.add(e);
            }
            tag.put("Requests", board);
        }
        tag.putBoolean("Culling", culling);
        if (swordLender != null) tag.putUUID("SwordLender", swordLender);
        if (!borrowedSword.isEmpty()) tag.put("BorrowedSword", borrowedSword.save(registryAccess()));
        if (!stashedMainHand.isEmpty()) tag.put("StashedMainHand", stashedMainHand.save(registryAccess()));
        tag.put("BlueprintBlocks", blueprintBlocks.copy());
        tag.put("BlueprintLibrary", blueprintLibrary.copy());
        tag.putInt("BuildRotation", buildRotation);
        tag.putString("SitePrep", sitePrep == null ? SitePrep.CLEAR : sitePrep);
        tag.putBoolean("SitePrepDone", sitePrepDone);
        tag.putInt("PrepCursor", prepCursor);
        tag.putBoolean("PrepOnly", prepOnly);
        tag.putBoolean("ResumeAfterRecovery", resumeAfterRecovery);
        if (!fellQueue.isEmpty()) {
            ListTag logs = new ListTag();
            for (BlockPos p : fellQueue) logs.add(net.minecraft.nbt.LongTag.valueOf(p.asLong()));
            tag.put("FellQueue", logs);
            if (fellBase != null) tag.putLong("FellBase", fellBase.asLong());
            tag.putString("FellSapling", fellSapling == null ? "" : fellSapling);
        }
        tag.putInt("IdleLevel", idleLevel);
        if (minePlan != null && !minePlan.isEmpty()) {
            ListTag plan = new ListTag();
            for (MinePlanner.Step step : minePlan) {
                CompoundTag e = new CompoundTag();
                e.putLong("P", step.pos().asLong());
                e.putByte("K", (byte) step.kind().ordinal());
                plan.add(e);
            }
            tag.put("MinePlan", plan);
            tag.putInt("MinePlanIndex", minePlanIndex);
        }
        savePos(tag, "First", first); savePos(tag, "Second", second); savePos(tag, "Bed", bed);
        savePos(tag, "Supply", supply); savePos(tag, "Output", output);
        savePos(tag, "CraftingTable", craftingTable); savePos(tag, "BuildOrigin", buildOrigin);
        ListTag furnaceTag = new ListTag();
        for (BlockPos p : furnaces) { CompoundTag e = new CompoundTag(); e.putLong("P", p.asLong()); furnaceTag.add(e); }
        tag.put("Furnaces", furnaceTag);
        ListTag pickupTag = new ListTag();
        for (BlockPos p : pickups) {
            CompoundTag e = new CompoundTag();
            e.putLong("P", p.asLong());
            List<String> filter = pickupFilters.get(p.asLong());
            if (filter != null && !filter.isEmpty()) {
                ListTag ids = new ListTag();
                for (String id : filter) ids.add(StringTag.valueOf(id));
                e.put("Filter", ids);
            }
            int keep = pickupKeep.getOrDefault(p.asLong(), 0);
            if (keep > 0) e.putInt("Keep", keep);
            pickupTag.add(e);
        }
        tag.put("Pickups", pickupTag);
        tag.putInt("CourierCursor", courierCursor);
        if (!furnaceHaul.isEmpty()) {
            ListTag haul = new ListTag();
            for (String id : furnaceHaul) haul.add(StringTag.valueOf(id));
            tag.put("FurnaceHaul", haul);
        }
        ListTag items = new ListTag();
        for (int i = 0; i < bag.getContainerSize(); i++) if (!bag.getItem(i).isEmpty()) {
            CompoundTag entry = new CompoundTag(); entry.putByte("Slot", (byte) i);
            entry.put("Item", bag.getItem(i).save(registryAccess())); items.add(entry);
        }
        tag.put("Bag", items);
        ListTag learned = new ListTag();
        for (String id : recipes) learned.add(StringTag.valueOf(id));
        tag.put("Recipes", learned);
        ListTag crops = new ListTag();
        for (String crop : enabledCrops) crops.add(StringTag.valueOf(crop));
        tag.put("EnabledCrops", crops);
        ListTag ranch = new ListTag();
        for (String a : ranchAnimals) ranch.add(StringTag.valueOf(a));
        tag.put("RanchAnimals", ranch);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) setOwner(tag.getUUID("Owner"));
        setAppearance(tag.contains("WorkerSkin") ? tag.getString("WorkerSkin") : "auto", tag.getBoolean("WorkerSkinSlim"));
        siteId = tag.hasUUID("SiteId") ? tag.getUUID("SiteId") : null;
        supplyDelivery = tag.contains("SupplyDelivery") ? BlockPos.of(tag.getLong("SupplyDelivery")) : null;
        supplyDeliveryWorker = tag.hasUUID("SupplyDeliveryWorker") ? tag.getUUID("SupplyDeliveryWorker") : null;
        constructionId = tag.hasUUID("ConstructionId") ? tag.getUUID("ConstructionId") : null;
        role = tag.contains("Role") && !tag.getString("Role").isEmpty() ? tag.getString("Role") : "idle";
        mode = tag.contains("Mode") && !tag.getString("Mode").isEmpty() ? tag.getString("Mode") : "excavate";
        status = tag.contains("Status") && !tag.getString("Status").isEmpty() ? tag.getString("Status") : "Waiting for an assignment";
        companionMode = tag.contains("CompanionMode") ? tag.getString("CompanionMode") : "follow";
        if (tag.contains("BaseName") && !tag.getString("BaseName").isEmpty()) baseName = tag.getString("BaseName");
        else baseName = stripRoleSuffix(hasCustomName() ? getCustomName().getString() : "Worker");
        working = tag.getBoolean("Working");
        useBoneMeal = tag.getBoolean("UseBoneMeal");
        scanCursor = tag.getInt("Cursor");
        blueprint = tag.getString("Blueprint");
        digDepth = tag.contains("DigDepth") ? tag.getInt("DigDepth") : 0;
        targetY = tag.contains("TargetY") ? tag.getInt("TargetY") : 16;
        lastUnloadGameTime = tag.getLong("LastUnload");
        recoverUntil = tag.getLong("RecoverUntil");
        idleUntil = tag.getLong("IdleUntil");
        lastDepositTime = tag.getLong("LastDeposit");
        xp = Math.max(0, tag.getInt("Xp"));
        trait = tag.getString("Trait");
        pickTraitIfMissing();
        hall = readPos(tag, "Hall");
        reserves.clear();
        CompoundTag kept = tag.getCompound("Reserves");
        for (String id : kept.getAllKeys()) if (kept.getInt(id) > 0) reserves.put(id, kept.getInt(id));
        applyLevelHealth();
        ranchShear = !tag.contains("RanchShear") || tag.getBoolean("RanchShear");
        ranchMilk = !tag.contains("RanchMilk") || tag.getBoolean("RanchMilk");
        ranchEggs = !tag.contains("RanchEggs") || tag.getBoolean("RanchEggs");
        ranchBreed = !tag.contains("RanchBreed") || tag.getBoolean("RanchBreed");
        ranchHoney = !tag.contains("RanchHoney") || tag.getBoolean("RanchHoney");
        requestsStop = !tag.contains("RequestsStop") || tag.getBoolean("RequestsStop");
        requests.clear();
        ListTag board = tag.getList("Requests", Tag.TAG_COMPOUND);
        for (int i = 0; i < board.size() && requests.size() < MAX_REQUESTS; i++) {
            CompoundTag e = board.getCompound(i);
            if (e.getString("Item").isEmpty() || e.getInt("Count") <= 0) continue;
            requests.add(new Request(e.getString("Item"), e.getInt("Count"), Math.max(0, e.getInt("Done"))));
        }
        statusBase = status;
        statusBaseCode = StatusText.literal(status);
        statusCode = statusBaseCode;
        culling = tag.getBoolean("Culling");
        swordLender = tag.hasUUID("SwordLender") ? tag.getUUID("SwordLender") : null;
        borrowedSword = tag.contains("BorrowedSword")
            ? ItemStack.parseOptional(registryAccess(), tag.getCompound("BorrowedSword")) : ItemStack.EMPTY;
        stashedMainHand = tag.contains("StashedMainHand")
            ? ItemStack.parseOptional(registryAccess(), tag.getCompound("StashedMainHand")) : ItemStack.EMPTY;
        blueprintBlocks = tag.getList("BlueprintBlocks", Tag.TAG_COMPOUND).copy();
        blueprintLibrary = tag.getList("BlueprintLibrary", Tag.TAG_COMPOUND).copy();
        buildRotation = tag.getInt("BuildRotation");
        sitePrep = tag.contains("SitePrep") && SitePrep.isMode(tag.getString("SitePrep"))
            ? tag.getString("SitePrep") : SitePrep.CLEAR;
        // Older saves: a build already in progress keeps going without re-clearing its site.
        sitePrepDone = tag.contains("SitePrepDone") ? tag.getBoolean("SitePrepDone") : scanCursor > 0;
        prepCursor = tag.getInt("PrepCursor");
        prepOnly = tag.getBoolean("PrepOnly");
        resumeAfterRecovery = tag.getBoolean("ResumeAfterRecovery");
        fellQueue.clear();
        fellBase = null;
        fellSapling = "";
        if (tag.contains("FellQueue", Tag.TAG_LIST)) {
            ListTag logs = tag.getList("FellQueue", Tag.TAG_LONG);
            for (int i = 0; i < logs.size(); i++) fellQueue.add(BlockPos.of(((net.minecraft.nbt.LongTag) logs.get(i)).getAsLong()));
            fellBase = tag.contains("FellBase") ? BlockPos.of(tag.getLong("FellBase")) : null;
            fellSapling = tag.getString("FellSapling");
        }
        idleLevel = Math.min(3, Math.max(0, tag.getInt("IdleLevel")));
        minePlan = null;
        minePlanIndex = 0;
        if (tag.contains("MinePlan", Tag.TAG_LIST)) {
            ListTag plan = tag.getList("MinePlan", Tag.TAG_COMPOUND);
            List<MinePlanner.Step> steps = new ArrayList<>(plan.size());
            MinePlanner.Kind[] kinds = MinePlanner.Kind.values();
            for (int i = 0; i < plan.size(); i++) {
                CompoundTag e = plan.getCompound(i);
                int k = e.getByte("K");
                if (k < 0 || k >= kinds.length) continue;
                steps.add(new MinePlanner.Step(BlockPos.of(e.getLong("P")), kinds[k]));
            }
            if (!steps.isEmpty()) {
                minePlan = steps;
                minePlanIndex = Math.min(Math.max(0, tag.getInt("MinePlanIndex")), steps.size());
            }
        }
        first = readPos(tag, "First"); second = readPos(tag, "Second"); bed = readPos(tag, "Bed");
        supply = readPos(tag, "Supply"); output = readPos(tag, "Output");
        craftingTable = readPos(tag, "CraftingTable"); buildOrigin = readPos(tag, "BuildOrigin");
        furnaces.clear();
        ListTag furnaceTag = tag.getList("Furnaces", Tag.TAG_COMPOUND);
        for (int i = 0; i < furnaceTag.size(); i++) furnaces.add(BlockPos.of(furnaceTag.getCompound(i).getLong("P")));
        pickups.clear();
        pickupFilters.clear();
        pickupKeep.clear();
        ListTag pickupTag = tag.getList("Pickups", Tag.TAG_COMPOUND);
        for (int i = 0; i < pickupTag.size(); i++) {
            CompoundTag e = pickupTag.getCompound(i);
            BlockPos p = BlockPos.of(e.getLong("P"));
            pickups.add(p);
            if (e.contains("Filter", Tag.TAG_LIST)) {
                ListTag ids = e.getList("Filter", Tag.TAG_STRING);
                List<String> filter = new ArrayList<>();
                for (int j = 0; j < ids.size(); j++) filter.add(ids.getString(j));
                if (!filter.isEmpty()) pickupFilters.put(p.asLong(), filter);
            }
            if (e.getInt("Keep") > 0) pickupKeep.put(p.asLong(), e.getInt("Keep"));
        }
        courierCursor = tag.getInt("CourierCursor");
        furnaceHaul.clear();
        ListTag haul = tag.getList("FurnaceHaul", Tag.TAG_STRING);
        for (int i = 0; i < haul.size(); i++) furnaceHaul.add(haul.getString(i));
        bag.clearContent();
        ListTag items = tag.getList("Bag", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            int slot = entry.getByte("Slot") & 255;
            if (slot < bag.getContainerSize()) bag.setItem(slot, ItemStack.parseOptional(registryAccess(), entry.getCompound("Item")));
        }
        recipes.clear();
        ListTag learned = tag.getList("Recipes", Tag.TAG_STRING);
        for (int i = 0; i < learned.size(); i++) recipes.add(learned.getString(i));
        enabledCrops.clear();
        ListTag crops = tag.getList("EnabledCrops", Tag.TAG_STRING);
        if (crops.isEmpty()) enabledCrops.addAll(List.of("wheat", "carrots", "potatoes", "beetroots"));
        else for (int i = 0; i < crops.size(); i++) enabledCrops.add(crops.getString(i));
        ranchAnimals.clear();
        ListTag ranch = tag.getList("RanchAnimals", Tag.TAG_STRING);
        if (ranch.isEmpty()) ranchAnimals.addAll(List.of("sheep", "cow", "pig", "chicken"));
        else for (int i = 0; i < ranch.size(); i++) ranchAnimals.add(ranch.getString(i));
        refreshDisplayName();
        rebuildGoals();
    }

    private static void savePos(CompoundTag tag, String key, BlockPos pos) {
        if (pos != null) tag.putLong(key, pos.asLong());
    }

    private static BlockPos readPos(CompoundTag tag, String key) {
        return tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null;
    }

    /** Follow owner / patrol guard area / hold position. */
    static final class CompanionGoal extends Goal {
        /** Follow mode teleports only beyond this many blocks ... */
        static final double TELEPORT_DISTANCE = 48;
        /** ... and only after this long without a usable path or progress toward the owner. */
        static final int TELEPORT_STALL_TICKS = 100;

        private final Worker worker;
        private int cooldown;
        private int stallTicks;
        private double lastOwnerDistance = Double.MAX_VALUE;

        CompanionGoal(Worker worker) {
            this.worker = worker;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override public boolean canUse() {
            return WorkerActions.isCombatRole(worker.role) && worker.getTarget() == null && !worker.nav.hasErrand();
        }

        @Override public void tick() {
            if (--cooldown > 0) return;
            cooldown = 10;
            String mode = worker.companionMode == null ? "follow" : worker.companionMode;
            if ("stay".equals(mode)) {
                worker.getNavigation().stop();
                return;
            }
            if(worker.siteId!=null && worker.level() instanceof ServerLevel l) {
                var site=SiteData.get(l).sites.get(worker.siteId);
                if(site==null || site.paused || !worker.working || !site.active()) { worker.getNavigation().stop();return; }
                BlockPos post=site.template.isEmpty() ? (worker.role.equals("archer")?site.at(4,6,4):site.entrance()):site.station();
                worker.approachWithin(post,1,1); return;
            }
            if ("guard".equals(mode)) {
                Worker.AreaBounds box = worker.areaBounds();
                if (box == null) {
                    if (worker.bed != null) worker.getNavigation().moveTo(worker.bed.getX() + 0.5, worker.bed.getY(), worker.bed.getZ() + 0.5, 1);
                    return;
                }
                if (worker.level().isNight() && worker.bed != null) {
                    worker.getNavigation().moveTo(worker.bed.getX() + 0.5, worker.bed.getY(), worker.bed.getZ() + 0.5, 1);
                    return;
                }
                if (worker.getNavigation().isDone() || worker.random.nextInt(40) == 0) {
                    int x = box.minX() + worker.random.nextInt(Math.max(1, box.dx()));
                    int z = box.minZ() + worker.random.nextInt(Math.max(1, box.dz()));
                    int y = box.maxY();
                    worker.getNavigation().moveTo(x + 0.5, y, z + 0.5, 1.0);
                }
                return;
            }
            Player owner = worker.findOwner();
            if (owner == null) return;
            double dist = worker.distanceToSqr(owner);
            if (dist < 3 * 3) {
                worker.getNavigation().stop();
                stallTicks = 0;
                lastOwnerDistance = dist;
                return;
            }
            // Always try to walk first; a teleport needs the owner far away, on the ground, and
            // the worker without a usable path or any progress for 5 s.
            boolean pathed;
            if (dist > 10 * 10) pathed = worker.getNavigation().moveTo(owner, 1.15);
            else if (worker.getNavigation().isDone()) pathed = worker.getNavigation().moveTo(owner, 1.0);
            else pathed = true;
            boolean closing = dist < lastOwnerDistance - 1.0;
            lastOwnerDistance = dist;
            if (pathed && closing) stallTicks = 0;
            else stallTicks += 10;
            if (dist > TELEPORT_DISTANCE * TELEPORT_DISTANCE && owner.onGround() && stallTicks >= TELEPORT_STALL_TICKS) {
                BlockPos near = owner.blockPosition();
                if (!worker.nav.teleportBeside(near)) {
                    // Find any safe spot near owner instead of dropping into void/water.
                    for (BlockPos p : BlockPos.betweenClosed(near.offset(-3, -2, -3), near.offset(3, 2, 3))) {
                        if (Worker.canStandAt(worker.level(), p)) {
                            worker.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                            worker.hops++;
                            WorkerPerf.recordHop();
                            break;
                        }
                    }
                }
                stallTicks = 0;
                lastOwnerDistance = Double.MAX_VALUE;
            }
        }
    }

    /** Attack whoever is hurting the owner. */
    static final class OwnerHurtTargetGoal extends Goal {
        private final Worker worker;
        private LivingEntity ownerLastHurt;
        private int timestamp;

        OwnerHurtTargetGoal(Worker worker) {
            this.worker = worker;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override public boolean canUse() {
            Player owner = worker.findOwner();
            if (owner == null) return false;
            this.ownerLastHurt = owner.getLastHurtMob();
            int time = owner.getLastHurtMobTimestamp();
            return time != timestamp && ownerLastHurt != null && worker.canAttack(ownerLastHurt)
                && !(ownerLastHurt instanceof Player p && worker.owns(p));
        }

        @Override public void start() {
            worker.setTarget(ownerLastHurt);
            Player owner = worker.findOwner();
            if (owner != null) timestamp = owner.getLastHurtMobTimestamp();
            super.start();
        }
    }
}
