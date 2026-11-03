package fr.antiafk;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;

public final class AntiAfkCommand implements TabExecutor {

    private final AntiAfkPlugin plugin;

    public AntiAfkCommand(AntiAfkPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("antiafk.admin")) { s.sendMessage(plugin.msg("no-permission", null)); return true; }
        AfkManager m = plugin.manager();

        if (a.length == 0 || a[0].equalsIgnoreCase("help")) { help(s); return true; }

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadAll();
                s.sendMessage(plugin.msg("reloaded", null));
            }
            case "check" -> {
                if (a.length < 2) { s.sendMessage(plugin.msg("usage-check", null)); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { s.sendMessage(plugin.msg("player-not-found", Map.of("name", a[1]))); return true; }
                if (m.isExempt(t)) { s.sendMessage(plugin.msg("check-exempt", Map.of("name", t.getName()))); return true; }
                s.sendMessage(plugin.msg("check", Map.of("name", t.getName(),
                        "idle", AfkManager.format(m.idleSeconds(t)), "limit", AfkManager.format(m.limitFor(t)))));
            }
            case "reset" -> {
                if (a.length < 2) { s.sendMessage(plugin.msg("usage-reset", null)); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { s.sendMessage(plugin.msg("player-not-found", Map.of("name", a[1]))); return true; }
                m.touch(t.getUniqueId());
                s.sendMessage(plugin.msg("reset-done", Map.of("name", t.getName())));
            }
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        for (String line : plugin.messages().getStringList("help")) s.sendMessage(plugin.text(line, null));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("antiafk.admin")) return List.of();
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("check", "reset", "reload", "help"));
        else if (a.length == 2 && !a[0].equalsIgnoreCase("reload")) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
