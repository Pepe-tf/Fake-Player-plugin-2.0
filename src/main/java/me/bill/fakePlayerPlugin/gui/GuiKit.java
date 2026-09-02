package me.bill.fakePlayerPlugin.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import me.bill.fakePlayerPlugin.util.FppScheduler;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Shared building blocks for the plugin's inventory GUIs (colors, glass fillers, text wrapping, click
 * sounds, page-nav arrows, a generic {@link InventoryHolder} marker, and a centralized chat-capture
 * mechanism for search prompts) - extracted from near-identical copies that used to live in every GUI
 * class separately.
 */
public final class GuiKit {

    private GuiKit() {}

    // FPP 2.0 "Bot Console" palette - violet / lime, see language/en.yml header for the full reference.
    public static final TextColor ACCENT = TextColor.fromHexString("#A78BFA");
    public static final TextColor ON_GREEN = TextColor.fromHexString("#BAFF4F");
    public static final TextColor OFF_RED = TextColor.fromHexString("#FF6A5C");
    public static final TextColor VALUE_YELLOW = TextColor.fromHexString("#BAFF4F");
    public static final TextColor YELLOW = TextColor.fromHexString("#BAFF4F");
    public static final TextColor GRAY = TextColor.fromHexString("#9691AB");
    public static final TextColor DARK_GRAY = TextColor.fromHexString("#5F5B73");
    public static final TextColor WHITE = TextColor.fromHexString("#EEECF7");
    public static final TextColor DANGER_RED = TextColor.fromHexString("#FF6A5C");
    public static final TextColor COMING_SOON_COLOR = TextColor.fromHexString("#5F5B73");
    public static final TextColor SELECTED_GREEN = TextColor.fromHexString("#BAFF4F");
    public static final TextColor FROZEN = TextColor.fromHexString("#CABAFF");

    public static final int CAT_WINDOW = 5;

    /** A generic marker record any GUI can reuse instead of writing its own near-identical holder. */
    public record SimpleHolder(UUID uuid, String kind) implements InventoryHolder {
        public SimpleHolder(UUID uuid) {
            this(uuid, "main");
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public org.bukkit.inventory.Inventory getInventory() {
            return null;
        }
    }

    public static ItemStack glassFiller(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        meta.lore(List.of());
        item.setItemMeta(meta);
        return item;
    }

    public static void playUiClick(Player player, float pitch) {
        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, pitch);
        } catch (Throwable ignored) {
        }
    }

    /** Word-wraps {@code text} to lines no longer than {@code maxLen} characters. */
    public static List<String> wrapText(String text, int maxLen) {
        if (text == null || text.isEmpty()) return List.of();
        if (text.length() <= maxLen) return List.of(text);
        List<String> lines = new java.util.ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty() && sb.length() + 1 + word.length() > maxLen) {
                lines.add(sb.toString().trim());
                sb.setLength(0);
            }
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(word);
        }
        if (!sb.isEmpty()) lines.add(sb.toString().trim());
        return lines;
    }

    /** A prev/next page arrow, target page shown in lore (not stack count). */
    public static ItemStack buildPageNavArrow(boolean isNext, int targetPage, TextColor color) {
        Material mat = isNext ? Material.LIME_STAINED_GLASS_PANE : Material.MAGENTA_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(isNext ? "▶" : "◄").color(color).decoration(TextDecoration.BOLD, true)));
        meta.lore(List.of(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text((isNext ? "ɴᴇxᴛ ᴘᴀɢᴇ" : "ᴘʀᴇᴠ ᴘᴀɢᴇ") + " (" + targetPage + ")")
                        .color(DARK_GRAY))));
        item.setItemMeta(meta);
        return item;
    }

    /** A search icon; {@code active} indicates a filter is currently applied (adds a glow + clear hint). */
    public static ItemStack buildSearchItem(String currentFilter) {
        boolean active = currentFilter != null && !currentFilter.isBlank();
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("🔍 ꜱᴇᴀʀᴄʜ")
                        .color(active ? ON_GREEN : ACCENT)
                        .decoration(TextDecoration.BOLD, true)));
        List<Component> lore = new java.util.ArrayList<>();
        if (active) {
            lore.add(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("ꜰɪʟᴛᴇʀ: ").color(DARK_GRAY))
                    .append(Component.text(currentFilter).color(VALUE_YELLOW)));
            lore.add(Component.empty());
            lore.add(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("◈ ").color(ACCENT))
                    .append(Component.text("ᴄʟɪᴄᴋ ᴛᴏ ᴄʜᴀɴɢᴇ").color(DARK_GRAY)));
            lore.add(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("✕ ").color(OFF_RED))
                    .append(Component.text("ꜱʜɪꜰᴛ+ᴄʟɪᴄᴋ ᴛᴏ ᴄʟᴇᴀʀ").color(DARK_GRAY)));
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        } else {
            lore.add(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("ᴄʟɪᴄᴋ ᴛᴏ ꜱᴇᴀʀᴄʜ ʙʏ ɴᴀᴍᴇ").color(DARK_GRAY)));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Chat capture (search prompts) ──────────────────────────────────────────

    private record PendingCapture(Consumer<String> onSubmit, Runnable onCancel, int timeoutTaskId) {}

    private static final Map<UUID, PendingCapture> CAPTURES = new HashMap<>();
    private static ChatCaptureListener listenerInstance;
    private static Plugin ownerPlugin;

    /** Must be called once at plugin startup before any GUI uses {@link #beginCapture}. */
    public static void registerChatCapture(Plugin plugin) {
        ownerPlugin = plugin;
        if (listenerInstance != null) return;
        listenerInstance = new ChatCaptureListener();
        Bukkit.getPluginManager().registerEvents(listenerInstance, plugin);
    }

    /**
     * Captures the next chat message from {@code player} instead of letting it reach normal chat.
     * Calls {@code onSubmit} with the typed text, or {@code onCancel} if the player types "cancel" /
     * quits / times out.
     */
    public static void beginCapture(
            Plugin plugin, Player player, Consumer<String> onSubmit, Runnable onCancel, long timeoutTicks) {
        UUID uuid = player.getUniqueId();
        cancelCapture(uuid);
        int taskId = FppScheduler.runSyncLaterWithId(
                plugin,
                () -> {
                    PendingCapture pc = CAPTURES.remove(uuid);
                    if (pc != null && pc.onCancel() != null) pc.onCancel().run();
                },
                timeoutTicks);
        CAPTURES.put(uuid, new PendingCapture(onSubmit, onCancel, taskId));
    }

    private static void cancelCapture(UUID uuid) {
        PendingCapture prev = CAPTURES.remove(uuid);
        if (prev != null && prev.timeoutTaskId() >= 0) FppScheduler.cancelTask(prev.timeoutTaskId());
    }

    private static final class ChatCaptureListener implements Listener {

        @EventHandler
        public void onModernChat(AsyncChatEvent event) {
            handle(event.getPlayer(), PlainTextComponentSerializer.plainText().serialize(event.message()), event);
        }

        // AsyncPlayerChatEvent is deprecated in favor of AsyncChatEvent, but some server/plugin setups
        // still only fire the legacy event - kept as a fallback alongside onModernChat above.
        @SuppressWarnings("deprecation")
        @EventHandler
        public void onLegacyChat(AsyncPlayerChatEvent event) {
            if (!CAPTURES.containsKey(event.getPlayer().getUniqueId())) return;
            handle(event.getPlayer(), event.getMessage(), event);
        }

        private void handle(Player player, String raw, org.bukkit.event.Cancellable event) {
            UUID uuid = player.getUniqueId();
            PendingCapture pc = CAPTURES.get(uuid);
            if (pc == null) return;
            event.setCancelled(true);
            cancelCapture(uuid);
            String trimmed = raw.trim();
            if (ownerPlugin == null) return;
            Bukkit.getScheduler().runTask(ownerPlugin, () -> {
                if (trimmed.equalsIgnoreCase("cancel")) {
                    if (pc.onCancel() != null) pc.onCancel().run();
                } else if (pc.onSubmit() != null) {
                    pc.onSubmit().accept(trimmed);
                }
            });
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            cancelCapture(event.getPlayer().getUniqueId());
        }
    }
}
