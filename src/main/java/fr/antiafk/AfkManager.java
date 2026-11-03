package fr.antiafk;

import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AfkManager {

    static final class Data {
        volatile long last = System.currentTimeMillis();
        Location anchor;                                  // dernière position "active" (thread principal uniquement)
        final Set<Integer> warned = new HashSet<>();      // seuils déjà annoncés (thread principal uniquement)
        boolean afk;                                      // déjà envoyé en zone AFK, en attente d'une vraie activité
        long afkSince;
    }

    private final AntiAfkPlugin plugin;
    private final Map<UUID, Data> data = new ConcurrentHashMap<>();

    private int limit = 300;
    private int[] warnings = new int[0];
    private int maxWarn;
    private double radiusSq;
    private final Map<String, Boolean> activity = new HashMap<>();
    private final Set<String> disabledWorlds = new HashSet<>();

    public AfkManager(AntiAfkPlugin plugin) { this.plugin = plugin; }

    public void load() {
        FileConfiguration c = plugin.getConfig();
        limit = Math.max(5, c.getInt("settings.idle-limit", 300));
        warnings = c.getIntegerList("settings.warnings").stream()
                .filter(i -> i > 0 && i < limit)
                .sorted(Comparator.reverseOrder())
                .mapToInt(Integer::intValue).toArray();
        maxWarn = warnings.length == 0 ? 0 : warnings[0];
        double r = Math.max(0, c.getDouble("settings.movement-radius", 3.0));
        radiusSq = r * r;
        disabledWorlds.clear();
        disabledWorlds.addAll(c.getStringList("settings.disabled-worlds"));
        activity.clear();
        for (String k : List.of("movement", "rotation", "chat", "commands", "interact", "blocks", "inventory", "held-item")) {
            boolean def = k.equals("movement") || k.equals("chat") || k.equals("commands");
            activity.put(k, c.getBoolean("activity." + k, def));
        }
    }

    // ------------------------------------------------------------------ activité (appelable depuis n'importe quel thread)

    public boolean act(String key) { return activity.getOrDefault(key, false); }

    public Data data(UUID id) { return data.computeIfAbsent(id, k -> new Data()); }

    public void touch(UUID id) { data(id).last = System.currentTimeMillis(); }

    public void remove(UUID id) { data.remove(id); }

    /** Téléportation : on déplace l'ancre SANS compter d'activité (sinon le warp AFK annulerait l'état AFK). */
    public void onTeleport(Player p, Location to) {
        data(p.getUniqueId()).anchor = to.clone();
    }

    /** Déplacement (thread principal). Ne compte que si on s'éloigne assez de l'ancre. */
    public void onMove(Player p, Location from, Location to) {
        Data d = data(p.getUniqueId());
        boolean moved = from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
        boolean rotated = from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch();

        if (rotated && act("rotation")) d.last = System.currentTimeMillis();
        if (!moved || !act("movement")) return;

        if (radiusSq <= 0 || d.anchor == null || !Objects.equals(d.anchor.getWorld(), to.getWorld())
                || d.anchor.distanceSquared(to) > radiusSq) {
            d.anchor = to.clone();
            d.last = System.currentTimeMillis();
        }
    }

    // ------------------------------------------------------------------ boucle principale (1x / seconde)

    public boolean isExempt(Player p) {
        FileConfiguration c = plugin.getConfig();
        if (p.hasPermission("antiafk.bypass")) return true;
        if (disabledWorlds.contains(p.getWorld().getName())) return true;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE && c.getBoolean("settings.ignore-creative", false)) return true;
        return gm == GameMode.SPECTATOR && c.getBoolean("settings.ignore-spectator", true);
    }

    public long idleSeconds(Player p) { return (System.currentTimeMillis() - data(p.getUniqueId()).last) / 1000; }
    public int limit() { return limit; }
    public boolean isAfk(Player p) { return data(p.getUniqueId()).afk; }

    public void tick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Data d = data(p.getUniqueId());
            if (isExempt(p)) { d.last = now; d.warned.clear(); d.afk = false; continue; }

            // Déjà envoyé en zone AFK : on attend une vraie activité avant de reprendre la surveillance
            if (d.afk) {
                if (d.last > d.afkSince) {
                    d.afk = false;
                    d.warned.clear();
                    p.sendMessage(plugin.msg("afk.left", placeholders(p, limit, 0)));
                }
                continue;
            }

            long idle = (now - d.last) / 1000;
            long remaining = limit - idle;

            if (remaining > maxWarn) {
                if (!d.warned.isEmpty()) {            // le joueur est revenu avant l'action
                    d.warned.clear();
                    p.sendMessage(plugin.msg("back", placeholders(p, remaining, idle)));
                }
                continue;
            }
            if (remaining <= 0) {
                boolean kicked = execute(p, idle);
                d.warned.clear();
                d.last = System.currentTimeMillis();
                if (!kicked) { d.afk = true; d.afkSince = d.last; }
                continue;
            }

            Map<String, String> ph = placeholders(p, remaining, idle);
            if (plugin.getConfig().getBoolean("warning.actionbar", true)) {
                p.sendActionBar(plugin.msg("warning.actionbar", ph));
            }
            Integer hit = null;
            for (int w : warnings) if (remaining <= w && (hit == null || w < hit)) hit = w;
            if (hit != null && !d.warned.contains(hit)) {
                for (int w : warnings) if (w >= hit) d.warned.add(w);
                warn(p, ph);
            }
        }
    }

    private void warn(Player p, Map<String, String> ph) {
        FileConfiguration c = plugin.getConfig();
        if (c.getBoolean("warning.chat", true)) p.sendMessage(plugin.msg("warning.chat", ph));
        if (c.getBoolean("warning.title", true)) {
            List<Integer> t = plugin.messages().getIntegerList("warning.title-times");
            if (t.size() < 3) t = List.of(5, 40, 10);
            p.showTitle(Title.title(plugin.msg("warning.title", ph), plugin.msg("warning.subtitle", ph),
                    Title.Times.times(Ticks.duration(t.get(0)), Ticks.duration(t.get(1)), Ticks.duration(t.get(2)))));
        }
        if (c.getBoolean("warning.sound.enabled", true)) {
            p.playSound(p.getLocation(), c.getString("warning.sound.key", "block.note_block.pling"),
                    (float) c.getDouble("warning.sound.volume", 1.0), (float) c.getDouble("warning.sound.pitch", 1.0));
        }
    }

    /** @return true si le joueur a été expulsé */
    private boolean execute(Player p, long idle) {
        Map<String, String> ph = placeholders(p, 0, idle);
        String type = plugin.getConfig().getString("action.type", "PLAYER").toUpperCase(Locale.ROOT);

        if (type.equals("KICK")) {
            String broadcast = plugin.messages().getString("kick.broadcast", "");
            p.kick(plugin.msg("kick.message", ph));
            if (!broadcast.isEmpty()) Bukkit.broadcast(plugin.text(broadcast, ph));
            return true;
        }

        for (String raw : plugin.getConfig().getStringList("action.commands")) {
            String cmd = raw.replace("{player}", p.getName());
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            if (type.equals("CONSOLE")) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            else p.performCommand(cmd);            // PLAYER : exécutée par le joueur (comme /warp zoneafk)
        }
        p.sendMessage(plugin.msg("afk.sent", ph));
        String broadcast = plugin.messages().getString("afk.broadcast", "");
        if (!broadcast.isEmpty()) Bukkit.broadcast(plugin.text(broadcast, ph));
        return false;
    }

    public Map<String, String> placeholders(Player p, long remaining, long idle) {
        Map<String, String> m = new HashMap<>();
        m.put("player", p.getName());
        m.put("seconds", String.valueOf(Math.max(0, remaining)));
        m.put("time", format(Math.max(0, remaining)));
        m.put("idle", format(idle));
        m.put("limit", format(limit));
        return m;
    }

    public static String format(long sec) {
        return sec >= 60 ? (sec / 60) + "m " + String.format("%02d", sec % 60) + "s" : sec + "s";
    }
}
