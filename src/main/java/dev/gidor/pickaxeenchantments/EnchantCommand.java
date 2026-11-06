package dev.gidor.pickaxeenchantments;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** /pickaxeenchant <enchant> | /pickaxeenchant remove <enchant> */
public final class EnchantCommand implements BasicCommand {

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (!(stack.getSender() instanceof Player player)) {
            stack.getSender().sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return;
        }

        boolean remove = args.length == 2 && args[0].equalsIgnoreCase("remove");
        if (args.length == 0 || args.length > 2 || (args.length == 2 && !remove)) {
            player.sendMessage(Component.text(
                    "Usage: /pe <smelting|veinminer|hammer|magnetic> | /pe remove <enchant>", NamedTextColor.YELLOW));
            return;
        }

        String name = args[args.length - 1].toLowerCase(Locale.ROOT);
        Enchantment enchantment = Enchants.NAMES.contains(name) ? Enchants.get(name) : null;
        if (enchantment == null) {
            player.sendMessage(Component.text("Unknown enchantment: " + name, NamedTextColor.RED));
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!Tag.ITEMS_PICKAXES.isTagged(item.getType())) {
            player.sendMessage(Component.text("Hold a pickaxe in your main hand.", NamedTextColor.RED));
            return;
        }

        if (remove) {
            item.removeEnchantment(enchantment);
            player.sendMessage(Component.text("Removed " + name + ".", NamedTextColor.GREEN));
            return;
        }

        for (Enchantment existing : item.getEnchantments().keySet()) {
            if (!existing.equals(enchantment) && enchantment.conflictsWith(existing)) {
                player.sendMessage(Component.text(name + " conflicts with an enchantment on this pickaxe.",
                        NamedTextColor.RED));
                return;
            }
        }

        item.addUnsafeEnchantment(enchantment, 1);
        player.sendMessage(Component.text("Added " + name + ".", NamedTextColor.GREEN));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length <= 1) {
            out.addAll(Enchants.NAMES);
            out.add("remove");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            out.addAll(Enchants.NAMES);
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.startsWith(prefix));
        return out;
    }

    @Override
    public @Nullable String permission() {
        return "pickaxeenchantments.command";
    }
}
