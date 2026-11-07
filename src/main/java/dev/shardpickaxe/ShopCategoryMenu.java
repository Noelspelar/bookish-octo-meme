package dev.shardpickaxe;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopCategoryMenu extends Menu {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final ShardPickaxePlugin plugin;
    private final ShopManager.Category cat;

    public ShopCategoryMenu(ShardPickaxePlugin plugin, Player p, ShopManager.Category cat) {
        this.plugin = plugin;
        this.cat = cat;
        create(54, Util.bold(cat.name(), Util.EMBER));
        frame();

        for (int i = 0; i < cat.entries().size() && i < SLOTS.length; i++) {
            ShopManager.Entry en = cat.entries().get(i);
            List<Component> lore = new ArrayList<>();
            if (en.special()) {
                lore.add(Util.c("2% chance per block to summon", Util.GOLD));
                lore.add(Util.c("a Lava Minion that protects you.", Util.GOLD));
                lore.add(Component.empty());
            }
            if (en.buy() > 0) {
                lore.add(Util.c("Buy: ", Util.GREEN).append(Util.c(Util.money(en.buy()), Util.WHITE)));
                lore.add(Util.c("  Left-click = 1   |   Shift+Left = 64", Util.ASH));
            }
            if (en.sell() > 0) {
                lore.add(Util.c("Sell: ", Util.RED).append(Util.c(Util.money(en.sell()), Util.WHITE)));
                lore.add(Util.c("  Right-click = 1   |   Shift+Right = all", Util.ASH));
            }
            ItemStack display = en.special() ? plugin.createPickaxe() : new ItemStack(en.material());
            var meta = display.getItemMeta();
            if (!en.special()) meta.displayName(Util.c(pretty(en.material()), Util.WHITE));
            meta.lore(lore);
            display.setItemMeta(meta);
            inv.setItem(SLOTS[i], display);
        }

        inv.setItem(4, Util.item(cat.icon(), Util.bold(cat.name(), Util.GOLD), Util.c(cat.blurb(), Util.ASH)));
        inv.setItem(49, Util.item(Material.ARROW, Util.bold("« Back to the Forge", Util.EMBER)));
        inv.setItem(53, Util.item(Material.GOLD_INGOT, Util.bold("Your purse", Util.GOLD),
                Util.c(Util.money(plugin.eco().get(p.getUniqueId())), Util.WHITE)));
    }

    private static String pretty(Material m) {
        String[] parts = m.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String s : parts) sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(' ');
        return sb.toString().trim();
    }

    @Override
    public void click(Player p, InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot == 49) { new ShopMenu(plugin, p).open(p); return; }

        for (int i = 0; i < cat.entries().size() && i < SLOTS.length; i++) {
            if (SLOTS[i] != slot) continue;
            ShopManager.Entry en = cat.entries().get(i);
            ClickType t = e.getClick();
            if (t == ClickType.LEFT) buy(p, en, 1);
            else if (t == ClickType.SHIFT_LEFT) buy(p, en, en.special() ? 1 : 64);
            else if (t == ClickType.RIGHT) sell(p, en, 1);
            else if (t == ClickType.SHIFT_RIGHT) sell(p, en, -1);
            new ShopCategoryMenu(plugin, p, cat).open(p); // refresh purse
            return;
        }
    }

    private void buy(Player p, ShopManager.Entry en, int amount) {
        if (en.buy() <= 0) { p.sendMessage(Util.c("That item can't be bought.", Util.RED)); return; }
        double cost = en.buy() * amount;
        if (!plugin.eco().take(p.getUniqueId(), cost)) {
            p.sendMessage(Util.c("Not enough cash - need " + Util.money(cost) + ".", Util.RED));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        ItemStack stack = en.special() ? plugin.createPickaxe() : new ItemStack(en.material(), amount);
        p.getInventory().addItem(stack).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.1f);
        p.sendMessage(Util.c("Bought ", Util.GREEN).append(Util.c(amount + "x " + pretty(en.material()), Util.WHITE))
                .append(Util.c(" for " + Util.money(cost), Util.GOLD)));
    }

    private void sell(Player p, ShopManager.Entry en, int amount) {
        if (en.sell() <= 0 || en.special()) { p.sendMessage(Util.c("That item can't be sold.", Util.RED)); return; }
        int have = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() == en.material() && !it.hasItemMeta()) have += it.getAmount();
        }
        int sellAmt = amount < 0 ? have : Math.min(amount, have);
        if (sellAmt <= 0) {
            p.sendMessage(Util.c("You have none to sell.", Util.RED));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        int remaining = sellAmt;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType() != en.material() || it.hasItemMeta()) continue;
            int take = Math.min(remaining, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) p.getInventory().setItem(i, null);
            remaining -= take;
        }
        double gain = en.sell() * sellAmt;
        plugin.eco().add(p.getUniqueId(), gain);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.6f);
        p.sendMessage(Util.c("Sold ", Util.GREEN).append(Util.c(sellAmt + "x " + pretty(en.material()), Util.WHITE))
                .append(Util.c(" for " + Util.money(gain), Util.GOLD)));
    }
}
