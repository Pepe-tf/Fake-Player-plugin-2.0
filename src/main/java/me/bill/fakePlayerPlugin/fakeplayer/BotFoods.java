package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Material;

/**
 * Single source of truth for the foods the auto-eat system understands. Powers both the eating math
 * (nutrition + saturation) and the per-bot food-selector GUI, so the two can never drift apart.
 *
 * <p>Only beneficial / neutral foods are listed - items with harmful side effects (rotten flesh,
 * spider eye, pufferfish, poisonous potato, chorus fruit, etc.) are intentionally excluded so a bot
 * left on the default "eat anything" filter never poisons or teleports itself.
 */
public final class BotFoods {

    /** A single edible item: hunger restored, saturation modifier and a small-caps display name. */
    public record FoodDef(Material material, int nutrition, float saturationModifier, String display) {

        /** Saturation granted on eating, using the vanilla formula (capped at the new food level). */
        public float saturationGain() {
            return nutrition * saturationModifier * 2f;
        }
    }

    private static final Map<Material, FoodDef> FOODS;

    static {
        Map<Material, FoodDef> m = new LinkedHashMap<>();
        add(m, Material.COOKED_BEEF, 8, 0.8f, "ꜱᴛᴇᴀᴋ");
        add(m, Material.COOKED_PORKCHOP, 8, 0.8f, "ᴄᴏᴏᴋᴇᴅ ᴘᴏʀᴋᴄʜᴏᴘ");
        add(m, Material.GOLDEN_CARROT, 6, 1.2f, "ɢᴏʟᴅᴇɴ ᴄᴀʀʀᴏᴛ");
        add(m, Material.GOLDEN_APPLE, 4, 1.2f, "ɢᴏʟᴅᴇɴ ᴀᴘᴘʟᴇ");
        add(m, Material.ENCHANTED_GOLDEN_APPLE, 4, 1.2f, "ᴇɴᴄʜᴀɴᴛᴇᴅ ᴀᴘᴘʟᴇ");
        add(m, Material.COOKED_MUTTON, 6, 0.8f, "ᴄᴏᴏᴋᴇᴅ ᴍᴜᴛᴛᴏɴ");
        add(m, Material.COOKED_SALMON, 6, 0.8f, "ᴄᴏᴏᴋᴇᴅ ꜱᴀʟᴍᴏɴ");
        add(m, Material.COOKED_CHICKEN, 6, 0.6f, "ᴄᴏᴏᴋᴇᴅ ᴄʜɪᴄᴋᴇɴ");
        add(m, Material.BREAD, 5, 0.6f, "ʙʀᴇᴀᴅ");
        add(m, Material.COOKED_COD, 5, 0.6f, "ᴄᴏᴏᴋᴇᴅ ᴄᴏᴅ");
        add(m, Material.COOKED_RABBIT, 5, 0.6f, "ᴄᴏᴏᴋᴇᴅ ʀᴀʙʙɪᴛ");
        add(m, Material.BAKED_POTATO, 5, 0.6f, "ʙᴀᴋᴇᴅ ᴘᴏᴛᴀᴛᴏ");
        add(m, Material.PUMPKIN_PIE, 8, 0.3f, "ᴘᴜᴍᴘᴋɪɴ ᴘɪᴇ");
        add(m, Material.MUSHROOM_STEW, 6, 0.6f, "ᴍᴜꜱʜʀᴏᴏᴍ ꜱᴛᴇᴡ");
        add(m, Material.BEETROOT_SOUP, 6, 0.6f, "ʙᴇᴇᴛʀᴏᴏᴛ ꜱᴏᴜᴘ");
        add(m, Material.RABBIT_STEW, 10, 0.6f, "ʀᴀʙʙɪᴛ ꜱᴛᴇᴡ");
        add(m, Material.APPLE, 4, 0.3f, "ᴀᴘᴘʟᴇ");
        add(m, Material.CARROT, 3, 0.6f, "ᴄᴀʀʀᴏᴛ");
        add(m, Material.BEEF, 3, 0.3f, "ʀᴀᴡ ʙᴇᴇꜰ");
        add(m, Material.PORKCHOP, 3, 0.3f, "ʀᴀᴡ ᴘᴏʀᴋᴄʜᴏᴘ");
        add(m, Material.MUTTON, 2, 0.3f, "ʀᴀᴡ ᴍᴜᴛᴛᴏɴ");
        add(m, Material.CHICKEN, 2, 0.3f, "ʀᴀᴡ ᴄʜɪᴄᴋᴇɴ");
        add(m, Material.RABBIT, 3, 0.3f, "ʀᴀᴡ ʀᴀʙʙɪᴛ");
        add(m, Material.COD, 2, 0.1f, "ʀᴀᴡ ᴄᴏᴅ");
        add(m, Material.SALMON, 2, 0.1f, "ʀᴀᴡ ꜱᴀʟᴍᴏɴ");
        add(m, Material.TROPICAL_FISH, 1, 0.1f, "ᴛʀᴏᴘɪᴄᴀʟ ꜰɪꜱʜ");
        add(m, Material.MELON_SLICE, 2, 0.3f, "ᴍᴇʟᴏɴ ꜱʟɪᴄᴇ");
        add(m, Material.SWEET_BERRIES, 2, 0.1f, "ꜱᴡᴇᴇᴛ ʙᴇʀʀɪᴇꜱ");
        add(m, Material.GLOW_BERRIES, 2, 0.1f, "ɢʟᴏᴡ ʙᴇʀʀɪᴇꜱ");
        add(m, Material.POTATO, 1, 0.3f, "ᴘᴏᴛᴀᴛᴏ");
        add(m, Material.BEETROOT, 1, 0.6f, "ʙᴇᴇᴛʀᴏᴏᴛ");
        add(m, Material.DRIED_KELP, 1, 0.3f, "ᴅʀɪᴇᴅ ᴋᴇʟᴘ");
        add(m, Material.COOKIE, 2, 0.1f, "ᴄᴏᴏᴋɪᴇ");
        add(m, Material.HONEY_BOTTLE, 6, 0.1f, "ʜᴏɴᴇʏ ʙᴏᴛᴛʟᴇ");

        FOODS = Collections.unmodifiableMap(m);
    }

    private BotFoods() {}

    private static void add(Map<Material, FoodDef> m, Material mat, int nutrition, float sat, String display) {
        if (mat != null) m.put(mat, new FoodDef(mat, nutrition, sat, display));
    }

    /** All auto-eat-eligible foods, in a sensible high-to-low nutrition display order. */
    public static List<FoodDef> all() {
        return List.copyOf(FOODS.values());
    }

    public static boolean isFood(Material type) {
        return type != null && FOODS.containsKey(type);
    }

    public static FoodDef get(Material type) {
        return type == null ? null : FOODS.get(type);
    }

    /** Parses a persisted comma-separated material list into a validated allowed-food set. */
    public static Set<Material> parse(String csv) {
        if (csv == null || csv.isBlank()) return new java.util.LinkedHashSet<>();
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Material.valueOf(s.toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(BotFoods::isFood)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** Serialises an allowed-food set back to a comma-separated material list for persistence. */
    public static String serialize(Set<Material> foods) {
        if (foods == null || foods.isEmpty()) return "";
        return foods.stream().map(Material::name).collect(Collectors.joining(","));
    }
}
