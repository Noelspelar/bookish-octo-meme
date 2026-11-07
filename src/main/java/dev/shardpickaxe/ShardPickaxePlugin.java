package dev.shardpickaxe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ShardPickaxePlugin extends org.bukkit.plugin.java.JavaPlugin implements Listener {

    /**
     * IMPORTANT: this text + colour must match the entry in the resource pack's
     * assets/minecraft/items/netherite_pickaxe.json (it uses the same small-caps / Cyrillic letters).
     */
    public static final String ITEM_NAME = "ѕʜᴀʀᴅ ᴘɪᴄᴋᴀхᴇ";
    public static final TextColor ITEM_COLOR = TextColor.color(0xFF5A00);

    private Eco eco;
    private HomeManager homeManager;
    private ShopManager shopManager;

    private NamespacedKey itemKey;
    private NamespacedKey ownerKey;
    private NamespacedKey lavaKey;
    private boolean minionMining = false;

    /** owner UUID -> minion UUIDs (oldest first) */
    private final Map<UUID, Deque<UUID>> minions = new HashMap<>();
    /** owner UUID -> last living entity that hurt them */
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "shard_pickaxe");
        ownerKey = new NamespacedKey(this, "minion_owner");
        lavaKey = new NamespacedKey(this, "lava_ball");
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        eco = new Eco(this);
        homeManager = new HomeManager(this);
        shopManager = new ShopManager(this);
    }

    public Eco eco() { return eco; }
    public HomeManager homes() { return homeManager; }
    public ShopManager shop() { return shopManager; }

    @Override
    public void onDisable() {
        for (Deque<UUID> dq : minions.values()) {
            for (UUID id : dq) {
                Entity e = getServer().getEntity(id);
                if (e != null) e.remove();
            }
        }
        minions.clear();
    }

    // ------------------------------------------------------------------ item

    public ItemStack createPickaxe() {
        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(ITEM_NAME, ITEM_COLOR).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text("Forged in lava.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("2% chance on break to summon", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("a Lava Minion that protects you.", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        int eff = getConfig().getInt("item.efficiency", 5);
        int unb = getConfig().getInt("item.unbreaking", 3);
        if (eff > 0) meta.addEnchant(Enchantment.EFFICIENCY, eff, true);
        if (unb > 0) meta.addEnchant(Enchantment.UNBREAKING, unb, true);
        if (getConfig().getBoolean("item.mending", true)) meta.addEnchant(Enchantment.MENDING, 1, true);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isShardPickaxe(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_PICKAXE || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------- commands

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "home" -> { return cmdHome(sender, args); }
            case "sethome" -> { return cmdSetHome(sender, args); }
            case "delhome" -> { return cmdDelHome(sender, args); }
            case "shop" -> { return cmdShop(sender); }
            case "lavapickaxe" -> {
                if (!(sender instanceof Player lp)) { sender.sendMessage(Component.text("Players only.", NamedTextColor.RED)); return true; }
                if (!lp.hasPermission("shardpickaxe.admin") && lp.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    lp.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return true;
                }
                lp.getInventory().addItem(createPickaxe()).values()
                        .forEach(left -> lp.getWorld().dropItemNaturally(lp.getLocation(), left));
                lp.sendMessage(Component.text("Here is your Shard Pickaxe.", NamedTextColor.GREEN));
                return true;
            }
            case "balance" -> { return cmdBalance(sender, args); }
            case "pay" -> { return cmdPay(sender, args); }
            default -> { }
        }
        if (!sender.hasPermission("shardpickaxe.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(Component.text("ShardPickaxe config reloaded.", NamedTextColor.GREEN));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            Player target = null;
            if (args.length >= 2) {
                target = getServer().getPlayerExact(args[1]);
            } else if (sender instanceof Player p) {
                target = p;
            }
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
            final Player tg = target;
            tg.getInventory().addItem(createPickaxe()).values()
                    .forEach(left -> tg.getWorld().dropItemNaturally(tg.getLocation(), left));
            sender.sendMessage(Component.text("Gave Shard Pickaxe to " + target.getName() + ".", NamedTextColor.GREEN));
            return true;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("eco")) {
            Player t = getServer().getPlayerExact(args[2]);
            if (t == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return true; }
            double amt;
            try { amt = Double.parseDouble(args[3]); } catch (NumberFormatException ex) { sender.sendMessage(Component.text("Invalid amount.", NamedTextColor.RED)); return true; }
            if (args[1].equalsIgnoreCase("give")) eco.add(t.getUniqueId(), amt);
            else if (args[1].equalsIgnoreCase("set")) eco.set(t.getUniqueId(), amt);
            else { sender.sendMessage(Component.text("Usage: /shardpickaxe eco <give|set> <player> <amount>", NamedTextColor.YELLOW)); return true; }
            sender.sendMessage(Component.text(t.getName() + " now has " + Util.money(eco.get(t.getUniqueId())), NamedTextColor.GREEN));
            return true;
        }
        sender.sendMessage(Component.text("Usage: /shardpickaxe <give [player] | reload | eco <give|set> <player> <amt>>", NamedTextColor.YELLOW));
        return true;
    }

    // ----------------------------------------------------- homes / shop / eco

    private Player playerOnly(CommandSender s) {
        if (s instanceof Player p) return p;
        s.sendMessage("Players only.");
        return null;
    }

    private boolean cmdHome(CommandSender s, String[] args) {
        Player p = playerOnly(s);
        if (p == null) return true;
        if (args.length == 0) { new HomesMenu(this, p).open(p); return true; }
        teleportHome(p, args[0]);
        return true;
    }

    private boolean cmdSetHome(CommandSender s, String[] args) {
        Player p = playerOnly(s);
        if (p == null) return true;
        String name = args.length > 0 ? args[0] : "home";
        String err = homeManager.set(p, name);
        if (err != null) p.sendMessage(Util.c(err, Util.RED));
        else p.sendMessage(Util.c("Hearth planted: ", Util.GREEN).append(Util.c(name.toLowerCase(), Util.GOLD)));
        return true;
    }

    private boolean cmdDelHome(CommandSender s, String[] args) {
        Player p = playerOnly(s);
        if (p == null) return true;
        if (args.length == 0) { p.sendMessage(Util.c("Usage: /delhome <name>", Util.RED)); return true; }
        if (homeManager.delete(p.getUniqueId(), args[0])) p.sendMessage(Util.c("Hearth removed: " + args[0].toLowerCase(), Util.RED));
        else p.sendMessage(Util.c("No home called " + args[0] + ".", Util.RED));
        return true;
    }

    private boolean cmdShop(CommandSender s) {
        Player p = playerOnly(s);
        if (p == null) return true;
        new ShopMenu(this, p).open(p);
        return true;
    }

    private boolean cmdBalance(CommandSender s, String[] args) {
        Player p = playerOnly(s);
        if (p == null) return true;
        p.sendMessage(Util.c("Purse: ", Util.GOLD).append(Util.c(Util.money(eco.get(p.getUniqueId())), Util.WHITE)));
        return true;
    }

    private boolean cmdPay(CommandSender s, String[] args) {
        Player p = playerOnly(s);
        if (p == null) return true;
        if (args.length < 2) { p.sendMessage(Util.c("Usage: /pay <player> <amount>", Util.RED)); return true; }
        Player t = getServer().getPlayerExact(args[0]);
        if (t == null || t.equals(p)) { p.sendMessage(Util.c("Player not found.", Util.RED)); return true; }
        double amt;
        try { amt = Double.parseDouble(args[1]); } catch (NumberFormatException ex) { p.sendMessage(Util.c("Invalid amount.", Util.RED)); return true; }
        if (amt <= 0 || !eco.take(p.getUniqueId(), amt)) { p.sendMessage(Util.c("Not enough cash.", Util.RED)); return true; }
        eco.add(t.getUniqueId(), amt);
        p.sendMessage(Util.c("Sent " + Util.money(amt) + " to " + t.getName() + ".", Util.GREEN));
        t.sendMessage(Util.c(p.getName() + " sent you " + Util.money(amt) + ".", Util.GREEN));
        return true;
    }

    /** Teleport with a stand-still warmup. */
    public void teleportHome(Player p, String name) {
        Location dest = homeManager.get(p.getUniqueId(), name);
        if (dest == null) { p.sendMessage(Util.c("No home called " + name + ".", Util.RED)); return; }
        int warm = getConfig().getInt("homes.warmup-seconds", 3);
        if (warm <= 0) { p.teleport(dest); return; }
        Location start = p.getLocation().clone();
        p.sendMessage(Util.c("Warping to ", Util.GOLD).append(Util.c(name.toLowerCase(), Util.WHITE))
                .append(Util.c(" - stand still for " + warm + "s...", Util.GOLD)));
        new BukkitRunnable() {
            int left = warm;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                Location now = p.getLocation();
                if (now.getWorld() != start.getWorld() || now.distanceSquared(start) > 0.6) {
                    p.sendActionBar(Util.c("Warp cancelled - you moved!", Util.RED));
                    cancel();
                    return;
                }
                if (left-- <= 0) {
                    p.getWorld().spawnParticle(Particle.FLAME, now.add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.02);
                    p.teleport(dest);
                    p.getWorld().spawnParticle(Particle.FLAME, dest.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.02);
                    p.playSound(dest, Sound.ITEM_FIRECHARGE_USE, 1f, 1f);
                    cancel();
                } else {
                    p.sendActionBar(Util.c("Warping in " + (left + 1) + "...", Util.EMBER));
                    p.getWorld().spawnParticle(Particle.FLAME, now.add(0, 0.2, 0), 6, 0.3, 0.1, 0.3, 0.01);
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String n = cmd.getName().toLowerCase();
        if (sender instanceof Player p && args.length == 1 && (n.equals("home") || n.equals("delhome"))) {
            return homeManager.names(p.getUniqueId()).stream().filter(x -> x.startsWith(args[0].toLowerCase())).toList();
        }
        if (n.equals("shardpickaxe") && args.length == 1) return java.util.List.of("give", "reload", "eco");
        return null;
    }

    // ------------------------------------------------------------ the 2% roll

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (minionMining) return; // blocks broken by minions never roll for new minions
        Player p = e.getPlayer();
        if (!isShardPickaxe(p.getInventory().getItemInMainHand())) return;

        double chance = getConfig().getDouble("chance-percent", 2.0) / 100.0;
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            spawnMinion(p, e.getBlock().getLocation().add(0.5, 1, 0.5));
        }
    }

    // ---------------------------------------------------------------- minion

    private boolean isMinion(Entity e) {
        return e != null && e.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING);
    }

    private UUID ownerOf(Entity e) {
        String s = e.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return s == null ? null : UUID.fromString(s);
    }

    private void spawnMinion(Player owner, Location loc) {
        int max = getConfig().getInt("minion.max-per-player", 3);
        Deque<UUID> dq = minions.computeIfAbsent(owner.getUniqueId(), k -> new ArrayDeque<>());
        while (dq.size() >= max) {
            Entity old = getServer().getEntity(dq.pollFirst());
            if (old != null) poof(old);
        }

        int size = Math.max(1, Math.min(3, getConfig().getInt("minion.size", 2)));
        double hp = getConfig().getDouble("minion.health", 40.0);
        String name = getConfig().getString("minion.name", "Lava Minion");

        WitherSkeleton mc = loc.getWorld().spawn(loc, WitherSkeleton.class, m -> {
            m.setCustomNameVisible(true);
            m.setGlowing(true);
            var sc = m.getAttribute(Attribute.SCALE);
            if (sc != null) sc.setBaseValue(1.0 + size * 0.2);
            m.getEquipment().clear();
            m.setCanPickupItems(false);
            m.setPersistent(false);
            m.setRemoveWhenFarAway(true);
            m.customName(Component.text(name, TextColor.color(0xFF5A00)));
            m.setCustomNameVisible(true);
            m.getAttribute(Attribute.MAX_HEALTH).setBaseValue(hp);
            m.setHealth(hp);
            m.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        });
        dq.addLast(mc.getUniqueId());

        mc.getEquipment().clear();
        loc.getWorld().spawnParticle(Particle.LAVA, loc, 25, 0.4, 0.4, 0.4, 0);
        loc.getWorld().playSound(loc, Sound.BLOCK_LAVA_POP, 1f, 0.8f);
        owner.sendActionBar(Component.text("A Lava Minion rises to protect you!", ITEM_COLOR));

        runMinionAI(owner.getUniqueId(), mc);
    }

    private void runMinionAI(UUID ownerId, WitherSkeleton mc) {
        final int lifetimeTicks = getConfig().getInt("minion.lifetime-seconds", 30) * 20;
        final double radius = getConfig().getDouble("minion.defend-radius", 12);

        new BukkitRunnable() {
            int age = 0;
            int throwCd = 0;
            int mineCd = 0;

            @Override
            public void run() {
                Player owner = getServer().getPlayer(ownerId);
                if (!mc.isValid() || owner == null || !owner.isOnline() || owner.isDead()
                        || !owner.getWorld().equals(mc.getWorld()) || age >= lifetimeTicks) {
                    cancel();
                    forget(ownerId, mc.getUniqueId());
                    if (mc.isValid()) poof(mc);
                    return;
                }
                age += 5;

                // keep the target valid
                LivingEntity current = mc.getTarget();
                if (current != null && (!current.isValid() || current.getLocation().distanceSquared(owner.getLocation()) > radius * radius * 2.25)) {
                    mc.setTarget(null);
                    current = null;
                }

                // 1) retaliate against whoever last hurt the owner
                if (current == null) {
                    UUID att = lastAttacker.get(ownerId);
                    if (att != null) {
                        Entity a = getServer().getEntity(att);
                        if (a instanceof LivingEntity le && le.isValid() && !isMinion(le) && !le.getUniqueId().equals(ownerId)
                                && le.getLocation().distanceSquared(owner.getLocation()) <= radius * radius) {
                            mc.setTarget(le);
                            current = le;
                        }
                    }
                }

                // 2) otherwise pick the nearest hostile mob near the owner
                if (current == null) {
                    LivingEntity best = null;
                    double bestD = Double.MAX_VALUE;
                    for (Entity en : owner.getNearbyEntities(radius, radius, radius)) {
                        if (en instanceof Monster m && m.isValid() && !isMinion(m)) {
                            double d = m.getLocation().distanceSquared(owner.getLocation());
                            if (d < bestD) { bestD = d; best = m; }
                        }
                    }
                    if (best != null) mc.setTarget(best);
                }

                // 3) follow the owner when idle
                if (mc.getTarget() == null) {
                    double d = mc.getLocation().distanceSquared(owner.getLocation());
                    if (d > 24 * 24) {
                        mc.teleport(owner.getLocation());
                    } else if (d > 5 * 5) {
                        mc.getPathfinder().moveTo(owner.getLocation(), 1.3);
                    }
                }

                // 4) throw lava from a distance
                if (throwCd > 0) throwCd -= 5;
                LivingEntity tgt = mc.getTarget();
                if (tgt != null && throwCd <= 0 && mc.hasLineOfSight(tgt)) {
                    double d2 = tgt.getLocation().distanceSquared(mc.getLocation());
                    if (d2 > 3 * 3 && d2 < 16 * 16) {
                        throwLava(mc, tgt);
                        throwCd = getConfig().getInt("minion.throw-cooldown-ticks", 40);
                    }
                }

                // 5) help mine: when idle, break blocks near the owner
                if (mineCd > 0) mineCd -= 5;
                if (mc.getTarget() == null && mineCd <= 0 && getConfig().getBoolean("minion.mining", true)) {
                    mineCd = getConfig().getInt("minion.mine-interval-ticks", 20);
                    mineNearby(owner, mc);
                }

                // glowing magma cracks
                Location body = mc.getLocation().add(0, 1.1, 0);
                mc.getWorld().spawnParticle(Particle.FLAME, body, 4, 0.3, 0.5, 0.3, 0.01);
                mc.getWorld().spawnParticle(Particle.DRIPPING_LAVA, body, 2, 0.35, 0.6, 0.35, 0);
            }
        }.runTaskTimer(this, 5L, 5L);
    }

    /** Mines one random pickaxe-breakable block near the minion (respects protection plugins via BlockBreakEvent). */
    private void mineNearby(Player owner, WitherSkeleton mc) {
        int r = getConfig().getInt("minion.mine-radius", 4);
        Location c = mc.getLocation();
        java.util.concurrent.ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 12; i++) {
            org.bukkit.block.Block b = c.getWorld().getBlockAt(
                    c.getBlockX() + rnd.nextInt(-r, r + 1),
                    c.getBlockY() + rnd.nextInt(-1, r),
                    c.getBlockZ() + rnd.nextInt(-r, r + 1));
            Material m = b.getType();
            if (m.isAir() || b.isLiquid() || m.getHardness() < 0 || !org.bukkit.Tag.MINEABLE_PICKAXE.isTagged(m)
                    || b.getState() instanceof org.bukkit.block.TileState) continue;
            if (b.getLocation().distanceSquared(owner.getLocation()) > 14 * 14) continue;
            BlockBreakEvent ev = new BlockBreakEvent(b, owner);
            minionMining = true;
            try { getServer().getPluginManager().callEvent(ev); } finally { minionMining = false; }
            if (ev.isCancelled()) continue;
            b.getWorld().spawnParticle(Particle.FLAME, b.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0.02);
            b.breakNaturally(owner.getInventory().getItemInMainHand());
            b.getWorld().playSound(b.getLocation(), Sound.BLOCK_STONE_BREAK, 1f, 0.8f);
            return;
        }
    }

    private void throwLava(WitherSkeleton mc, LivingEntity target) {
        Location from = mc.getEyeLocation();
        Vector dir = target.getEyeLocation().toVector().subtract(from.toVector()).normalize();
        SmallFireball ball = mc.getWorld().spawn(from.add(dir.clone().multiply(0.8)), SmallFireball.class, f -> {
            f.setShooter(mc);
            f.setIsIncendiary(false);
            f.setYield(0);
            f.setDirection(dir);
            f.getPersistentDataContainer().set(lavaKey, PersistentDataType.BYTE, (byte) 1);
        });
        mc.getWorld().playSound(from, Sound.ITEM_BUCKET_EMPTY_LAVA, 1f, 1.2f);
        mc.getWorld().spawnParticle(Particle.LAVA, from, 8, 0.2, 0.2, 0.2, 0);
    }

    /** Lava balls set their victim on fire for a short time. */
    @EventHandler
    public void onLavaHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof SmallFireball f)
                || !f.getPersistentDataContainer().has(lavaKey, PersistentDataType.BYTE)) return;
        Location l = f.getLocation();
        l.getWorld().spawnParticle(Particle.LAVA, l, 20, 0.4, 0.4, 0.4, 0);
        l.getWorld().playSound(l, Sound.BLOCK_LAVA_POP, 1f, 0.8f);
        Entity hit = e.getHitEntity();
        if (hit instanceof LivingEntity le && !isMinion(le)
                && !(f.getShooter() instanceof Entity sh && le.getUniqueId().equals(ownerOf(sh)))) {
            le.setFireTicks(getConfig().getInt("minion.burn-ticks", 80));
        }
    }

    private void forget(UUID ownerId, UUID minionId) {
        Deque<UUID> dq = minions.get(ownerId);
        if (dq != null) {
            dq.remove(minionId);
            if (dq.isEmpty()) minions.remove(ownerId);
        }
    }

    private void poof(Entity e) {
        Location l = e.getLocation().add(0, 0.5, 0);
        l.getWorld().spawnParticle(Particle.SMOKE, l, 20, 0.3, 0.3, 0.3, 0.02);
        l.getWorld().playSound(l, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1f);
        e.remove();
    }

    // ---------------------------------------------------------------- events

    /** Minions never target their owner, other minions, or random players. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!isMinion(e.getEntity())) return;
        LivingEntity t = e.getTarget();
        if (t == null) return;
        UUID ownerId = ownerOf(e.getEntity());
        if (isMinion(t) || t.getUniqueId().equals(ownerId)) {
            e.setCancelled(true);
            return;
        }
        if (t instanceof Player) {
            // only fight a player if they were the last one to hit the owner
            UUID att = lastAttacker.get(ownerId);
            if (att == null || !att.equals(t.getUniqueId())) e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) damager = shooter;

        // no friendly fire between owner and minion
        if (isMinion(e.getEntity()) && ownerOf(e.getEntity()).equals(damager.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (isMinion(damager) && e.getEntity().getUniqueId().equals(ownerOf(damager))) {
            e.setCancelled(true);
            return;
        }

        // remember who hurt a player so their minions can retaliate
        if (e.getEntity() instanceof Player victim && damager instanceof LivingEntity attacker && !isMinion(attacker)) {
            lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
            Deque<UUID> dq = minions.get(victim.getUniqueId());
            if (dq != null) {
                for (UUID id : new ArrayList<>(dq)) {
                    if (getServer().getEntity(id) instanceof WitherSkeleton m) m.setTarget(attacker);
                }
            }
        }
        // also help when the owner attacks something
        if (damager instanceof Player owner && e.getEntity() instanceof LivingEntity victim && !isMinion(victim)) {
            Deque<UUID> dq = minions.get(owner.getUniqueId());
            if (dq != null && !(victim instanceof Player)) {
                for (UUID id : new ArrayList<>(dq)) {
                    if (getServer().getEntity(id) instanceof WitherSkeleton m && m.getTarget() == null) m.setTarget(victim);
                }
            }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!isMinion(e.getEntity())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        forget(ownerOf(e.getEntity()), e.getEntity().getUniqueId());
    }
}
