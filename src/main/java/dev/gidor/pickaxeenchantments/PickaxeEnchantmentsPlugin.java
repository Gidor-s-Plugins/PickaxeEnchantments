package dev.gidor.pickaxeenchantments;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class PickaxeEnchantmentsPlugin extends JavaPlugin {

    private SmeltingManager smeltingManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.smeltingManager = new SmeltingManager();

        getServer().getPluginManager().registerEvents(new BreakListener(this), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        "pickaxeenchant",
                        "Add or remove pickaxe enchantments on the held item",
                        List.of("pe"),
                        new EnchantCommand()
                ));
    }

    public SmeltingManager smeltingManager() {
        return smeltingManager;
    }
}
