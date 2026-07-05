package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Per-bot auto-eat engine. When a bot's hunger drops to its configured threshold and it is carrying
 * an allowed food, the bot pauses its current action, holds the food, "eats" it over a short window,
 * applies the nutrition/saturation (plus notable effects like golden apples), and then switches back
 * to whatever it was holding before.
 *
 * <p>Food priority mirrors a real player reaching for the quickest snack: <b>off-hand → hotbar →
 * main inventory</b>. Off-hand food always wins so players can pin a preferred food there.
 *
 * <p>The action pause is achieved with the shared action-lock ({@link FakePlayerManager#lockForAction})
 * which freezes the bot in place; repeating task controllers (find/PVE) re-issue their navigation once
 * the lock releases, so the interrupted action resumes naturally.
 */
public final class AutoEatController {

    /**
     * Eating window measured in controller ticks. The controller is driven from the manager's
     * staggered auto-eat hook (~every 4 server ticks), so 8 calls ≈ 32 ticks ≈ the vanilla 1.6s eat.
     */
    private static final int EAT_TICKS = 8;

    private final FakePlayerManager manager;

    private final ConcurrentHashMap<UUID, EatState> eating = new ConcurrentHashMap<>();

    public AutoEatController(FakePlayerManager manager) {
        this.manager = manager;
    }

    /** @return true while the bot is mid-eat (used by the nametag activity line). */
    public boolean isEating(UUID botUuid) {
        return eating.containsKey(botUuid);
    }

    /** Drops any eating state and releases the action lock for a bot (despawn / death / disable). */
    public void cleanupBot(UUID botUuid) {
        EatState st = eating.remove(botUuid);
        if (st == null) return;
        if (st.weLocked) manager.unlockAction(botUuid);
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp != null) {
            fp.setAutoEating(false);
            fp.setActionsPaused(false);
        }
    }

    public void shutdown() {
        for (UUID id : Set.copyOf(eating.keySet())) cleanupBot(id);
    }

    /**
     * Called for a single bot from the manager tick loop. Handles both starting a new eat and
     * advancing / finishing an in-progress one.
     */
    public void tick(FakePlayer fp, Player bot) {
        if (fp == null || bot == null || !bot.isOnline()) return;
        UUID uuid = fp.getUuid();

        EatState active = eating.get(uuid);
        if (active != null) {
            advanceEat(fp, bot, active);
            return;
        }

        if (!fp.isAutoEatEnabled()) return;
        if (bot.getGameMode() == GameMode.CREATIVE || bot.getGameMode() == GameMode.SPECTATOR) return;

        int threshold = Math.max(0, Math.min(19, fp.getAutoEatHungerThreshold()));
        if (bot.getFoodLevel() > threshold || bot.getFoodLevel() >= 20) return;

        beginEat(fp, bot);
    }

    private void beginEat(FakePlayer fp, Player bot) {
        PlayerInventory inv = bot.getInventory();
        Set<Material> allowed = fp.getAutoEatFoods();

        int heldSlot = inv.getHeldItemSlot();

        // Off-hand takes top priority.
        ItemStack off = inv.getItemInOffHand();
        if (isEatable(off, allowed)) {
            startEating(fp, bot, new EatState(heldSlot, -1, true, off.getType()));
            return;
        }

        // Then the hotbar (0-8), then the rest of the inventory (9-35).
        int slot = firstEatableSlot(inv, allowed, 0, 9);
        if (slot < 0) slot = firstEatableSlot(inv, allowed, 9, 36);
        if (slot < 0) return;

        ItemStack food = inv.getItem(slot);
        if (food == null) return;

        // Bring the food into the main hand so the eat animation and switch-back look natural.
        if (slot != heldSlot) {
            ItemStack prevHeld = inv.getItem(heldSlot);
            inv.setItem(heldSlot, food);
            inv.setItem(slot, prevHeld);
        }
        inv.setHeldItemSlot(heldSlot);
        startEating(fp, bot, new EatState(heldSlot, slot, false, food.getType()));
    }

    private void startEating(FakePlayer fp, Player bot, EatState st) {
        st.ticksRemaining = EAT_TICKS;
        Location loc = bot.getLocation();
        // Pause whatever the bot is doing (movement, mining, combat, …). Every action tick checks
        // FakePlayer#isActionsPaused and no-ops while set, so the task resumes automatically after.
        fp.setActionsPaused(true);
        // Also hard-lock the position so a bot that was mid-navigation doesn't drift while eating.
        st.weLocked = !manager.isActionLocked(fp.getUuid());
        if (st.weLocked) manager.lockForAction(fp.getUuid(), loc, true);
        fp.setAutoEating(true);
        eating.put(fp.getUuid(), st);
        swing(bot, st);
    }

    private void advanceEat(FakePlayer fp, Player bot, EatState st) {
        swing(bot, st);
        // Keep the food in hand for the whole animation in case something nudged the held slot.
        if (!st.fromOffhand) bot.getInventory().setHeldItemSlot(st.heldSlot);

        if (--st.ticksRemaining > 0) return;

        finishEat(fp, bot, st);
        eating.remove(fp.getUuid());
    }

    private void finishEat(FakePlayer fp, Player bot, EatState st) {
        try {
            PlayerInventory inv = bot.getInventory();
            Material eaten;
            if (st.fromOffhand) {
                ItemStack off = inv.getItemInOffHand();
                eaten = consumeOne(off, st.foodType) ? st.foodType : null;
                inv.setItemInOffHand(off);
            } else {
                ItemStack held = inv.getItem(st.heldSlot);
                eaten = consumeOne(held, st.foodType) ? st.foodType : null;
                inv.setItem(st.heldSlot, held == null || held.getAmount() <= 0 ? null : held);
                // Switch back to what the bot was holding before it reached for the food.
                if (st.foodSlot >= 0 && st.foodSlot != st.heldSlot) {
                    ItemStack remainingFood = inv.getItem(st.heldSlot);
                    ItemStack original = inv.getItem(st.foodSlot);
                    inv.setItem(st.heldSlot, original);
                    inv.setItem(st.foodSlot, remainingFood);
                }
            }
            inv.setHeldItemSlot(st.heldSlot);

            if (eaten != null) applyNutrition(bot, eaten);
        } finally {
            if (st.weLocked) manager.unlockAction(fp.getUuid());
            fp.setAutoEating(false);
            fp.setActionsPaused(false); // resume the paused task
        }
    }

    private void applyNutrition(Player bot, Material type) {
        BotFoods.FoodDef def = BotFoods.get(type);
        if (def == null) return;

        int newFood = Math.min(20, bot.getFoodLevel() + def.nutrition());
        bot.setFoodLevel(newFood);
        float newSat = Math.min((float) newFood, bot.getSaturation() + def.saturationGain());
        bot.setSaturation(newSat);
        applySpecialEffects(bot, type);
    }

    private void applySpecialEffects(Player bot, Material type) {
        switch (type) {
            case GOLDEN_APPLE -> {
                bot.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                bot.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0));
            }
            case ENCHANTED_GOLDEN_APPLE -> {
                bot.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 400, 1));
                bot.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 3));
                bot.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 6000, 0));
                bot.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 6000, 0));
            }
            case HONEY_BOTTLE -> bot.removePotionEffect(PotionEffectType.POISON);
            default -> {}
        }
    }

    private void swing(Player bot, EatState st) {
        try {
            if (st.fromOffhand) bot.swingOffHand();
            else bot.swingMainHand();
        } catch (Exception ignored) {
        }
    }

    private boolean isEatable(ItemStack item, Set<Material> allowed) {
        if (item == null || item.getAmount() <= 0) return false;
        if (!BotFoods.isFood(item.getType())) return false;
        return allowed == null || allowed.isEmpty() || allowed.contains(item.getType());
    }

    private int firstEatableSlot(PlayerInventory inv, Set<Material> allowed, int from, int to) {
        for (int slot = from; slot < to; slot++) {
            if (isEatable(inv.getItem(slot), allowed)) return slot;
        }
        return -1;
    }

    /** Decrements one from the stack if it is still the expected food; returns whether it consumed. */
    private boolean consumeOne(ItemStack item, Material expected) {
        if (item == null || item.getAmount() <= 0 || item.getType() != expected) return false;
        item.setAmount(item.getAmount() - 1);
        return true;
    }

    /** Mutable per-bot eating progress. */
    private static final class EatState {
        final int heldSlot;
        final int foodSlot; // -1 when eating from the off-hand
        final boolean fromOffhand;
        final Material foodType;
        boolean weLocked;
        int ticksRemaining;

        EatState(int heldSlot, int foodSlot, boolean fromOffhand, Material foodType) {
            this.heldSlot = heldSlot;
            this.foodSlot = foodSlot;
            this.fromOffhand = fromOffhand;
            this.foodType = foodType;
        }
    }
}
