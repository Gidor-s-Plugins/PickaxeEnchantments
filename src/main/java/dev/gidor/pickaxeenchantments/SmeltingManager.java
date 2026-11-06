package dev.gidor.pickaxeenchantments;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

/** Looks up vanilla furnace recipes for ore drops. */
public final class SmeltingManager {

    public record Result(ItemStack item, float experience) {}

    private final Map<Material, FurnaceRecipe> recipes = new EnumMap<>(Material.class);
    private boolean loaded = false;

    private void load() {
        recipes.clear();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            if (it.next() instanceof FurnaceRecipe furnace
                    && furnace.getInputChoice() instanceof RecipeChoice.MaterialChoice choice) {
                for (Material material : choice.getChoices()) {
                    recipes.putIfAbsent(material, furnace);
                }
            }
        }
        loaded = true;
    }

    /** Raw ores, ancient debris and (silk-touched) ore blocks. */
    private static boolean isSmeltable(Material m) {
        String n = m.name();
        return n.startsWith("RAW_") && !n.endsWith("_BLOCK")
                || n.endsWith("_ORE")
                || m == Material.ANCIENT_DEBRIS;
    }

    /** @return the smelted result, or null if the item isn't smeltable. */
    public Result smelt(ItemStack stack) {
        if (!isSmeltable(stack.getType())) return null;
        if (!loaded) load();
        FurnaceRecipe recipe = recipes.get(stack.getType());
        if (recipe == null) return null;

        ItemStack result = recipe.getResult().clone();
        result.setAmount(result.getAmount() * stack.getAmount());
        return new Result(result, recipe.getExperience() * stack.getAmount());
    }
}
