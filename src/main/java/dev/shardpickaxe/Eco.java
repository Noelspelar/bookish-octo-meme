package dev.shardpickaxe;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Tiny built-in economy stored in balances.yml. */
public class Eco {
    private final ShardPickaxePlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public Eco(ShardPickaxePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balances.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public double get(UUID id) {
        return data.getDouble(id.toString(), plugin.getConfig().getDouble("economy.start-balance", 1000));
    }

    public void set(UUID id, double v) {
        data.set(id.toString(), Math.max(0, v));
        save();
    }

    public void add(UUID id, double v) { set(id, get(id) + v); }

    public boolean take(UUID id, double v) {
        if (get(id) < v) return false;
        set(id, get(id) - v);
        return true;
    }

    private void save() {
        try { data.save(file); } catch (IOException ex) { plugin.getLogger().warning("Could not save balances: " + ex.getMessage()); }
    }
}
