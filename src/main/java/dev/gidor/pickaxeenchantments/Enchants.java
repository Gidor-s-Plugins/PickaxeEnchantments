package dev.gidor.pickaxeenchantments;

import io.papermc.paper.registry.keys.EnchantmentKeys;
import io.papermc.paper.registry.TypedKey;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class Enchants {

    public static final String NAMESPACE = "pickaxeenchantments";

    public static final String SMELTING = "smelting";
    public static final String VEINMINER = "veinminer";
    public static final String HAMMER = "hammer";
    public static final String MAGNETIC = "magnetic";

    public static final List<String> NAMES = List.of(SMELTING, VEINMINER, HAMMER, MAGNETIC);

    private Enchants() {}

    public static TypedKey<Enchantment> key(String name) {
        return EnchantmentKeys.create(Key.key(NAMESPACE, name));
    }

    /** Runtime lookup (only valid after the registry has been frozen, i.e. after bootstrap). */
    public static Enchantment get(String name) {
        return Registry.ENCHANTMENT.get(new NamespacedKey(NAMESPACE, name));
    }

    public static boolean has(ItemStack item, String name) {
        Enchantment enchantment = get(name);
        return enchantment != null && item.getEnchantmentLevel(enchantment) > 0;
    }
}
