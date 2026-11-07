package dev.shardpickaxe;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

/** "The Forge" - main shop menu listing categories. */
public class ShopMenu extends Menu {
    private static final int[] SLOTS = {10, 12, 14, 16, 20, 22, 24};
    private final ShardPickaxePlugin plugin;
    private final List<ShopManager.Category> cats;

    public ShopMenu(ShardPickaxePlugin plugin, Player p) {
        this.plugin = plugin;
        this.cats = plugin.shop().categories();
        create(36, Util.bold("🔥 The Forge", Util.EMBER));
        frame();
        for (int i = 0; i < cats.size() && i < SLOTS.length; i++) {
            ShopManager.Category c = cats.get(i);
            inv.setItem(SLOTS[i], Util.item(c.icon(), Util.bold(c.name(), Util.GOLD),
                    Util.c(c.blurb(), Util.ASH),
                    net.kyori.adventure.text.Component.empty(),
                    Util.c(c.entries().size() + " items - click to browse", Util.EMBER)));
        }
        inv.setItem(31, Util.item(Material.GOLD_INGOT, Util.bold("Your purse", Util.GOLD),
                Util.c(Util.money(plugin.eco().get(p.getUniqueId())), Util.WHITE)));
    }

    @Override
    public void click(Player p, InventoryClickEvent e) {
        for (int i = 0; i < cats.size() && i < SLOTS.length; i++) {
            if (SLOTS[i] == e.getRawSlot()) {
                new ShopCategoryMenu(plugin, p, cats.get(i)).open(p);
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                return;
            }
        }
    }
}
