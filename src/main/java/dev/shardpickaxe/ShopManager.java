package dev.shardpickaxe;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ShopManager {
    public record Entry(String key, Material material, double buy, double sell) {
        public boolean special() { return key.equals("SHARD_PICKAXE"); }
    }

    public record Category(String id, String name, Material icon, String blurb, List<Entry> entries) {}

    private final List<Category> categories = new ArrayList<>();

    public ShopManager(ShardPickaxePlugin plugin) {
        File f = new File(plugin.getDataFolder(), "shop.yml");
        if (!f.exists()) plugin.saveResource("shop.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection cats = y.getConfigurationSection("categories");
        if (cats == null) return;
        for (String id : cats.getKeys(false)) {
            ConfigurationSection c = cats.getConfigurationSection(id);
            Material icon = Material.matchMaterial(c.getString("icon", "CHEST"));
            if (icon == null) icon = Material.CHEST;
            List<Entry> entries = new ArrayList<>();
            ConfigurationSection items = c.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    ConfigurationSection it = items.getConfigurationSection(key);
                    Material m = key.equals("SHARD_PICKAXE") ? Material.NETHERITE_PICKAXE : Material.matchMaterial(key);
                    if (m == null) { plugin.getLogger().warning("Unknown shop material: " + key); continue; }
                    entries.add(new Entry(key, m, it.getDouble("buy", 0), it.getDouble("sell", 0)));
                }
            }
            categories.add(new Category(id, c.getString("name", id), icon, c.getString("blurb", ""), entries));
        }
    }

    public List<Category> categories() { return categories; }
}
