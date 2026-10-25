package com.example.helpfulworkers;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Town hall: one place to manage the village. Right-click opens the worker overview; right-click
 * with a Recruitment Contract recruits a worker beside the hall; sneak-right-click makes the hall
 * the gathering point for your workers that have no bed (recall and "go home" walk there).
 */
public final class TownHallBlock extends Block {
    /** How far around the hall sneak-click reaches when assigning workers to it. */
    static final int ASSIGN_RANGE = 64;

    public TownHallBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(HelpfulWorkers.CONTRACT.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return ItemInteractionResult.SUCCESS;
        }
        BlockPos spot = standSpotBeside(server, pos, hit.getDirection());
        if (spot == null) {
            player.displayClientMessage(Component.translatable(Lang.HALL_NO_ROOM), false);
            return ItemInteractionResult.FAIL;
        }
        Worker worker = ContractItem.recruit(server, serverPlayer, spot, serverPlayer.getYRot());
        if (worker == null) return ItemInteractionResult.FAIL;
        worker.hall = pos.immutable();
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(Component.translatable(Lang.HALL_RECRUITED, worker.getName().getString()), false);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            int n = assignHall(server, serverPlayer, pos);
            player.displayClientMessage(Component.translatable(Lang.HALL_ASSIGNED, n), false);
            return InteractionResult.CONSUME;
        }
        WorkerNetwork.sendRoster(serverPlayer);
        return InteractionResult.CONSUME;
    }

    /** Make the hall the gathering point of every owned worker within range; returns how many. */
    static int assignHall(ServerLevel level, ServerPlayer player, BlockPos hall) {
        List<Worker> workers = level.getEntitiesOfClass(Worker.class, new AABB(hall).inflate(ASSIGN_RANGE), w -> w.owns(player));
        for (Worker w : workers) {
            w.hall = hall.immutable();
            WorkerActions.sync(w);
        }
        return workers.size();
    }

    /** A free spot next to the hall: the clicked face first, then the other sides, at the hall's height or one above. */
    static BlockPos standSpotBeside(ServerLevel level, BlockPos hall, Direction preferred) {
        if (preferred.getAxis().isHorizontal()) {
            BlockPos p = hall.relative(preferred);
            if (Worker.canStandAt(level, p)) return p;
        }
        for (int dy = 0; dy <= 1; dy++) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos p = hall.relative(d).above(dy);
                if (Worker.canStandAt(level, p)) return p;
            }
        }
        return Worker.canStandAt(level, hall.above()) ? hall.above() : null;
    }
}
