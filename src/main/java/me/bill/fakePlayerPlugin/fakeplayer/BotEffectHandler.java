package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.List;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class BotEffectHandler {

    private static final Set<PotionEffectType> HARMFUL = Set.of(
            PotionEffectType.SLOWNESS,
            PotionEffectType.MINING_FATIGUE,
            PotionEffectType.INSTANT_DAMAGE,
            PotionEffectType.NAUSEA,
            PotionEffectType.BLINDNESS,
            PotionEffectType.HUNGER,
            PotionEffectType.WEAKNESS,
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            PotionEffectType.LEVITATION,
            PotionEffectType.DARKNESS);

    private static final Set<PotionEffectType> PREVENT =
            Set.of(PotionEffectType.BAD_OMEN, PotionEffectType.RAID_OMEN, PotionEffectType.TRIAL_OMEN);

    private BotEffectHandler() {}

    public static PotionEffectType tickEffects(Player bot, boolean autoMilk, boolean preventBadOmen) {
        if (bot.isDead()) return null;

        if (preventBadOmen) {
            for (PotionEffectType preventType : PREVENT) {
                if (bot.hasPotionEffect(preventType)) {
                    bot.removePotionEffect(preventType);
                    return preventType;
                }
            }
        }

        if (autoMilk) {
            PotionEffectType harmfulFound = null;
            for (PotionEffect effect : bot.getActivePotionEffects()) {
                if (HARMFUL.contains(effect.getType()) && harmfulFound == null) {
                    harmfulFound = effect.getType();
                }
            }
            if (harmfulFound != null) {
                for (PotionEffect e : List.copyOf(bot.getActivePotionEffects())) {
                    if (HARMFUL.contains(e.getType())) {
                        bot.removePotionEffect(e.getType());
                    }
                }
                return harmfulFound;
            }
        }

        return null;
    }
}
