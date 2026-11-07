package dev.shardpickaxe;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HomeManager {
    public static final int HARD_MAX = 14;

    private final ShardPickaxePlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public HomeManager(ShardPickaxePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    /** Homes in creation order. */
    public Map<String, Location> homes(UUID id) {
        Map<String, Location> out = new LinkedHashMap<>();
        ConfigurationSection sec = data.getConfigurationSection(id.toString());
        if (sec == null) return out;
        for (String name : sec.getKeys(false)) {
            Location l = sec.getLocation(name);
            if (l != null && l.getWorld() != null) out.put(name, l);
        }
        return out;
    }

    public List<String> names(UUID id) { return new ArrayList<>(homes(id).keySet()); }

    public int maxHomes(Player p) {
        int max = plugin.getConfig().getInt("homes.default-max", 3);
        for (int i = HARD_MAX; i > max; i--) {
            if (p.hasPermission("shardpickaxe.homes." + i)) { max = i; break; }
        }
        return Math.min(max, HARD_MAX);
    }

    /** @return null on success, otherwise an error message. */
    public String set(Player p, String name) {
        name = name.toLowerCase();
        if (!name.matches("[a-z0-9_\\-]{1,16}")) return "Names may use a-z, 0-9, _ and - (max 16 characters).";
        Map<String, Location> cur = homes(p.getUniqueId());
        if (!cur.containsKey(name) && cur.size() >= maxHomes(p)) return "You've used all " + maxHomes(p) + " home slots.";
        data.set(p.getUniqueId() + "." + name, p.getLocation());
        save();
        return null;
    }

    public boolean delete(UUID id, String name) {
        String path = id + "." + name.toLowerCase();
        if (!data.contains(path)) return false;
        data.set(path, null);
        save();
        return true;
    }

    public Location get(UUID id, String name) { return homes(id).get(name.toLowerCase()); }

    private void save() {
        try { data.save(file); } catch (IOException ex) { plugin.getLogger().warning("Could not save homes: " + ex.getMessage()); }
    }
}
