package dev.shardpickaxe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Small helpers + the "Ember" colour palette used by every menu. */
public final class Util {
    private Util() {}

    public static final TextColor EMBER  = TextColor.color(0xFF5A00);
    public static final TextColor GOLD   = TextColor.color(0xFFC247);
    public static final TextColor ASH    = TextColor.color(0x9A8F8A);
    public static final TextColor GREEN  = TextColor.color(0x7CE38B);
    public static final TextColor RED    = TextColor.color(0xFF5555);
    public static final TextColor WHITE  = TextColor.color(0xFFF3E6);

    public static Component c(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public static Component bold(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true);
    }

    public static ItemStack item(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name);
        if (lore != null && !lore.isEmpty()) meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack item(Material m, Component name, Component... lore) {
        List<Component> l = new ArrayList<>(List.of(lore));
        return item(m, name, l);
    }

    public static ItemStack filler(Material m) {
        return item(m, Component.text(" "));
    }

    public static String money(double v) {
        if (v == Math.rint(v)) return "$" + String.format("%,d", (long) v);
        return "$" + String.format("%,.2f", v);
    }
}
