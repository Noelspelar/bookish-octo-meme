package dev.shardpickaxe;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Base class for all GUIs. The holder lets the listener know which menu a click belongs to. */
public abstract class Menu implements InventoryHolder {
    protected Inventory inv;

    protected void create(int size, Component title) {
        inv = Bukkit.createInventory(this, size, title);
    }

    /** Paints an ember-coloured frame around the inventory. */
    protected void frame() {
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            boolean edge = i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8;
            if (edge) inv.setItem(i, Util.filler(i % 2 == 0 ? Material.ORANGE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE));
        }
    }

    public void open(Player p) { p.openInventory(inv); }

    public abstract void click(Player p, InventoryClickEvent e);

    @Override public Inventory getInventory() { return inv; }
}
