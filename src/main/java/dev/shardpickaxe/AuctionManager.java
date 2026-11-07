package dev.shardpickaxe;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Auction house storage (auctions.yml). */
public class AuctionManager {
    public static class Listing {
        public long id;
        public UUID seller;
        public String sellerName;
        public ItemStack item;
        public double price;
        public long expires;
    }

    private final ShardPickaxePlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<Long, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> claims = new LinkedHashMap<>();
    private long nextId = 1;

    public AuctionManager(ShardPickaxePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auctions.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        nextId = data.getLong("next", 1);
        ConfigurationSection ls = data.getConfigurationSection("listings");
        if (ls != null) {
            for (String key : ls.getKeys(false)) {
                ConfigurationSection s = ls.getConfigurationSection(key);
                if (s == null) continue;
                ItemStack it = s.getItemStack("item");
                if (it == null) continue;
                Listing l = new Listing();
                l.id = Long.parseLong(key);
                l.seller = UUID.fromString(s.getString("seller"));
                l.sellerName = s.getString("name", "?");
                l.item = it;
                l.price = s.getDouble("price");
                l.expires = s.getLong("expires");
                listings.put(l.id, l);
            }
        }
        ConfigurationSection cs = data.getConfigurationSection("claims");
        if (cs != null) {
            for (String key : cs.getKeys(false)) {
                List<ItemStack> items = new ArrayList<>();
                for (Object o : cs.getList(key, new ArrayList<>())) if (o instanceof ItemStack i) items.add(i);
                if (!items.isEmpty()) claims.put(UUID.fromString(key), items);
            }
        }
    }

    /** Moves expired listings into their seller's claim box. */
    public void purge() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Listing l : new ArrayList<>(listings.values())) {
            if (l.expires <= now) {
                listings.remove(l.id);
                claims.computeIfAbsent(l.seller, k -> new ArrayList<>()).add(l.item);
                changed = true;
            }
        }
        if (changed) save();
    }

    public List<Listing> all() {
        purge();
        List<Listing> out = new ArrayList<>(listings.values());
        java.util.Collections.reverse(out); // newest first
        return out;
    }

    public int countBy(UUID seller) {
        int n = 0;
        for (Listing l : listings.values()) if (l.seller.equals(seller)) n++;
        return n;
    }

    public void add(UUID seller, String name, ItemStack item, double price, long expires) {
        Listing l = new Listing();
        l.id = nextId++;
        l.seller = seller;
        l.sellerName = name;
        l.item = item;
        l.price = price;
        l.expires = expires;
        listings.put(l.id, l);
        save();
    }

    /** Removes and returns the listing, or null if it is already gone. */
    public Listing remove(long id) {
        Listing l = listings.remove(id);
        if (l != null) save();
        return l;
    }

    public void addClaim(UUID id, ItemStack item) {
        claims.computeIfAbsent(id, k -> new ArrayList<>()).add(item);
        save();
    }

    public List<ItemStack> takeClaims(UUID id) {
        List<ItemStack> l = claims.remove(id);
        if (l != null) save();
        return l == null ? new ArrayList<>() : l;
    }

    public int claimCount(UUID id) {
        List<ItemStack> l = claims.get(id);
        return l == null ? 0 : l.size();
    }

    private void save() {
        data.set("next", nextId);
        data.set("listings", null);
        for (Listing l : listings.values()) {
            String p = "listings." + l.id;
            data.set(p + ".seller", l.seller.toString());
            data.set(p + ".name", l.sellerName);
            data.set(p + ".item", l.item);
            data.set(p + ".price", l.price);
            data.set(p + ".expires", l.expires);
        }
        data.set("claims", null);
        for (Map.Entry<UUID, List<ItemStack>> e : claims.entrySet()) data.set("claims." + e.getKey(), e.getValue());
        try { data.save(file); } catch (IOException ex) { plugin.getLogger().warning("Could not save auctions: " + ex.getMessage()); }
    }
}
