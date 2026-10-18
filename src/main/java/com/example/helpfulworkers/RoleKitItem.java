package com.example.helpfulworkers;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class RoleKitItem extends Item {
    private final String role;

    public RoleKitItem(Properties properties, String role) {
        super(properties);
        this.role = role;
    }

    public String role() {
        return role;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Worker worker)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!worker.owns(player)) {
            player.displayClientMessage(Component.literal("This worker belongs to someone else."), false);
            return InteractionResult.SUCCESS;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            WorkerNetwork.openDialogue(serverPlayer, worker, role);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(Lang.kitDescKey(role)).withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable(Lang.KIT_REUSABLE).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
