package dev.gidor.pickaxeenchantments;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.event.RegistryComposeEvent;
import io.papermc.paper.registry.keys.tags.EnchantmentTagKeys;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemType;

/**
 * Registers the custom enchantments into the real enchantment registry so they
 * work with anvils, books, tooltips, /enchant, etc.
 */
@SuppressWarnings("UnstableApiUsage")
public class PickaxeEnchantmentsBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
                RegistryEvents.ENCHANTMENT.compose().newHandler(event -> {
                    RegistryKeySet<ItemType> pickaxes = event.getOrCreateTag(ItemTypeTagKeys.PICKAXES);

                    RegistryKeySet<Enchantment> notVeinminer =
                            RegistrySet.keySet(RegistryKey.ENCHANTMENT, Enchants.key(Enchants.VEINMINER));
                    RegistryKeySet<Enchantment> notHammer =
                            RegistrySet.keySet(RegistryKey.ENCHANTMENT, Enchants.key(Enchants.HAMMER));

                    register(event, pickaxes, Enchants.SMELTING, "Smelting", NamedTextColor.GOLD, null);
                    register(event, pickaxes, Enchants.MAGNETIC, "Magnetic", NamedTextColor.AQUA, null);
                    // Mutually exclusive
                    register(event, pickaxes, Enchants.VEINMINER, "Veinminer", NamedTextColor.GREEN, notHammer);
                    register(event, pickaxes, Enchants.HAMMER, "Hammer", NamedTextColor.RED, notVeinminer);
                })
        );

        // Make the enchantments appear in the enchanting table
        context.getLifecycleManager().registerEventHandler(
                LifecycleEvents.TAGS.postFlatten(RegistryKey.ENCHANTMENT),
                event -> event.registrar().addToTag(
                        EnchantmentTagKeys.IN_ENCHANTING_TABLE,
                        Enchants.NAMES.stream().map(Enchants::key).toList()
                )
        );
    }

    private static void register(
            RegistryComposeEvent<Enchantment, EnchantmentRegistryEntry.Builder> event,
            RegistryKeySet<ItemType> pickaxes,
            String name,
            String display,
            NamedTextColor color,
            RegistryKeySet<Enchantment> exclusive
    ) {
        event.registry().register(Enchants.key(name), builder -> {
            builder.description(Component.text(display, color))
                    .supportedItems(pickaxes)
                    .primaryItems(pickaxes)
                    .weight(2)
                    .maxLevel(1)
                    .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(20, 0))
                    .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(50, 0))
                    .anvilCost(4)
                    .activeSlots(EquipmentSlotGroup.MAINHAND);
            if (exclusive != null) {
                builder.exclusiveWith(exclusive);
            }
        });
    }
}
