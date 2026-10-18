package com.example.helpfulworkers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;

import java.util.*;

final class SiteUi {
    static final class Session {
        UUID worker, site;
        String dimension;
        int revision;
        boolean remote;
        String pending = "", type = "iron";
        BlockPos first, second;
        Integer floor;
        int rotation;
        SiteData.Site preview;
        boolean customTemplate;
        net.minecraft.nbt.CompoundTag capture;
        int captureCursor, anchorCursor;
        final List<Runnable> actions = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
    }

    static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int revision;

    static Session create(ServerPlayer p) {
        close(p);
        Session s = new Session();
        s.dimension = p.serverLevel().dimension().location().toString();
        SESSIONS.put(p.getUUID(), s);
        return s;
    }

    static boolean valid(ServerPlayer p, Session s) {
        if (!s.dimension.equals(p.serverLevel().dimension().location().toString())) return false;
        if (s.site != null) {
            var site = SiteData.get(p.serverLevel()).sites.get(s.site);
            if (site == null || !site.owner.equals(p.getUUID())) return false;
        }
        if (s.worker != null) {
            return p.serverLevel().getEntity(s.worker) instanceof Worker w
                    && w.isAlive()
                    && w.owns(p)
                    && (s.remote || p.distanceToSqr(w) <= 64);
        }
        var site = SiteData.get(p.serverLevel()).sites.get(s.site);
        return site != null
                && site.owner.equals(p.getUUID())
                && p.serverLevel().hasChunkAt(site.core())
                && (s.remote || p.distanceToSqr(site.core().getCenter()) <= 64);
    }

    static void openWorker(ServerPlayer p, Worker w) {
        if (!w.owns(p) || p.distanceToSqr(w) > 64) return;
        Session s = create(p);
        s.worker = w.getUUID();
        WorkerSessions.openViewer(p, w, WorkerSessions.ViewerKind.DIALOGUE);
        workerMenu(p, s);
    }

    static void openCore(ServerPlayer p, BlockPos pos) {
        var site = SiteData.get(p.serverLevel()).at(pos);
        if (site == null
                || !site.owner.equals(p.getUUID())
                || p.distanceToSqr(pos.getCenter()) > 64) {
            tell(p, "This site is unavailable or belongs to another player");
            return;
        }
        Session s = create(p);
        s.site = site.id;
        coreMenu(p, s);
    }

    static void close(ServerPlayer p) {
        SESSIONS.remove(p.getUUID());
        WorkerSessions.closeViewer(p);
    }

    static void action(ServerPlayer p, SiteNetwork.Action a) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || a.revision() != s.revision || !valid(p, s)) {
            close(p);
            return;
        }
        if (a.index() == -1) {
            close(p);
            return;
        }
        if (a.index() < 0 || a.index() >= s.actions.size()) return;
        Runnable run = s.actions.get(a.index());
        try {
            run.run();
        } catch (IllegalArgumentException e) {
            tell(p, e.getMessage());
            if (s.site != null) coreMenu(p, s);
            else if (s.preview != null) previewMenu(p, s);
            else workerMenu(p, s);
        }
    }

    static void begin(Session s) {
        s.actions.clear();
        s.labels.clear();
        s.revision = ++revision;
    }

    static void option(Session s, String label, Runnable action) {
        s.labels.add(label);
        s.actions.add(action);
    }

    static void send(ServerPlayer p, Session s, String title, String text) {
        SiteNetwork.send(p, new SiteNetwork.Menu(s.revision, title, text, List.copyOf(s.labels)));
    }

    static Worker worker(ServerPlayer p, Session s) {
        return (Worker) p.serverLevel().getEntity(s.worker);
    }

    static void workerMenu(ServerPlayer p, Session s) {
        Worker w = worker(p, s);
        begin(s);
        option(s, "Refresh status", () -> workerMenu(p, s));
        if (w.constructionId != null) {
            option(
                    s,
                    "Resume site construction",
                    () -> {
                        w.working = true;
                        close(p);
                        SiteNetwork.send(p, new SiteNetwork.Menu(-1, "", "", List.of()));
                        tell(p, "Construction resumed");
                    });
            option(
                    s,
                    "Manage construction",
                    () -> {
                        var site = SiteData.get(p.serverLevel()).sites.get(w.constructionId);
                        if (site != null) {
                            s.site = site.id;
                            coreMenu(p, s);
                        }
                    });
        } else if ("builder".equals(w.role)) option(s, "Build a worker site", () -> catalog(p, s));
        if ("builder".equals(w.role)) {
            for (var site : SiteData.get(p.serverLevel()).sites.values()) {
                if (site.owner.equals(p.getUUID())
                        && !site.active()
                        && !site.id.equals(w.constructionId)) {
                    option(
                            s,
                            "Manage unfinished: "
                                    + SiteCatalog.label(site.type)
                                    + " "
                                    + site.core().toShortString(),
                            () -> {
                                s.site = site.id;
                                coreMenu(p, s);
                            });
                }
            }
        }
        if (w.siteId != null) {
            option(
                    s,
                    "Manage assigned site",
                    () -> {
                        s.site = w.siteId;
                        coreMenu(p, s);
                    });
            option(
                    s,
                    "Release site / use zones",
                    () -> {
                        SiteActions.release(p.serverLevel(), w);
                        workerMenu(p, s);
                    });
        } else
            for (var site : SiteData.get(p.serverLevel()).sites.values())
                if (site.owner.equals(p.getUUID()) && site.active() && SiteActions.accepts(site, w) && (site.worker == null || site.type.equals("warehouse")))
                    option(
                            s,
                            "Assign: "
                                    + SiteCatalog.label(site.type)
                                    + " "
                                    + site.core().toShortString(),
                            () -> {
                                SiteActions.assign(p, w, site);
                                workerMenu(p, s);
                            });
        send(p, s, "Worker sites", w.getName().getString() + " — " + w.status);
    }

    static void catalog(ServerPlayer p, Session s) {
        begin(s);
        for (String ore : SiteCatalog.ORES)
            option(s, SiteCatalog.label(ore), () -> choose(p, s, ore));
        option(s, "Logging Camp (all eight woods)", () -> choose(p, s, "logging"));
        option(s, "Warehouse", () -> choose(p, s, "warehouse"));
        for(String type : SiteCatalog.EXTRA) option(s,SiteCatalog.label(type),()->choose(p,s,type));
        option(s,"Save a custom worker-site template…",()->SiteTemplates.menu(p,s));
        option(s, "Back", () -> workerMenu(p, s));
        send(
                p,
                s,
                "Build a worker site",
                "Choose a site, then mark a plot. One matching core pays for construction.");
    }

    static void choose(ServerPlayer p, Session s, String type) {
        s.type = type;
        s.customTemplate=false;
        if(SiteData.get(p.serverLevel()).templates.containsKey(p.getUUID()+":"+type)) {
            begin(s);
            option(s,"Use my custom template",()->{s.customTemplate=true; selectPlot(p,s);});
            option(s,"Use starter template",()->selectPlot(p,s));
            send(p,s,SiteCatalog.label(type),"Choose a building design."); return;
        }
        selectPlot(p,s);
    }

    static void selectPlot(ServerPlayer p,Session s) {
        s.floor = null;
        s.first = null;
        s.second = null;
        s.preview = null;
        s.rotation = 0;
        startSelection(p, s, "plot");
    }

    static void startSelection(ServerPlayer p, Session s, String kind) {
        if (!WorkerActions.hasItem(p, HelpfulWorkers.CLIPBOARD.get()))
            throw new IllegalArgumentException("Craft an Assignment Clipboard: paper + stick");
        s.pending = kind;
        s.remote = true;
        WorkerSessions.clearClipboard(p);
        if (s.worker != null)
            WorkerSessions.openViewer(p, worker(p, s), WorkerSessions.ViewerKind.DIALOGUE, true);
        begin(s);
        // The client can close the selection screen without ending the persistent selection
        // session.
        option(
                s,
                "Select in the world",
                () -> SiteNetwork.send(p, new SiteNetwork.Menu(-1, "", "", List.of())));
        send(
                p,
                s,
                "Show the location",
                "Click Select, then point out " + kind + ". Sneak-right-click cancels.");
    }

    static boolean pending(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        return s != null && !s.pending.isEmpty();
    }

    static boolean click(ServerPlayer p, BlockPos pos) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.pending.isEmpty()) return false;
        if (!valid(p, s)) {
            close(p);
            tell(p, "Site selection cancelled");
            return true;
        }
        if (p.isShiftKeyDown()) {
            s.pending = "";
            if (s.site != null) coreMenu(p, s);
            else workerMenu(p, s);
            return true;
        }
        if (!p.getMainHandItem().is(HelpfulWorkers.CLIPBOARD.get())
                && !p.getOffhandItem().is(HelpfulWorkers.CLIPBOARD.get())) {
            tell(p, "Hold your Assignment Clipboard to select");
            return true;
        }
        if (p.distanceToSqr(pos.getCenter()) > 64 || !p.serverLevel().hasChunkAt(pos)) {
            tell(p, "Select a loaded target within eight blocks");
            return true;
        }
        try {
            if(s.pending.startsWith("capture")) { SiteTemplates.click(p,s,pos); return true; }
            if (s.pending.equals("preview")) {
                s.pending = "";
                previewMenu(p, s);
                return true;
            }
            if (s.pending.equals("plot")) {
                if (s.first == null) {
                    s.first = pos.immutable();
                    tell(p, "First corner set. Click the opposite corner.");
                    return true;
                }
                s.second = pos.immutable();
                s.preview =
                        SiteConstruction.plan(
                                p,
                                worker(p, s),
                                s.type,
                                s.first,
                                s.second,
                                s.rotation,
                                s.floor,
                                false);
                s.floor = s.preview.origin.getY();
                s.pending = "";
                previewMenu(p, s);
                return true;
            }
            if (s.pending.equals("link_worker")) {
                tell(p, "Right-click the worker to link");
                return true;
            }
            var site = SiteData.get(p.serverLevel()).sites.get(s.site);
            if (site == null) throw new IllegalArgumentException("Warehouse unavailable");
            if (s.pending.equals("link_site")) {
                var other = SiteData.get(p.serverLevel()).at(pos);
                if (other == null
                        || !other.owner.equals(p.getUUID())
                        || other.id.equals(site.id)
                        || "warehouse".equals(other.type))
                    throw new IllegalArgumentException("Click an owned resource-site core");
                if (site.linkedSites.size() >= 64)
                    throw new IllegalArgumentException("Warehouse site-link limit reached");
                WarehouseSupplies.linkSite(p.serverLevel(),site,other.id);
            } else {
                BlockPos key = WarehouseJobs.canonical(p.serverLevel(), pos);
                if (StorageOps.at(p.serverLevel(), key) == null)
                    throw new IllegalArgumentException("Click a chest or barrel");
                if (!(p.serverLevel().getBlockState(key).getBlock()
                                instanceof net.minecraft.world.level.block.ChestBlock)
                        && !p.serverLevel()
                                .getBlockState(key)
                                .is(net.minecraft.world.level.block.Blocks.BARREL))
                    throw new IllegalArgumentException("Use a chest or barrel");
                if (!SiteActions.canRegister(p.serverLevel(), p.getUUID(), key))
                    throw new IllegalArgumentException("Container belongs to another worker site");
                if (site.storage.stream().anyMatch(st -> st.pos.equals(key)))
                    throw new IllegalArgumentException("Container already registered");
                if (site.storage.size() >= SiteSettings.storageLimit())
                    throw new IllegalArgumentException("Warehouse container limit reached");
                var st = new SiteData.Storage(key);
                st.mode = s.pending;
                site.storage.add(st);
            }
            s.pending = "";
            SiteData.get(p.serverLevel()).setDirty();
            coreMenu(p, s);
        } catch (IllegalArgumentException e) {
            tell(p, e.getMessage());
        }
        return true;
    }

    static boolean clickWorker(ServerPlayer p, Worker w) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || !s.pending.equals("link_worker")) return false;
        if (!valid(p, s) || !w.owns(p) || p.distanceToSqr(w) > 64) return true;
        var site = SiteData.get(p.serverLevel()).sites.get(s.site);
        if (site != null) {
            if (site.linkedWorkers.size() >= 64) {
                tell(p, "Warehouse worker-link limit reached");
                return true;
            }
            WarehouseSupplies.linkWorker(p.serverLevel(),site,w.getUUID());
            SiteData.get(p.serverLevel()).setDirty();
            s.pending = "";
            coreMenu(p, s);
        }
        return true;
    }

    static void previewMenu(ServerPlayer p, Session s) {
        begin(s);
        var plan = s.preview;
        if (plan == null) {
            catalog(p, s);
            return;
        }
        preview(p, plan);
        option(
                s,
                "View plot in the world",
                () -> {
                    s.pending = "preview";
                    SiteNetwork.send(p, new SiteNetwork.Menu(-1, "", "", List.of()));
                    tell(
                            p,
                            "Check the outline, then click with your clipboard to return to the"
                                    + " preview.");
                });
        option(
                s,
                "Rotate 90 degrees",
                () -> {
                    int rotation = (s.rotation + 1) % 4;
                    var preview =
                            SiteConstruction.plan(
                                    p,
                                    worker(p, s),
                                    s.type,
                                    s.first,
                                    s.second,
                                    rotation,
                                    s.floor,
                                    false);
                    s.rotation = rotation;
                    s.preview = preview;
                    previewMenu(p, s);
                });
        for (int dy : new int[] {-1, 1})
            option(
                    s,
                    dy < 0 ? "Lower ground one block" : "Raise ground one block",
                    () -> {
                        int y = s.floor + dy;
                        s.preview =
                                SiteConstruction.plan(
                                        p,
                                        worker(p, s),
                                        s.type,
                                        s.first,
                                        s.second,
                                        s.rotation,
                                        y,
                                        false);
                        s.floor = y;
                        previewMenu(p, s);
                    });
        option(
                s,
                "Confirm and build (one core)",
                () -> {
                    SiteActions.construct(p, worker(p, s), s);
                    s.preview = null;
                    s.pending = "";
                    close(p);
                    SiteNetwork.send(p, new SiteNetwork.Menu(-1, "", "", List.of()));
                });
        option(s, "Instant Build (one core)…", () -> instantMenu(p,s,null));
        option(s, "Choose another plot", () -> choose(p, s, s.type));
        option(s, "Cancel", () -> workerMenu(p, s));
        send(
                p,
                s,
                SiteCatalog.label(s.type),
                "Ground Y="
                        + plan.origin.getY()
                        + ", rotation "
                        + (s.rotation * 90)
                        + "°. Green: footprint; yellow: center; blue: entrance.");
    }

    static void preview(ServerPlayer p, SiteData.Site site) {
        var l = p.serverLevel();
        int w = site.width(), d = site.depth();
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++)
                if (x == 0 || z == 0 || x == w - 1 || z == d - 1) {
                    BlockPos q = site.at(x, 1, z);
                    l.sendParticles(
                            p,
                            ParticleTypes.HAPPY_VILLAGER,
                            true,
                            q.getX() + .5,
                            q.getY(),
                            q.getZ() + .5,
                            1,
                            0,
                            0,
                            0,
                            0);
                }
        BlockPos c = site.core(), e = site.at(w / 2, 1, d - 1);
        l.sendParticles(
                p,
                ParticleTypes.END_ROD,
                true,
                c.getX() + .5,
                c.getY() + 1,
                c.getZ() + .5,
                12,
                .1,
                1,
                .1,
                0);
        l.sendParticles(
                p,
                ParticleTypes.SOUL_FIRE_FLAME,
                true,
                e.getX() + .5,
                e.getY(),
                e.getZ() + .5,
                12,
                .2,
                .2,
                .2,
                0);
    }

    static void coreMenu(ServerPlayer p, Session s) {
        var site = SiteData.get(p.serverLevel()).sites.get(s.site);
        if (site == null) {
            close(p);
            return;
        }
        begin(s);
        option(s, "Refresh status", () -> coreMenu(p, s));
        option(
                s,
                site.paused ? "Resume site" : "Pause site",
                () -> {
                    site.paused = !site.paused;
                    if (!site.paused && site.worker != null && p.serverLevel().getEntity(site.worker) instanceof Worker assigned) assigned.working = true;
                    site.lastActive = -1;
                    SiteData.get(p.serverLevel()).setDirty();
                    coreMenu(p, s);
                });
        if (!site.active()) option(s,"Instant Build…",()->instantMenu(p,s,site));
        if (!site.active())
            option(
                    s,
                    "Resume with nearby builder",
                    () -> {
                        Worker b = WorkerActions.nearestOwned(p, 12);
                        if (b == null || !b.role.equals("builder"))
                            throw new IllegalArgumentException(
                                    "Bring your builder within 12 blocks");
                        if (b.constructionId != null && !b.constructionId.equals(site.id))
                            throw new IllegalArgumentException(
                                    "Builder already has a construction job");
                        site.builder = b.getUUID();
                        b.constructionId = site.id;
                        b.working = true;
                        site.paused = false;
                        SiteData.get(p.serverLevel()).setDirty();
                        coreMenu(p, s);
                    });
        if (site.active()) {
            for (Worker w :
                    p.serverLevel()
                            .getEntities(
                                    HelpfulWorkers.WORKER.get(),
                                    w -> w.owns(p) && w.isAlive() && p.distanceToSqr(w) <= 1024))
                if (SiteActions.accepts(site, w) && w.siteId == null && w.constructionId == null && (!w.working || w.first == null) && (site.worker == null || site.type.equals("warehouse")))
                    option(
                            s,
                            "Assign " + w.getName().getString(),
                            () -> {
                                SiteActions.assign(p, w, site);
                                coreMenu(p, s);
                            });
            if (site.worker != null)
                option(
                        s,
                        "Release assigned worker",
                        () -> {
                            if (p.serverLevel().getEntity(site.worker) instanceof Worker w)
                                SiteActions.release(p.serverLevel(), w);
                            else site.worker = null;
                            SiteData.get(p.serverLevel()).setDirty();
                            coreMenu(p, s);
                        });
            if (site.type.equals("logging")) {
                NaturalTrees.ensure(p.serverLevel(),site);
                option(s,"Add tree slot…",()->NaturalTrees.menu(p,s,site));
                for(var slot:site.treeSlots) option(s,(slot.enabled ? "On: " : "Off: ")+slot.species+" "+slot.base.toShortString(),()->{
                    slot.enabled=!slot.enabled; SiteData.get(p.serverLevel()).setDirty(); coreMenu(p,s);
                });
            }
            if (false)
                for (int i = 0; i < 8; i++) {
                    final int index = i;
                    option(
                            s,
                            (site.enabled[i] ? "On: " : "Off: ")
                                    + SiteCatalog.pretty(SiteCatalog.TREES[i]),
                            () -> {
                                site.enabled[index] = !site.enabled[index];
                                SiteData.get(p.serverLevel()).setDirty();
                                coreMenu(p, s);
                            });
                }
            if (site.type.equals("warehouse")) {
                option(s, "Register incoming chest", () -> startSelection(p, s, "incoming"));
                option(s, "Register sorted chest", () -> startSelection(p, s, "sorted"));
                option(s, "Register overflow chest", () -> startSelection(p, s, "overflow"));
                option(s, "Link site output (click core)", () -> startSelection(p, s, "link_site"));
                option(s, "Link worker (click worker)", () -> startSelection(p, s, "link_worker"));
                for (var st : site.storage)
                    option(
                            s,
                            st.mode + ": " + st.pos.toShortString() + " / " + st.filter,
                            () -> storageMenu(p, s, site, st));
                for(UUID id : site.linkedWorkers) option(s,"Supplies: "+shortName(p,id),()->WarehouseSupplies.menu(p,s,site,id));
                option(s,"Restore automatic connections",()->{ site.excluded.clear(); WarehouseSupplies.connect(p.serverLevel(),site); coreMenu(p,s); });
                for (UUID id : site.linkedWorkers)
                    option(
                            s,
                            "Unlink worker " + shortName(p, id),
                            () -> {
                                site.linkedWorkers.remove(id); site.excluded.add(id);
                                SiteData.get(p.serverLevel()).setDirty();
                                coreMenu(p, s);
                            });
                for (UUID id : site.linkedSites)
                    option(
                            s,
                            "Unlink site " + id.toString().substring(0, 8),
                            () -> {
                                site.linkedSites.remove(id); site.excluded.add(id);
                                SiteData.get(p.serverLevel()).setDirty();
                                coreMenu(p, s);
                            });
                for (UUID id : site.couriers)
                    option(
                            s,
                            "Release courier " + shortName(p, id),
                            () -> {
                                if (p.serverLevel().getEntity(id) instanceof Worker w)
                                    SiteActions.release(p.serverLevel(), w);
                                else site.couriers.remove(id);
                                SiteData.get(p.serverLevel()).setDirty();
                                coreMenu(p, s);
                            });
            }
        }
        option(
                s,
                "Show footprint",
                () -> {
                    preview(p, site);
                    coreMenu(p, s);
                });
        option(
                s,
                "Dismantle site…",
                () -> {
                    begin(s);
                    option(
                            s,
                            "Confirm dismantle and return core",
                            () -> {
                                SiteActions.dismantle(p, site);
                                close(p);
                                SiteNetwork.send(p, new SiteNetwork.Menu(-1, "", "", List.of()));
                            });
                    option(s, "Back", () -> coreMenu(p, s));
                    send(
                            p,
                            s,
                            "Dismantle site?",
                            "Generated resources disappear. Building and stored items remain.");
                });
        String info = site.paused ? "Paused by owner" : site.worker != null ? "Assigned" : site.status;
        if (site.worker != null) info += " | " + shortName(p, site.worker);
        if (site.type.equals("warehouse")) {
            int used = 0, slots = 0;
            for (var st : site.storage) {
                var c = StorageOps.at(p.serverLevel(), st.pos);
                if (c != null) {
                    slots += c.getContainerSize();
                    for (int i = 0; i < c.getContainerSize(); i++)
                        if (!c.getItem(i).isEmpty()) used++;
                }
            }
            info =
                    "Storage "
                            + used
                            + "/"
                            + slots
                            + " slots; couriers "
                            + site.couriers.size()
                            + "; links "
                            + (site.linkedSites.size() + site.linkedWorkers.size());
        }
        send(p, s, SiteCatalog.label(site.type), info);
    }

    static String shortName(ServerPlayer p, UUID id) {
        return p.serverLevel().getEntity(id) instanceof Worker w
                ? w.getName().getString() + ": " + w.status
                : id.toString().substring(0, 8) + " (unloaded)";
    }

    static void storageMenu(ServerPlayer p, Session s, SiteData.Site site, SiteData.Storage st) {
        begin(s);
        for (String mode : new String[] {"incoming", "sorted", "overflow"})
            option(
                    s,
                    "Use as " + mode,
                    () -> {
                        st.mode = mode;
                        SiteData.get(p.serverLevel()).setDirty();
                        storageMenu(p, s, site, st);
                    });
        for (String category : WarehouseJobs.CATEGORIES)
            option(
                    s,
                    "Category: " + category.replace('_', ' '),
                    () -> {
                        st.mode = "sorted";
                        st.filter = category;
                        SiteData.get(p.serverLevel()).setDirty();
                        storageMenu(p, s, site, st);
                    });
        option(
                s,
                "Exact filter: item in your main hand",
                () -> {
                    ItemStack held = p.getMainHandItem();
                    if (held.isEmpty())
                        throw new IllegalArgumentException("Hold the desired item first");
                    st.mode = "sorted";
                    st.filter =
                            "item:"
                                    + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                                            held.getItem());
                    SiteData.get(p.serverLevel()).setDirty();
                    storageMenu(p, s, site, st);
                });
        option(
                s,
                "Unregister container",
                () -> {
                    site.storage.remove(st);
                    SiteData.get(p.serverLevel()).setDirty();
                    coreMenu(p, s);
                });
        option(s, "Back", () -> coreMenu(p, s));
        send(
                p,
                s,
                "Warehouse container",
                st.pos.toShortString() + " — " + st.mode + " / " + st.filter);
    }

    static void instantMenu(ServerPlayer p, Session session, SiteData.Site site) {
        begin(session);
        option(session,"Yes — finish now",()-> {
            SiteData.Site job=site;
            if(job==null) {
                SiteActions.construct(p,worker(p,session),session);
                job=SiteData.get(p.serverLevel()).sites.get(worker(p,session).constructionId);
            }
            if(job==null || !job.owner.equals(p.getUUID()) || job.active()) throw new IllegalArgumentException("No unfinished build");
            if(!(p.serverLevel().getEntity(job.builder) instanceof Worker builder) || !builder.owns(p)) throw new IllegalArgumentException("Load the assigned builder first");
            job.instant=true; job.paused=false; builder.working=true;
            SiteData.get(p.serverLevel()).setDirty();
            close(p); SiteNetwork.send(p,new SiteNetwork.Menu(-1,"","",List.of()));
        });
        option(session,"No",()-> { if(site==null) previewMenu(p,session); else coreMenu(p,session); });
        send(p,session,"Instant Build?","Only if you’re feeling really lazy — finish this build now?");
    }

    static void tell(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text));
    }
}
