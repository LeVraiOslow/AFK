package fr.antiafk;

import net.kyori.adventure.text.Component;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class AntiAfkPlugin extends JavaPlugin {

    private YamlConfiguration messages;
    private AfkManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) saveResource("messages.yml", false);

        manager = new AfkManager(this);
        reloadAll();

        getServer().getPluginManager().registerEvents(new ActivityListener(manager), this);
        getServer().getScheduler().runTaskTimer(this, manager::tick, 20L, 20L);

        PluginCommand cmd = getCommand("antiafk");
        if (cmd != null) {
            AntiAfkCommand exec = new AntiAfkCommand(this);
            cmd.setExecutor(exec);
            cmd.setTabCompleter(exec);
        }
    }

    public void reloadAll() {
        reloadConfig();
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        InputStream in = getResource("messages.yml");
        if (in != null) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
        manager.load();
    }

    public AfkManager manager() { return manager; }
    public YamlConfiguration messages() { return messages; }

    public Component text(String raw, Map<String, String> ph) {
        Map<String, String> map = new HashMap<>();
        if (ph != null) map.putAll(ph);
        map.putIfAbsent("prefix", messages.getString("prefix", ""));
        return Text.parse(raw, map);
    }

    public Component msg(String path, Map<String, String> ph) {
        return text(messages.getString(path, "&cMessage manquant : " + path), ph);
    }
}
