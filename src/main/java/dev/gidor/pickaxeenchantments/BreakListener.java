package dev.gidor.pickaxeenchantments;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class BreakListener implements Listener {

    private record Drop(Location location, ItemStack item) {}

    private final PickaxeEnchantmentsPlugin plugin;
    /** Players whose extra blocks are currently being broken (prevents recursion). */
    private final Set<UUID> processing = new HashSet<>();

    public BreakListener(PickaxeEnchantmentsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (processing.contains(player.getUniqueId())) return;

        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!Tag.ITEMS_PICKAXES.isTagged(tool.getType())) return;

        boolean smelting = Enchants.has(tool, Enchants.SMELTING);
        boolean magnetic = Enchants.has(tool, Enchants.MAGNETIC);
        boolean hammer = Enchants.has(tool, Enchants.HAMMER);
        boolean veinminer = Enchants.has(tool, Enchants.VEINMINER);

        Block origin = event.getBlock();
        // Containers drop their contents in vanilla logic - leave them alone.
        if (origin.getState(false) instanceof Container) return;

        boolean requireSneak = plugin.getConfig().getBoolean("veinminer.require-sneaking", true);
        boolean hammerActive = hammer;
        boolean veinActive = !hammer && veinminer
                && (!requireSneak || player.isSneaking())
                && isOre(origin.getType());

        if (!smelting && !magnetic && !hammerActive && !veinActive) return;

        boolean silk = tool.containsEnchantment(Enchantment.SILK_TOUCH);
        List<Drop> drops = new ArrayList<>();
        double xp = 0;

        // ---- origin block (vanilla breaks it, we take over drops) ----
        Location originCenter = origin.getLocation().add(0.5, 0.5, 0.5);
        if (event.isDropItems()) {
            for (ItemStack item : origin.getDrops(tool, player)) {
                drops.add(new Drop(originCenter, item));
            }
        }
        xp += event.getExpToDrop();
        event.setDropItems(false);
        event.setExpToDrop(0);

        // ---- extra blocks ----
        List<Block> extras = List.of();
        if (hammerActive) {
            extras = hammerBlocks(player, origin);
        } else if (veinActive) {
            extras = veinBlocks(origin);
        }

        processing.add(player.getUniqueId());
        try {
            for (Block block : extras) {
                BlockBreakEvent extra = new BlockBreakEvent(block, player);
                Bukkit.getPluginManager().callEvent(extra);
                if (extra.isCancelled()) continue;

                Location center = block.getLocation().add(0.5, 0.5, 0.5);
                if (extra.isDropItems()) {
                    for (ItemStack item : block.getDrops(tool, player)) {
                        drops.add(new Drop(center, item));
                    }
                }
                xp += extra.getExpToDrop();
                if (!silk) xp += oreExperience(block.getType());

                breakEffect(block);
                block.setType(Material.AIR, true);
            }
        } finally {
            processing.remove(player.getUniqueId());
        }

        // ---- hammer: 3x durability total (vanilla already removes 1, add 2) ----
        if (hammerActive) {
            player.getInventory().setItemInMainHand(tool.damage(2, player));
        }

        // ---- smelting ----
        if (smelting) {
            boolean giveXp = plugin.getConfig().getBoolean("smelting.give-experience", true);
            List<Drop> smelted = new ArrayList<>(drops.size());
            for (Drop drop : drops) {
                SmeltingManager.Result result = plugin.smeltingManager().smelt(drop.item());
                if (result == null) {
                    smelted.add(drop);
                } else {
                    smelted.add(new Drop(drop.location(), result.item()));
                    if (giveXp) xp += result.experience();
                }
            }
            drops = smelted;
        }

        // ---- deliver ----
        for (Drop drop : drops) {
            World world = drop.location().getWorld();
            if (magnetic) {
                // Anything that doesn't fit gets dropped on the ground
                player.getInventory().addItem(drop.item().clone())
                        .values()
                        .forEach(left -> world.dropItemNaturally(drop.location(), left));
            } else {
                world.dropItemNaturally(drop.location(), drop.item());
            }
        }

        int totalXp = (int) xp;
        if (ThreadLocalRandom.current().nextDouble() < xp - totalXp) totalXp++;
        final int xpAmount = totalXp;
        if (xpAmount > 0) {
            if (magnetic) {
                player.giveExp(xpAmount, true);
            } else {
                originCenter.getWorld().spawn(originCenter, ExperienceOrb.class, orb -> orb.setExperience(xpAmount));
            }
        }
    }

    // ------------------------------------------------------------------ hammer

    private List<Block> hammerBlocks(Player player, Block origin) {
        BlockFace face = resolveFace(player, origin);
        boolean mineableOnly = plugin.getConfig().getBoolean("hammer.pickaxe-mineable-only", true);

        List<Block> blocks = new ArrayList<>(8);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue;
                int dx, dy, dz;
                if (face.getModX() != 0) {
                    dx = 0; dy = i; dz = j;
                } else if (face.getModY() != 0) {
                    dx = i; dy = 0; dz = j;
                } else {
                    dx = i; dy = j; dz = 0;
                }
                Block target = origin.getRelative(dx, dy, dz);
                if (canHammer(target, mineableOnly)) blocks.add(target);
            }
        }
        return blocks;
    }

    private static boolean canHammer(Block block, boolean mineableOnly) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid()) return false;
        if (type == Material.BEDROCK || type == Material.BARRIER) return false;
        if (type.getHardness() < 0) return false; // other unbreakable blocks
        if (mineableOnly && !Tag.MINEABLE_PICKAXE.isTagged(type)) return false;
        return !(block.getState(false) instanceof Container);
    }

    private static BlockFace resolveFace(Player player, Block origin) {
        RayTraceResult ray = player.rayTraceBlocks(8);
        if (ray != null && origin.equals(ray.getHitBlock()) && ray.getHitBlockFace() != null) {
            return ray.getHitBlockFace();
        }
        float pitch = player.getLocation().getPitch();
        if (pitch > 60) return BlockFace.UP;
        if (pitch < -60) return BlockFace.DOWN;
        return player.getFacing().getOppositeFace();
    }

    // --------------------------------------------------------------- veinminer

    private List<Block> veinBlocks(Block origin) {
        int max = Math.max(1, plugin.getConfig().getInt("veinminer.max-blocks", 64));
        String key = veinKey(origin.getType());

        List<Block> result = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);

        while (!queue.isEmpty() && result.size() < max - 1) {
            Block current = queue.poll();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        Block next = current.getRelative(x, y, z);
                        if (!seen.add(next)) continue;
                        if (!veinKey(next.getType()).equals(key)) continue;
                        result.add(next);
                        queue.add(next);
                        if (result.size() >= max - 1) return result;
                    }
                }
            }
        }
        return result;
    }

    private static boolean isOre(Material m) {
        return m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
    }

    /** Treats stone and deepslate variants of an ore as the same vein. */
    private static String veinKey(Material m) {
        return m.name().replace("DEEPSLATE_", "");
    }

    // ----------------------------------------------------------------- helpers

    private static void breakEffect(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        World world = block.getWorld();
        var data = block.getBlockData();
        world.spawnParticle(Particle.BLOCK, center, 20, 0.25, 0.25, 0.25, data);
        world.playSound(center, data.getSoundGroup().getBreakSound(), 1f, 0.8f);
    }

    /** Vanilla ore XP (extra blocks don't get XP computed by the server). */
    private static int oreExperience(Material m) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String n = m.name();
        if (n.endsWith("COAL_ORE")) return r.nextInt(0, 3);
        if (n.endsWith("DIAMOND_ORE") || n.endsWith("EMERALD_ORE")) return r.nextInt(3, 8);
        if (n.endsWith("LAPIS_ORE")) return r.nextInt(2, 6);
        if (n.endsWith("REDSTONE_ORE")) return r.nextInt(1, 6);
        if (n.equals("NETHER_QUARTZ_ORE")) return r.nextInt(2, 6);
        if (n.equals("NETHER_GOLD_ORE")) return r.nextInt(0, 2);
        return 0;
    }
}
