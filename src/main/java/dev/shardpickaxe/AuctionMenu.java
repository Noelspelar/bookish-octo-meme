package dev.shardpickaxe;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** The auction house GUI. */
public class AuctionMenu extends Menu {
    private static final int PER_PAGE = 45;

    private final ShardPickaxePlugin plugin;
    private final int page;
    private final List<AuctionManager.Listing> shown = new ArrayList<>();
    private final boolean hasNext;

    public AuctionMenu(ShardPickaxePlugin plugin, Player p, int page) {
        this.plugin = plugin;
        List<AuctionManager.Listing> all = plugin.auctions().all();
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.max(0, Math.min(page, pages - 1));
        create(54, Util.bold("✦ Auction House  (" + (this.page + 1) + "/" + pages + ")", Util.EMBER));

        int from = this.page * PER_PAGE;
        for (int i = from; i < Math.min(all.size(), from + PER_PAGE); i++) {
            AuctionManager.Listing l = all.get(i);
            shown.add(l);
            ItemStack it = l.item.clone();
            ItemMeta meta = it.getItemMeta();
            List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            long mins = Math.max(0, (l.expires - System.currentTimeMillis()) / 60000);
            lore.add(Component.empty());
            lore.add(Util.c("Price: ", Util.GOLD).append(Util.c(Util.money(l.price), Util.WHITE)));
            lore.add(Util.c("Seller: ", Util.GOLD).append(Util.c(l.sellerName, Util.WHITE)));
            lore.add(Util.c("Time left: " + (mins / 60) + "h " + (mins % 60) + "m", Util.ASH));
            if (l.seller.equals(p.getUniqueId())) {
                lore.add(Util.c("Shift + right-click: ", Util.RED).append(Util.c("take back", Util.WHITE)));
            } else {
                lore.add(Util.c("Left-click: ", Util.GREEN).append(Util.c("buy", Util.WHITE)));
            }
            meta.lore(lore);
            it.setItemMeta(meta);
            inv.setItem(i - from, it);
        }
        hasNext = all.size() > from + PER_PAGE;

        for (int i = 45; i < 54; i++) inv.setItem(i, Util.filler(Material.BLACK_STAINED_GLASS_PANE));
        if (this.page > 0) inv.setItem(45, Util.item(Material.ARROW, Util.bold("Previous page", Util.GOLD)));
        if (hasNext) inv.setItem(53, Util.item(Material.ARROW, Util.bold("Next page", Util.GOLD)));
        inv.setItem(49, Util.item(Material.GOLD_INGOT, Util.bold("Auction House", Util.EMBER),
                Util.c("Hold an item and type", Util.ASH),
                Util.c("/ah sell <price>", Util.WHITE),
                Util.c("Your purse: " + Util.money(plugin.eco().get(p.getUniqueId())), Util.GOLD),
                Util.c("Items to claim: " + plugin.auctions().claimCount(p.getUniqueId()) + "  (/ah claim)", Util.ASH)));
    }

    @Override
    public void click(Player p, InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot == 45 && page > 0) { new AuctionMenu(plugin, p, page - 1).open(p); return; }
        if (slot == 53 && hasNext) { new AuctionMenu(plugin, p, page + 1).open(p); return; }
        if (slot < 0 || slot >= PER_PAGE || slot >= shown.size()) return;
        AuctionManager.Listing l = shown.get(slot);

        if (l.seller.equals(p.getUniqueId())) {
            if (e.getClick() != ClickType.SHIFT_RIGHT) return;
            AuctionManager.Listing gone = plugin.auctions().remove(l.id);
            if (gone != null) {
                give(p, gone.item);
                p.sendMessage(Util.c("Listing taken back.", Util.GREEN));
            }
            new AuctionMenu(plugin, p, page).open(p);
            return;
        }
        if (!e.getClick().isLeftClick()) return;

        if (plugin.eco().get(p.getUniqueId()) < l.price) {
            p.sendMessage(Util.c("Not enough cash.", Util.RED));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        AuctionManager.Listing bought = plugin.auctions().remove(l.id);
        if (bought == null) {
            p.sendMessage(Util.c("Someone already bought that.", Util.RED));
            new AuctionMenu(plugin, p, page).open(p);
            return;
        }
        plugin.eco().take(p.getUniqueId(), bought.price);
        plugin.eco().add(bought.seller, bought.price);
        give(p, bought.item);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        p.sendMessage(Util.c("Bought for " + Util.money(bought.price) + ".", Util.GREEN));
        Player seller = plugin.getServer().getPlayer(bought.seller);
        if (seller != null) seller.sendMessage(Util.c(p.getName() + " bought your item for " + Util.money(bought.price) + ".", Util.GREEN));
        new AuctionMenu(plugin, p, page).open(p);
    }

    private static void give(Player p, ItemStack item) {
        p.getInventory().addItem(item).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
    }
}
