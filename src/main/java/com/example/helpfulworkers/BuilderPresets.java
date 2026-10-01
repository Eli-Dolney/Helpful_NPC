package com.example.helpfulworkers;

/** Compatibility shim: village presets now come from {@link VillageCatalog}. */
final class BuilderPresets {
    private BuilderPresets() {}

    /** Deprecated handmade names — empty; use {@link VillageCatalog}. */
    static final String[] NAMES = new String[0];

    static boolean isPreset(String name) {
        return VillageCatalog.byId(name) != null;
    }

    static String displayName(String name) {
        VillageCatalog.Entry entry = VillageCatalog.byId(name);
        if (entry != null) return entry.label();
        if (name == null || name.isEmpty()) return "";
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Prefer {@link VillageCatalog#loadIntoWorker}; this needs a level. */
    static String load(Worker worker, String name) {
        if (worker.level() instanceof net.minecraft.server.level.ServerLevel level) {
            return VillageCatalog.loadIntoWorker(level, worker, name);
        }
        return "Use VillageCatalog.loadIntoWorker";
    }
}
