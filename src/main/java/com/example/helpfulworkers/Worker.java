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
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
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

public final class Worker extends PathfinderMob implements RangedAttackMob {
    UUID owner;
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
    /** Extra assignment lists for smelter furnaces / courier pickups (NBT-saved). */
    final java.util.List<BlockPos> furnaces = new ArrayList<>();
    final java.util.List<BlockPos> pickups = new ArrayList<>();
    /** Enabled ranch species: sheep, cow, pig, chicken. */
    final List<String> ranchAnimals = new ArrayList<>(List.of("sheep", "cow", "pig", "chicken"));
    boolean ranchShear = true;
    boolean ranchMilk = true;
    boolean ranchEggs = true;
    boolean ranchBreed = true;
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
    long idleUntil;
    boolean statusDirty;
    boolean equipDirty = true;
    String blueprint = "";
    ListTag blueprintBlocks = new ListTag();
    ListTag blueprintLibrary = new ListTag();
    int buildRotation;
    final SimpleContainer bag = new SimpleContainer(27);
    final List<String> recipes = new ArrayList<>();
    final List<String> enabledCrops = new ArrayList<>(List.of("wheat", "carrots", "potatoes", "beetroots"));
    int scanCursor, stuckTicks;
    BlockPos target;
    BlockPos navigationTarget;
    double lastDistance = Double.MAX_VALUE;
    int noProgressTicks;
    net.minecraft.world.phys.Vec3 lastCheckPos;
    int failedRoutes;
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
        setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, 8.0f);
        setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER_BORDER, 4.0f);
        setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.LAVA, -1.0f);
    }

    void setStatus(String s) {
        if (s == null) s = "";
        if (!s.equals(status)) {
            status = s;
            statusDirty = true;
        }
    }

    void markWorkFound() {
        idleLevel = 0;
        if (level() instanceof ServerLevel sl) {
            nextWorkTick = sl.getGameTime() + WorkerConfig.workInterval();
        }
    }

    void markIdle() {
        idleLevel = Math.min(idleLevel + 1, 3);
        int[] backoff = {100, 300, 600, 1200}; // 5s, 15s, 30s, 60s
        if (level() instanceof ServerLevel sl) {
            nextWorkTick = sl.getGameTime() + backoff[idleLevel];
        }
    }

    /** Flags synced to the dialogue UI for checkbox state. */
    List<String> collectFlags() {
        List<String> flags = new ArrayList<>();
        for (String crop : enabledCrops) flags.add("crop:" + crop);
        if (useBoneMeal) flags.add("bonemeal");
        for (String animal : ranchAnimals) flags.add("rancher:" + animal);
        if (ranchBreed) flags.add("breed");
        if (ranchShear) flags.add("shear");
        if (ranchMilk) flags.add("milk");
        if (ranchEggs) flags.add("eggs");
        if (culling) flags.add("culling");
        return flags;
    }

    public void setOwner(UUID owner) { this.owner = owner; }
    public boolean owns(Player player) { return owner != null && owner.equals(player.getUUID()); }
    @Override public boolean removeWhenFarAway(double distanceToClosestPlayer) { return false; }

    void setBaseName(String name) {
        this.baseName = name == null ? "" : name.trim();
        refreshDisplayName();
    }

    void refreshDisplayName() {
        String base = baseName == null || baseName.isBlank() ? "Worker" : baseName;
        if ("idle".equals(role) || role == null || role.isBlank()) {
            setCustomName(Component.literal(base));
        } else {
            setCustomName(Component.literal(base + " (" + WorkerActions.roleLabel(role) + ")"));
        }
        setCustomNameVisible(true);
    }

    static String stripRoleSuffix(String name) {
        if (name == null || name.isBlank()) return "Worker";
        return name.replaceAll(" \\[[^\\]]*\\]$", "").replaceAll(" \\([^)]*\\)$", "").trim();
    }

    void rebuildGoals() {
        goalSelector.removeAllGoals(g -> true);
        targetSelector.removeAllGoals(g -> true);
        goalSelector.addGoal(0, new FloatGoal(this));
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
        // Stagger: only every 20 ticks, offset by entity id so workers don't sync.
        if ((tickCount + getId()) % 20 != 0) return;
        long t0 = System.nanoTime();
        try {
            tickWork(level);
        } finally {
            WorkerPerf.record(System.nanoTime() - t0);
        }
    }

    private void tickWork(ServerLevel level) {
        if (isSleeping() && !level.isNight()) stopSleeping();
        if (WorkerSessions.isSuspended(this)) {
            getNavigation().stop();
            return;
        }
        if (recoverUntil > level.getGameTime()) {
            getNavigation().stop();
            setStatus("Recovering (" + ((recoverUntil - level.getGameTime()) / 20) + "s)");
            flushStatus();
            return;
        }
        tryEatAndHeal(level);
        WorkerEquip.tick(level, this);
        if (WorkerActions.isCombatRole(role)) {
            if (!working) working = true;
            updateCombatStatus();
            flushStatus();
            return;
        }
        if (!working) {
            if (needsUnload && output == null && bed != null && level.isLoaded(bed)) {
                if (!approachWithin(bed, 2, 2)) setStatus(Jobs.OUT_OF_SPACE + " Returning to bed.");
                else { getNavigation().stop(); setStatus(Jobs.OUT_OF_SPACE); }
            }
            flushStatus();
            return;
        }
        if (level.getGameTime() < nextWorkTick) {
            flushStatus();
            return;
        }
        if (level.isNight() && bed != null) {
            if (!level.isLoaded(bed)) { setStatus("Bed chunk unloaded"); flushStatus(); return; }
            if (!approachWithin(bed, 2, 2)) {
                if (status.startsWith("Traveling")) setStatus("Going home");
            } else {
                getNavigation().stop();
                if (!isSleeping()) startSleeping(bed);
                setStatus("Resting at bed");
            }
            flushStatus();
            return;
        }
        if (idleUntil > level.getGameTime() && "forester".equals(role)) {
            setStatus("Waiting for trees to grow (" + ((idleUntil - level.getGameTime()) / 20) + "s)");
            flushStatus();
            return;
        }
        Jobs.tick(level, this);
        flushStatus();
    }

    private void flushStatus() {
        if (statusDirty) {
            statusDirty = false;
            WorkerActions.sync(this);
        }
    }

    private void updateCombatStatus() {
        if (getTarget() != null) {
            status = "Fighting " + getTarget().getName().getString();
            return;
        }
        status = switch (companionMode == null ? "follow" : companionMode) {
            case "guard" -> "Guarding the village";
            case "stay" -> "Holding position";
            default -> "Following you";
        };
    }

    private void tryEatAndHeal(ServerLevel level) {
        if (getHealth() >= getMaxHealth() - 0.5f) return;
        if (tickCount % 40 == 0 && getHealth() < getMaxHealth()) {
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
            status = "Eating to recover";
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
     * Called once per second. When no progress is made the target is marked unreachable and, after
     * repeated failures, the worker hops to a safe spot beside it instead of giving up.
     */
    boolean approachWithin(BlockPos pos, double horizontal, double vertical) {
        if (!level().isLoaded(pos)) { status = "Target chunk unloaded"; return false; }
        if (!pos.equals(navigationTarget)) resetRoute(pos);
        double dx = getX() - (pos.getX() + 0.5);
        double dz = getZ() - (pos.getZ() + 0.5);
        double dy = Math.abs(pos.getY() + 0.5 - getY());
        if (dx * dx + dz * dz <= horizontal * horizontal && dy <= vertical) {
            getNavigation().stop();
            resetRoute(pos);
            failedRoutes = 0;
            return true;
        }
        double distance = distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        boolean improved = distance < lastDistance - 0.5;
        boolean moved = lastCheckPos != null && position().distanceToSqr(lastCheckPos) > 0.25;
        if (improved) { lastDistance = distance; noProgressTicks = 0; stuckTicks = 0; }
        else {
            noProgressTicks++;
            if (!moved) stuckTicks++;
        }
        lastCheckPos = position();
        if (stuckTicks >= 6 || noProgressTicks >= 30) {
            routeFailed(pos);
            return false;
        }
        // Cache path: only repath when target changed or navigation finished.
        if (getNavigation().isDone() || getNavigation().getPath() == null) {
            getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1);
        }
        setStatus("Traveling to " + pos.toShortString());
        return false;
    }

    private void resetRoute(BlockPos pos) {
        navigationTarget = pos;
        stuckTicks = 0;
        noProgressTicks = 0;
        lastDistance = Double.MAX_VALUE;
        lastCheckPos = null;
    }

    private void routeFailed(BlockPos pos) {
        getNavigation().stop();
        boolean repeat = isUnreachable(pos);
        failedRoutes++;
        unreachable.put(pos.asLong(), level().getGameTime() + 2400);
        navigationTarget = null;
        if ((repeat || failedRoutes >= 3) && hopNear(pos)) {
            failedRoutes = 0;
            unreachable.remove(pos.asLong());
            status = "Got unstuck near " + pos.toShortString();
            return;
        }
        if (pos.equals(target)) target = null;
        status = "Can't reach " + pos.toShortString() + " — trying another spot";
    }

    boolean isUnreachable(BlockPos pos) {
        Long until = unreachable.get(pos.asLong());
        if (until == null) return false;
        if (level().getGameTime() >= until) { unreachable.remove(pos.asLong()); return false; }
        return true;
    }

    /** Teleport to the closest safe standing spot beside pos (within 64 blocks). */
    boolean hopNear(BlockPos pos) {
        if (!(level() instanceof ServerLevel level)) return false;
        if (distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 64 * 64) return false;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -3, -2), pos.offset(2, 3, 2))) {
            if (p.equals(pos) || p.above().equals(pos)) continue;
            if (!canStandAt(level, p)) continue;
            double d = p.distSqr(pos);
            if (d < bestDistance) { bestDistance = d; best = p.immutable(); }
        }
        if (best == null) return false;
        getNavigation().stop();
        teleportTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
        return true;
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
            status = "Came to you (paused)";
        } else {
            getNavigation().moveTo(player, 1.1);
            status = "Coming to you (paused)";
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

    record AreaBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int dx() { return maxX - minX + 1; }
        int dy() { return maxY - minY + 1; }
        int dz() { return maxZ - minZ + 1; }
        long volume() { return (long) dx() * dy() * dz(); }
    }

    void collect(ItemStack stack) {
        ItemStack remaining = bag.addItem(stack);
        if (!remaining.isEmpty()) {
            spawnAtLocation(remaining);
            needsUnload = true;
            if (output != null) status = "Inventory full — dropping off at chest";
            else {
                status = Jobs.OUT_OF_SPACE;
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
        if (arrow.isEmpty()) { status = "Out of arrows"; return; }
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
        if (baseName != null && !baseName.isBlank()) tag.putString("BaseName", baseName);
        tag.putString("Role", role); tag.putString("Mode", mode); tag.putString("Status", status);
        tag.putString("CompanionMode", companionMode == null ? "follow" : companionMode);
        tag.putBoolean("Working", working); tag.putBoolean("UseBoneMeal", useBoneMeal);
        tag.putInt("Cursor", scanCursor); tag.putString("Blueprint", blueprint);
        tag.putInt("DigDepth", digDepth); tag.putInt("TargetY", targetY);
        tag.putLong("LastUnload", lastUnloadGameTime);
        tag.putLong("RecoverUntil", recoverUntil);
        tag.putLong("IdleUntil", idleUntil);
        tag.putBoolean("RanchShear", ranchShear);
        tag.putBoolean("RanchMilk", ranchMilk);
        tag.putBoolean("RanchEggs", ranchEggs);
        tag.putBoolean("RanchBreed", ranchBreed);
        tag.putBoolean("Culling", culling);
        if (swordLender != null) tag.putUUID("SwordLender", swordLender);
        if (!borrowedSword.isEmpty()) tag.put("BorrowedSword", borrowedSword.save(registryAccess()));
        if (!stashedMainHand.isEmpty()) tag.put("StashedMainHand", stashedMainHand.save(registryAccess()));
        tag.put("BlueprintBlocks", blueprintBlocks.copy());
        tag.put("BlueprintLibrary", blueprintLibrary.copy());
        tag.putInt("BuildRotation", buildRotation);
        savePos(tag, "First", first); savePos(tag, "Second", second); savePos(tag, "Bed", bed);
        savePos(tag, "Supply", supply); savePos(tag, "Output", output);
        savePos(tag, "CraftingTable", craftingTable); savePos(tag, "BuildOrigin", buildOrigin);
        ListTag furnaceTag = new ListTag();
        for (BlockPos p : furnaces) { CompoundTag e = new CompoundTag(); e.putLong("P", p.asLong()); furnaceTag.add(e); }
        tag.put("Furnaces", furnaceTag);
        ListTag pickupTag = new ListTag();
        for (BlockPos p : pickups) { CompoundTag e = new CompoundTag(); e.putLong("P", p.asLong()); pickupTag.add(e); }
        tag.put("Pickups", pickupTag);
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
        if (tag.hasUUID("Owner")) owner = tag.getUUID("Owner");
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
        ranchShear = !tag.contains("RanchShear") || tag.getBoolean("RanchShear");
        ranchMilk = !tag.contains("RanchMilk") || tag.getBoolean("RanchMilk");
        ranchEggs = !tag.contains("RanchEggs") || tag.getBoolean("RanchEggs");
        ranchBreed = !tag.contains("RanchBreed") || tag.getBoolean("RanchBreed");
        culling = tag.getBoolean("Culling");
        swordLender = tag.hasUUID("SwordLender") ? tag.getUUID("SwordLender") : null;
        borrowedSword = tag.contains("BorrowedSword")
            ? ItemStack.parseOptional(registryAccess(), tag.getCompound("BorrowedSword")) : ItemStack.EMPTY;
        stashedMainHand = tag.contains("StashedMainHand")
            ? ItemStack.parseOptional(registryAccess(), tag.getCompound("StashedMainHand")) : ItemStack.EMPTY;
        blueprintBlocks = tag.getList("BlueprintBlocks", Tag.TAG_COMPOUND).copy();
        blueprintLibrary = tag.getList("BlueprintLibrary", Tag.TAG_COMPOUND).copy();
        buildRotation = tag.getInt("BuildRotation");
        first = readPos(tag, "First"); second = readPos(tag, "Second"); bed = readPos(tag, "Bed");
        supply = readPos(tag, "Supply"); output = readPos(tag, "Output");
        craftingTable = readPos(tag, "CraftingTable"); buildOrigin = readPos(tag, "BuildOrigin");
        furnaces.clear();
        ListTag furnaceTag = tag.getList("Furnaces", Tag.TAG_COMPOUND);
        for (int i = 0; i < furnaceTag.size(); i++) furnaces.add(BlockPos.of(furnaceTag.getCompound(i).getLong("P")));
        pickups.clear();
        ListTag pickupTag = tag.getList("Pickups", Tag.TAG_COMPOUND);
        for (int i = 0; i < pickupTag.size(); i++) pickups.add(BlockPos.of(pickupTag.getCompound(i).getLong("P")));
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
        private final Worker worker;
        private int cooldown;

        CompanionGoal(Worker worker) {
            this.worker = worker;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override public boolean canUse() {
            return WorkerActions.isCombatRole(worker.role) && worker.getTarget() == null;
        }

        @Override public void tick() {
            if (--cooldown > 0) return;
            cooldown = 10;
            String mode = worker.companionMode == null ? "follow" : worker.companionMode;
            if ("stay".equals(mode)) {
                worker.getNavigation().stop();
                return;
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
            if (dist > 24 * 24) {
                BlockPos near = owner.blockPosition();
                if (!worker.hopNear(near)) {
                    // Find any safe spot near owner instead of dropping into void/water.
                    for (BlockPos p : BlockPos.betweenClosed(near.offset(-3, -2, -3), near.offset(3, 2, 3))) {
                        if (Worker.canStandAt(worker.level(), p)) {
                            worker.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                            return;
                        }
                    }
                }
                return;
            }
            if (worker.isInWater() && dist > 8 * 8) {
                if (worker.hopNear(owner.blockPosition())) return;
            }
            if (dist > 10 * 10) worker.getNavigation().moveTo(owner, 1.15);
            else if (dist < 3 * 3) worker.getNavigation().stop();
            else if (worker.getNavigation().isDone()) worker.getNavigation().moveTo(owner, 1.0);
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
