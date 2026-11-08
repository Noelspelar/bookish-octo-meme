package dev.dupeitem;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DupeItemPlugin extends JavaPlugin implements Listener {
    private final Map<UUID, Long> last = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private boolean dupe(Player p, int amount) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) { p.sendMessage("Hold an item first."); return false; }
        long cd = getConfig().getLong("cooldown-seconds", 0) * 1000L;
        long now = System.currentTimeMillis();
        if (cd > 0 && now - last.getOrDefault(p.getUniqueId(), 0L) < cd) {
            p.sendMessage("Wait a moment before duping again.");
            return false;
        }
        last.put(p.getUniqueId(), now);
        for (int i = 0; i < amount; i++) {
            p.getWorld().dropItemNaturally(p.getLocation().add(0, 1, 0), hand.clone());
        }
        return true;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!getConfig().getBoolean("sneak-right-click", true)) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!p.isSneaking() || !p.hasPermission("dupeitem.use")) return;
        if (p.getInventory().getItemInMainHand().getType().isAir()) return;
        e.setCancelled(true);
        dupe(p, 1);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        if (!p.hasPermission("dupeitem.use")) { p.sendMessage("No permission."); return true; }
        int amt = 1;
        if (a.length > 0) {
            try { amt = Integer.parseInt(a[0]); } catch (NumberFormatException ex) { p.sendMessage("Usage: /dupe [amount]"); return true; }
        }
        amt = Math.max(1, Math.min(amt, getConfig().getInt("max-amount", 64)));
        dupe(p, amt);
        return true;
    }
}
