package dev.shardpickaxe;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** "Hearth" - the home picker. */
public class HomesMenu extends Menu {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final Material[] BEDS = {
            Material.RED_BED, Material.ORANGE_BED, Material.YELLOW_BED, Material.LIME_BED,
            Material.CYAN_BED, Material.LIGHT_BLUE_BED, Material.PURPLE_BED, Material.MAGENTA_BED,
            Material.PINK_BED, Material.WHITE_BED, Material.GRAY_BED, Material.BROWN_BED,
            Material.GREEN_BED, Material.BLUE_BED
    };

    private final ShardPickaxePlugin plugin;
    private final List<String> names = new ArrayList<>();

    public HomesMenu(ShardPickaxePlugin plugin, Player p) {
        this.plugin = plugin;
        create(36, Util.bold("✦ Your Hearths", Util.EMBER));
        frame();

        Map<String, Location> homes = plugin.homes().homes(p.getUniqueId());
        names.addAll(homes.keySet());
        int max = plugin.homes().maxHomes(p);

        for (int i = 0; i < SLOTS.length; i++) {
            if (i < names.size()) {
                String n = names.get(i);
                Location l = homes.get(n);
                inv.setItem(SLOTS[i], Util.item(BEDS[i % BEDS.length], Util.bold(n, Util.GOLD),
                        Util.c("World: " + l.getWorld().getName(), Util.ASH),
                        Util.c(String.format("XYZ: %d, %d, %d", l.getBlockX(), l.getBlockY(), l.getBlockZ()), Util.ASH),
                        Component.empty(),
                        Util.c("Left-click: ", Util.GREEN).append(Util.c("teleport", Util.WHITE)),
                        Util.c("Shift + right-click: ", Util.RED).append(Util.c("delete", Util.WHITE))));
            } else if (i < max) {
                inv.setItem(SLOTS[i], Util.item(Material.GRAY_DYE, Util.c("Free slot", Util.ASH),
                        Util.c("Use the beacon below or /sethome <name>", Util.ASH)));
            } else {
                inv.setItem(SLOTS[i], Util.item(Material.RED_STAINED_GLASS_PANE, Util.c("Locked", Util.RED),
                        Util.c("Rank up for more homes.", Util.ASH)));
            }
        }

        inv.setItem(31, Util.item(Material.BEACON, Util.bold("Plant a hearth here", Util.EMBER),
                Util.c("Saves your current spot as a new home.", Util.ASH),
                Util.c(names.size() + " / " + max + " used", Util.GOLD)));
        inv.setItem(4, Util.item(Material.CAMPFIRE, Util.bold("Hearths", Util.EMBER),
                Util.c("Pick a bed to warp home.", Util.ASH)));
    }

    @Override
    public void click(Player p, InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot == 31) {
            int n = names.size() + 1;
            String name = "home" + n;
            while (names.contains(name)) name = "home" + (++n);
            String err = plugin.homes().set(p, name);
            if (err != null) { p.sendMessage(Util.c(err, Util.RED)); return; }
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.4f);
            p.sendMessage(Util.c("Hearth planted: ", Util.GREEN).append(Util.c(name, Util.GOLD)));
            new HomesMenu(plugin, p).open(p);
            return;
        }
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] != slot || i >= names.size()) continue;
            String name = names.get(i);
            if (e.getClick() == ClickType.SHIFT_RIGHT) {
                plugin.homes().delete(p.getUniqueId(), name);
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1f);
                p.sendMessage(Util.c("Hearth removed: ", Util.RED).append(Util.c(name, Util.GOLD)));
                new HomesMenu(plugin, p).open(p);
            } else if (e.getClick().isLeftClick()) {
                p.closeInventory();
                plugin.teleportHome(p, name);
            }
            return;
        }
    }
}
