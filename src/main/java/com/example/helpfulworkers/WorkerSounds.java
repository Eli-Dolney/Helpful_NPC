package com.example.helpfulworkers;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The worker's voice and level-up chime. sounds.json maps them onto vanilla villager and player sounds. */
final class WorkerSounds {
    private WorkerSounds() {}

    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, HelpfulWorkers.ID);

    static final DeferredHolder<SoundEvent, SoundEvent> GREET = register("worker.greet");
    static final DeferredHolder<SoundEvent, SoundEvent> CHATTER = register("worker.chatter");
    static final DeferredHolder<SoundEvent, SoundEvent> HMM = register("worker.hmm");
    static final DeferredHolder<SoundEvent, SoundEvent> LEVEL_UP = register("worker.levelup");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(HelpfulWorkers.ID, name)));
    }
}
