package me.bill.fakePlayerPlugin.fakeplayer;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.minecraft.core.BlockPos;

/**
 * Auto-equips the best available tool in a bot's inventory for a block, mirroring the tool category a
 * competent player would swap to before mining - driven by Bukkit's own {@code MINEABLE_*}/{@code ITEMS_*}
 * tags (the same data vanilla itself uses to decide which tool category works on which block).
 */
public final class BotToolSelector {

    private BotToolSelector() {}

    /**
     * Equips the best available tool in the bot's inventory for the block at {@code pos}, if any tool
     * category applies and the bot actually has one. Never blocks progress: if the block wants no
     * specific tool, or the bot has none of the right category, mining proceeds with whatever's held.
     */
    public static void equipBestTool(Player bot, BlockPos pos) {
        Block block = bot.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
        equipBestTool(bot, block.getType());
    }

    /** Same as {@link #equipBestTool(Player, BlockPos)}, for a block type already in hand. */
    public static void equipBestTool(Player bot, Material blockType) {
        Tag<Material> toolCategory = toolTagFor(blockType);
        if (toolCategory == null) return;

        PlayerInventory inv = bot.getInventory();
        int bestSlot = -1;
        int bestTier = -1;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir() || !toolCategory.isTagged(item.getType())) continue;
            int tier = toolTier(item.getType());
            if (tier > bestTier) {
                bestTier = tier;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0 || bestSlot == inv.getHeldItemSlot()) return;

        if (bestSlot <= 8) {
            inv.setHeldItemSlot(bestSlot);
        } else {
            int heldSlot = inv.getHeldItemSlot();
            ItemStack heldItem = inv.getItem(heldSlot);
            ItemStack toolItem = inv.getItem(bestSlot);
            inv.setItem(heldSlot, toolItem);
            inv.setItem(bestSlot, heldItem);
        }
    }

    /** Which tool category (if any) is appropriate for mining this block, mirroring vanilla's own tags. */
    private static Tag<Material> toolTagFor(Material blockType) {
        if (Tag.MINEABLE_PICKAXE.isTagged(blockType)) return Tag.ITEMS_PICKAXES;
        if (Tag.MINEABLE_AXE.isTagged(blockType)) return Tag.ITEMS_AXES;
        if (Tag.MINEABLE_SHOVEL.isTagged(blockType)) return Tag.ITEMS_SHOVELS;
        if (Tag.MINEABLE_HOE.isTagged(blockType)) return Tag.ITEMS_HOES;
        return null;
    }

    /** Higher = better tier, for picking the strongest available tool within a category. */
    private static int toolTier(Material toolMaterial) {
        String name = toolMaterial.name();
        if (name.startsWith("NETHERITE_")) return 6;
        if (name.startsWith("DIAMOND_")) return 5;
        if (name.startsWith("IRON_")) return 4;
        if (name.startsWith("STONE_")) return 3;
        if (name.startsWith("GOLDEN_")) return 2;
        if (name.startsWith("WOODEN_")) return 1;
        return 0;
    }
}
