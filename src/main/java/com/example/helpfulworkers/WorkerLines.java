package com.example.helpfulworkers;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/** Short role-flavoured things a worker says when the owner walks up. The text lives in lang (line.helpfulworkers.*). */
final class WorkerLines {
    private WorkerLines() {}

    /** One of a role's three lines; lines that mention the bag use the item count as %s. */
    static Component greeting(Worker worker, RandomSource random) {
        int carried = 0;
        for (int i = 0; i < worker.bag.getContainerSize(); i++) carried += worker.bag.getItem(i).getCount();
        // Half the time the worker speaks its personality instead of its trade.
        if (worker.trait != null && Worker.TRAITS.contains(worker.trait) && random.nextBoolean()) {
            return Component.translatable(Lang.traitLineKey(worker.trait, 1 + random.nextInt(Lang.TRAIT_LINES)), carried);
        }
        return Component.translatable(Lang.lineKey(worker.role, 1 + random.nextInt(3)), carried);
    }
}
