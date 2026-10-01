package com.example.helpfulworkers;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Items;
import org.slf4j.Logger;

@Mod(HelpfulWorkers.ID)
public final class HelpfulWorkers {
    public static final String ID = "helpfulworkers";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    public static final Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);

    public static final DeferredHolder<EntityType<?>, EntityType<Worker>> WORKER = ENTITIES.register("worker",
        () -> EntityType.Builder.of(Worker::new, MobCategory.CREATURE).sized(0.6f, 1.8f).clientTrackingRange(8).build("helpfulworkers:worker"));

    public static final DeferredItem<Item> CONTRACT = ITEMS.register("recruitment_contract",
        () -> new ContractItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> FARMER_KIT = ITEMS.register("farmers_kit",
        () -> new RoleKitItem(new Item.Properties().stacksTo(1), "farmer"));
    public static final DeferredItem<Item> FORESTER_KIT = ITEMS.register("foresters_kit",
        () -> new RoleKitItem(new Item.Properties().stacksTo(1), "forester"));
    public static final DeferredItem<Item> MINER_KIT = ITEMS.register("miners_kit",
        () -> new RoleKitItem(new Item.Properties().stacksTo(1), "miner"));
    public static final DeferredItem<Item> BUILDER_KIT = ITEMS.register("builders_kit",
        () -> new RoleKitItem(new Item.Properties().stacksTo(1), "builder"));
    public static final DeferredItem<Item> KNIGHT_KIT = ITEMS.register("knights_kit",
        () -> new RoleKitItem(new Item.Properties().stacksTo(1), "knight"));
    public static final DeferredItem<Item> ARCHER_KIT = ITEMS.register("archers_kit",
        () -> new RoleKitItem(new Item.Properties().stacksTo(1), "archer"));
    public static final DeferredItem<Item> RANCHER_KIT = ITEMS.register("ranchers_kit",
        () -> new RoleKitItem(new Item.Properties().stacksTo(1), "rancher"));
    public static final DeferredItem<Item> FISHER_KIT = ITEMS.register("fishermans_kit",
        () -> new RoleKitItem(new Item.Properties().stacksTo(1), "fisher"));
    public static final DeferredItem<Item> SMELTER_KIT = ITEMS.register("smelters_kit",
        () -> new RoleKitItem(new Item.Properties().stacksTo(1), "smelter"));
    public static final DeferredItem<Item> COURIER_KIT = ITEMS.register("couriers_kit",
        () -> new RoleKitItem(new Item.Properties().stacksTo(1), "courier"));
    public static final DeferredItem<Item> CLIPBOARD = ITEMS.register("assignment_clipboard",
        () -> new AssignmentClipboardItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<MenuType<?>, MenuType<WorkerInventoryMenu>> WORKER_MENU = MENUS.register("worker_inventory",
        () -> IMenuTypeExtension.create(WorkerInventoryMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("helpfulworkers",
        () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.helpfulworkers"))
            .icon(() -> new ItemStack(CONTRACT.get()))
            .displayItems((params, output) -> {
                output.accept(CONTRACT.get());
                output.accept(FARMER_KIT.get());
                output.accept(FORESTER_KIT.get());
                output.accept(MINER_KIT.get());
                output.accept(BUILDER_KIT.get());
                output.accept(KNIGHT_KIT.get());
                output.accept(ARCHER_KIT.get());
                output.accept(RANCHER_KIT.get());
                output.accept(FISHER_KIT.get());
                output.accept(SMELTER_KIT.get());
                output.accept(COURIER_KIT.get());
                output.accept(CLIPBOARD.get());
            }).build());

    public HelpfulWorkers(IEventBus bus, ModContainer container) {
        ENTITIES.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
        MENUS.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, WorkerConfig.SPEC);
        bus.addListener(this::attributes);
        NeoForge.EVENT_BUS.addListener(WorkerCommands::register);
        WorkerSessions.register();
        WorkerRegistry.register();
        WorkerCombat.register();
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(WORKER.get(), Worker.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 30).add(Attributes.MOVEMENT_SPEED, 0.28)
            .add(Attributes.FOLLOW_RANGE, 48).add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.ARMOR, 2.0).build());
    }
}
