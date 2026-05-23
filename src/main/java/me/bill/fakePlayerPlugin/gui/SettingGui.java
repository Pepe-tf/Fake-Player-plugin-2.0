package me.bill.fakePlayerPlugin.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppSettingsItem;
import me.bill.fakePlayerPlugin.api.FppSettingsTab;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.AttributionManager;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SettingGui implements Listener {

  private static final TextColor ACCENT = TextColor.fromHexString("#0079FF");
  private static final TextColor ON_GREEN = TextColor.fromHexString("#66CC66");
  private static final TextColor OFF_RED = NamedTextColor.RED;
  private static final TextColor VALUE_YELLOW = TextColor.fromHexString("#FFDD57");
  private static final TextColor YELLOW = NamedTextColor.YELLOW;
  private static final TextColor GRAY = NamedTextColor.GRAY;
  private static final TextColor DARK_GRAY = NamedTextColor.DARK_GRAY;
  private static final TextColor WHITE = NamedTextColor.WHITE;
  private static final TextColor COMING_SOON_COLOR = TextColor.fromHexString("#FFA500");

  private static final int SIZE = 54;
  private static final int SETTINGS_PER_PAGE = 45;
  private static final int SLOT_RESET = 45;
  private static final int SLOT_CAT_PREV = 46;
  private static final int SLOT_CAT_NEXT = 52;
  private static final int SLOT_CLOSE = 53;

  private static final int CAT_WINDOW = 5;

  private static final int CAT_WINDOW_START = 47;

  private static final UUID SKIN_OWNER_UUID =
      UUID.fromString("a318f9f4-e2bf-479c-a47a-6a2c1b0b9e66");
  private static final String SKIN_OWNER_NAME = "F_PP";

  private static final long SKULL_TTL_MS = 30L * 60 * 1_000;

  private volatile ItemStack cachedOwnerSkull = null;
  private volatile long skullRefreshedAt = 0L;

  private final FakePlayerPlugin plugin;

  private final Map<UUID, int[]> sessions = new HashMap<>();

  private final Map<UUID, ChatInputSession> chatSessions = new HashMap<>();

  private final Set<UUID> pendingChatInput = new HashSet<>();

  private final Set<UUID> pendingRebuild = new HashSet<>();

  private final Category[] categories;

  private final CopyOnWriteArrayList<FppSettingsTab> extensionTabs = new CopyOnWriteArrayList<>();

  public SettingGui(FakePlayerPlugin plugin) {
    this.plugin = plugin;
    this.categories = new Category[]{general(), body()};

    if (!AttributionManager.quickAuthorCheck()) {
      FppLogger.warn(
          "Plugin attribution integrity check failed in SettingGui.");
    }
  }

  public void open(Player player) {
    sessions.put(player.getUniqueId(), new int[]{0, 0, 0});
    build(player);
  }

  public void registerExtensionTab(FppSettingsTab tab) {
    extensionTabs.addIfAbsent(tab);
  }

  public void unregisterExtensionTab(FppSettingsTab tab) {
    extensionTabs.remove(tab);
  }

  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) return;
    event.setCancelled(true);

    if (!(event.getWhoClicked() instanceof Player player)) return;
    if (event.getClickedInventory() == null) return;
    if (!event.getClickedInventory().equals(event.getInventory())) return;
    if (!Perm.has(player, Perm.SETTINGS)) return;

    int[] state = sessions.get(holder.uuid);
    if (state == null) return;

    List<SettingsTabRef> tabs = visibleTabs(player);
    if (tabs.isEmpty()) return;

    int slot = event.getSlot();
    int catIdx = state[0];
    int pageIdx = state[1];
    int catOffset = state[2];
    if (catIdx >= tabs.size()) catIdx = tabs.size() - 1;
    SettingsTabRef currentTab = tabs.get(catIdx);

    if (slot == SLOT_RESET) {
      playUiClick(player, 0.6f);
      resetAllCategories(player);
      return;
    }

    if (slot == SLOT_CAT_PREV) {
      if (catOffset > 0) {
        playUiClick(player, 1.0f);
        state[2]--;
      }
      build(player);
      return;
    }

    if (slot == SLOT_CAT_NEXT) {
      if (catOffset + CAT_WINDOW < tabs.size()) {
        playUiClick(player, 1.0f);
        state[2]++;
      }
      build(player);
      return;
    }

    if (slot == SLOT_CLOSE) {
      playUiClick(player, 0.8f);
      player.closeInventory();
      return;
    }

    if (slot >= CAT_WINDOW_START && slot < CAT_WINDOW_START + CAT_WINDOW) {
      int ci = catOffset + (slot - CAT_WINDOW_START);
      if (ci < tabs.size()) {
        if (ci != catIdx) playUiClick(player, 1.3f);
        state[0] = ci;
        state[1] = 0;
        build(player);
      }
      return;
    }

    int settingIdx = slotToSettingIdx(slot);
    if (settingIdx >= 0) {
      List<SettingEntry> settings = currentTab.entries(player);
      int entryIdx = pageIdx * SETTINGS_PER_PAGE + settingIdx;
      if (entryIdx >= settings.size()) return;

      SettingEntry entry = settings.get(entryIdx);

      if (entry.type == SettingType.COMING_SOON) {
        player.playSound(
            player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 0.8f, 1.0f);
        player.sendActionBar(
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("⊘ ").color(COMING_SOON_COLOR))
                .append(
                    Component.text(entry.label + "  ")
                        .color(WHITE)
                        .decoration(TextDecoration.BOLD, false))
                .append(
                    Component.text("- ᴄᴏᴍɪɴɢ ꜱᴏᴏɴ")
                        .color(COMING_SOON_COLOR)
                        .decoration(TextDecoration.BOLD, true)));
        return;
      }

      if (entry.type == SettingType.ACTION) {
        if (entry.clickAction != null) entry.clickAction.run();
        else handleAction(player, entry.configKey);
        build(player);
      } else if (entry.type == SettingType.TOGGLE) {
        entry.apply(plugin);
        plugin.saveConfig();
        Config.reload();
        applyLiveEffect(entry.configKey);
        String newVal = entry.currentValueString(plugin);
        playUiClick(player, newVal.startsWith("✔") ? 1.2f : 0.85f);
        sendActionBarConfirm(player, entry.label, newVal);
        build(player);
      } else {
        playUiClick(player, 1.0f);
        openChatInput(player, entry, state.clone());
      }
    }
  }

  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();
    if (!(event.getInventory().getHolder() instanceof GuiHolder)) return;

    if (pendingChatInput.contains(uuid)) return;

    if (pendingRebuild.contains(uuid)) return;
    sessions.remove(uuid);

    plugin.saveConfig();
    Config.reload();

    if (event.getReason() != InventoryCloseEvent.Reason.DISCONNECT
        && event.getPlayer() instanceof Player player) {
      player.sendMessage(
          Component.empty()
              .decoration(TextDecoration.ITALIC, false)
              .append(Component.text("✔ ").color(ON_GREEN))
              .append(Component.text("ꜱᴇᴛᴛɪɴɢꜱ ꜱᴀᴠᴇᴅ.").color(WHITE)));
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onPlayerChat(AsyncChatEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();
    ChatInputSession ses = chatSessions.remove(uuid);
    if (ses == null) return;

    event.setCancelled(true);
    handleChatInput(uuid, ses, PlainTextComponentSerializer.plainText().serialize(event.message()).trim());
  }

  private void handleChatInput(UUID uuid, ChatInputSession ses, String raw) {
    FppScheduler.cancelTask(ses.cleanupTaskId);

    sessions.put(uuid, ses.guiState);
    FppScheduler.runSync(
        plugin,
        () -> {
          Player p = Bukkit.getPlayer(uuid);
          if (p == null) return;

          if (raw.equalsIgnoreCase("cancel")) {
            p.sendMessage(
                Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("✦ ").color(ACCENT))
                    .append(
                        Component.text("ᴄᴀɴᴄᴇʟʟᴇᴅ - ʀᴇᴛᴜʀɴɪɴɢ ᴛᴏ" + " ꜱᴇᴛᴛɪɴɢꜱ.").color(GRAY)));
            build(p);
            return;
          }

          boolean ok = tryApply(p, ses.entry, raw);
          if (ok) {
            plugin.saveConfig();
            Config.reload();
            applyLiveEffect(ses.entry.configKey);
            sendActionBarConfirm(p, ses.entry.label, ses.entry.currentValueString(plugin));
          }
          build(p);
        });
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();
    sessions.remove(uuid);
    pendingChatInput.remove(uuid);
    ChatInputSession ses = chatSessions.remove(uuid);
    if (ses != null) {
      FppScheduler.cancelTask(ses.cleanupTaskId);
    }
  }

  private void openChatInput(Player player, SettingEntry entry, int[] guiState) {
    UUID uuid = player.getUniqueId();

    pendingChatInput.add(uuid);
    player.closeInventory();
    pendingChatInput.remove(uuid);

    String currentVal = entry.currentValueString(plugin).replace("✔ ", "").replace("✘ ", "");

    player.sendMessage(Component.empty());
    player.sendMessage(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("┌─ ").color(DARK_GRAY))
            .append(Component.text("[").color(DARK_GRAY))
            .append(Component.text("ꜰᴘᴘ").color(ACCENT))
            .append(Component.text("]  ").color(DARK_GRAY))
            .append(Component.text("ꜱᴇᴛᴛɪɴɢꜱ").color(WHITE).decoration(TextDecoration.BOLD, true))
            .append(Component.text("  ·  ᴇᴅɪᴛ ᴠᴀʟᴜᴇ").color(DARK_GRAY)));
    player.sendMessage(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("│  ").color(DARK_GRAY))
            .append(
                Component.text(entry.label)
                    .color(VALUE_YELLOW)
                    .decoration(TextDecoration.BOLD, true)));
    for (String line : entry.description.split("\\\\n|\n")) {
      if (!line.isBlank()) {
        player.sendMessage(
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("│  ").color(DARK_GRAY))
                .append(Component.text(line).color(GRAY)));
      }
    }
    player.sendMessage(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("│  ").color(DARK_GRAY)));
    player.sendMessage(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("│  ").color(DARK_GRAY))
            .append(Component.text("ᴄᴜʀʀᴇɴᴛ  ").color(DARK_GRAY))
            .append(
                Component.text(currentVal)
                    .color(VALUE_YELLOW)
                    .decoration(TextDecoration.BOLD, true)));
    player.sendMessage(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("└─ ").color(DARK_GRAY))
            .append(Component.text("ᴛʏᴘᴇ ᴀ ɴᴇᴡ ᴠᴀʟᴜᴇ, ᴏʀ ").color(GRAY))
            .append(Component.text("ᴄᴀɴᴄᴇʟ").color(OFF_RED).decoration(TextDecoration.BOLD, true))
            .append(Component.text(" ᴛᴏ ɢᴏ ʙᴀᴄᴋ.").color(GRAY)));
    player.sendMessage(Component.empty());

    int taskId =
        FppScheduler.runSyncLaterWithId(
            plugin,
            () -> {
              ChatInputSession stale = chatSessions.remove(uuid);
              if (stale != null) {
                sessions.put(uuid, stale.guiState);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                  p.sendMessage(
                      Component.empty()
                          .decoration(TextDecoration.ITALIC, false)
                          .append(Component.text("✦ ").color(ACCENT))
                          .append(
                              Component.text(
                                      "ɪɴᴘᴜᴛ ᴛɪᴍᴇᴅ" + " ᴏᴜᴛ -" + " ʀᴇᴛᴜʀɴɪɴɢ" + " ᴛᴏ ꜱᴇᴛᴛɪɴɢꜱ.")
                                  .color(GRAY)));
                  build(p);
                }
              }
            },
            20L * 60);

    chatSessions.put(uuid, new ChatInputSession(entry, guiState, taskId));
  }

  private boolean tryApply(Player player, SettingEntry entry, String raw) {
    var cfg = plugin.getConfig();
    try {
      switch (entry.type) {
        case CYCLE_INT -> {
          int val = Integer.parseInt(raw);
          if (val < 0) {
            player.sendMessage(
                Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("✘ ").color(OFF_RED))
                    .append(Component.text("ᴠᴀʟᴜᴇ ᴍᴜꜱᴛ ʙᴇ ").color(GRAY))
                    .append(Component.text("0 ᴏʀ ɢʀᴇᴀᴛᴇʀ").color(VALUE_YELLOW))
                    .append(Component.text(".").color(GRAY)));
            return false;
          }
          cfg.set(entry.configKey, val);
        }
        case CYCLE_DOUBLE -> {
          double val = Double.parseDouble(raw);
          if (val < 0) {
            player.sendMessage(
                Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("✘ ").color(OFF_RED))
                    .append(Component.text("ᴠᴀʟᴜᴇ ᴍᴜꜱᴛ ʙᴇ ").color(GRAY))
                    .append(Component.text("0 ᴏʀ ɢʀᴇᴀᴛᴇʀ").color(VALUE_YELLOW))
                    .append(Component.text(".").color(GRAY)));
            return false;
          }
          cfg.set(entry.configKey, val);
        }
        default -> {
          return false;
        }
      }
    } catch (NumberFormatException e) {
      player.sendMessage(
          Component.empty()
              .decoration(TextDecoration.ITALIC, false)
              .append(Component.text("✘ ").color(OFF_RED))
              .append(Component.text("\"").color(GRAY))
              .append(Component.text(raw).color(VALUE_YELLOW))
              .append(Component.text("\" ɪꜱ ɴᴏᴛ ᴀ ᴠᴀʟɪᴅ ɴᴜᴍʙᴇʀ.").color(GRAY)));
      return false;
    }
    return true;
  }

  private void build(Player player) {
    UUID uuid = player.getUniqueId();
    int[] state = sessions.get(uuid);
    if (state == null) return;

    int catIdx = state[0];
    int pageIdx = state[1];
    int catOffset = state[2];
    List<SettingsTabRef> tabs = visibleTabs(player);
    if (tabs.isEmpty()) return;
    if (catIdx >= tabs.size()) catIdx = tabs.size() - 1;
    state[0] = catIdx;
    SettingsTabRef tab = tabs.get(catIdx);

    GuiHolder holder = new GuiHolder(uuid);
    Component title =
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("[").color(DARK_GRAY))
            .append(Component.text("ꜰᴘᴘ").color(ACCENT))
            .append(Component.text("] ").color(DARK_GRAY))
            .append(Component.text(tab.label()).color(DARK_GRAY));

    Inventory inv = Bukkit.createInventory(holder, SIZE, title);

    List<SettingEntry> settings = tab.entries(player);
    int settingsCount = settings.size();
    int totalPages = Math.max(1, (int) Math.ceil(settingsCount / (double) SETTINGS_PER_PAGE));
    pageIdx = Math.min(pageIdx, Math.max(0, totalPages - 1));
    state[1] = pageIdx;

    int startIdx = pageIdx * SETTINGS_PER_PAGE;
    int endIdx = Math.min(startIdx + SETTINGS_PER_PAGE, settingsCount);
    for (int i = startIdx; i < endIdx; i++) {
      inv.setItem(i - startIdx, buildSettingItem(settings.get(i)));
    }

    inv.setItem(SLOT_RESET, buildResetAllButton());

    inv.setItem(
        SLOT_CAT_PREV,
        catOffset > 0 ? buildCatArrow(false) : glassFiller(Material.GRAY_STAINED_GLASS_PANE));

    for (int i = 0; i < CAT_WINDOW; i++) {
      int ci = catOffset + i;
      inv.setItem(
          CAT_WINDOW_START + i,
          ci < tabs.size()
              ? buildCategoryTab(tabs.get(ci), ci == catIdx)
              : glassFiller(Material.GRAY_STAINED_GLASS_PANE));
    }

    inv.setItem(
        SLOT_CAT_NEXT,
        catOffset + CAT_WINDOW < tabs.size()
            ? buildCatArrow(true)
            : glassFiller(Material.GRAY_STAINED_GLASS_PANE));

    inv.setItem(SLOT_CLOSE, buildCloseButton());

    pendingRebuild.add(uuid);
    player.openInventory(inv);
    pendingRebuild.remove(uuid);
    sessions.put(uuid, state);
  }

  private static int slotToSettingIdx(int slot) {
    return slot < 45 ? slot : -1;
  }

  private static int settingIdxToSlot(int localIdx) {
    return localIdx;
  }

  private ItemStack buildSettingItem(SettingEntry entry) {

    if (entry.type == SettingType.COMING_SOON) {

      ItemStack item =
          "skin.guaranteed-skin".equals(entry.configKey)
              ? getOwnerSkull()
              : new ItemStack(entry.icon);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(
          Component.empty()
              .decoration(TextDecoration.ITALIC, false)
              .append(Component.text("⊘ ").color(COMING_SOON_COLOR))
              .append(
                  Component.text(entry.label)
                      .color(COMING_SOON_COLOR)
                      .decoration(TextDecoration.BOLD, true)));
      List<Component> lore = new ArrayList<>();
      lore.add(Component.empty());
      lore.add(
          Component.empty()
              .decoration(TextDecoration.ITALIC, false)
              .append(Component.text("ᴠᴀʟᴜᴇ  ").color(DARK_GRAY))
              .append(
                  Component.text("⚠ ᴄᴏᴍɪɴɢ ꜱᴏᴏɴ")
                      .color(COMING_SOON_COLOR)
                      .decoration(TextDecoration.BOLD, true)));
      lore.add(Component.empty());
      for (String line : entry.description.split("\\\\n|\n")) {
        if (!line.isBlank()) {
          lore.add(
              Component.empty()
                  .decoration(TextDecoration.ITALIC, false)
                  .append(Component.text(line).color(GRAY)));
        }
      }
      lore.add(Component.empty());
      lore.add(
          Component.empty()
              .decoration(TextDecoration.ITALIC, false)
              .append(Component.text("⊘ ").color(COMING_SOON_COLOR))
              .append(Component.text("ꜰᴇᴀᴛᴜʀᴇ ᴜɴᴀᴠᴀɪʟᴀʙʟᴇ").color(DARK_GRAY)));
      meta.lore(lore);
      item.setItemMeta(meta);
      return item;
    }

    boolean isToggle = entry.type == SettingType.TOGGLE;
    boolean isOn = isToggle && plugin.getConfig().getBoolean(entry.configKey, false);

    TextColor nameColor = isToggle ? (isOn ? ON_GREEN : OFF_RED) : ACCENT;

    ItemStack item =
        "skin.guaranteed-skin".equals(entry.configKey)
            ? getOwnerSkull()
            : new ItemStack(entry.icon);
    ItemMeta meta = item.getItemMeta();

    if (isToggle && isOn) {
      meta.addEnchant(Enchantment.UNBREAKING, 1, true);
      meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }

    meta.displayName(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(
                Component.text(entry.label)
                    .color(nameColor)
                    .decoration(TextDecoration.BOLD, true)));

    List<Component> lore = new ArrayList<>();
    lore.add(Component.empty());

    String valStr = entry.currentValueString(plugin);
    TextColor valColor = isToggle ? (isOn ? ON_GREEN : OFF_RED) : VALUE_YELLOW;
    lore.add(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("ᴠᴀʟᴜᴇ  ").color(DARK_GRAY))
            .append(Component.text(valStr).color(valColor).decoration(TextDecoration.BOLD, true)));
    lore.add(Component.empty());

    for (String line : entry.description.split("\\\\n|\n")) {
      if (!line.isBlank()) {
        lore.add(
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(line).color(GRAY)));
      }
    }
    lore.add(Component.empty());

    if (isToggle) {
      lore.add(
          Component.empty()
              .decoration(TextDecoration.ITALIC, false)
              .append(Component.text("◈ ").color(ACCENT))
              .append(Component.text("ᴄʟɪᴄᴋ ᴛᴏ ᴛᴏɢɢʟᴇ").color(DARK_GRAY)));
    } else if (entry.type == SettingType.ACTION) {
      lore.add(
          Component.empty()
              .decoration(TextDecoration.ITALIC, false)
              .append(Component.text("⚠ ").color(OFF_RED))
              .append(Component.text("ᴄʟɪᴄᴋ ᴛᴏ ʀᴜɴ ᴛʜɪꜱ ᴀᴄᴛɪᴏɴ").color(DARK_GRAY)));
    } else {
      lore.add(
          Component.empty()
              .decoration(TextDecoration.ITALIC, false)
              .append(Component.text("✎ ").color(ACCENT))
              .append(Component.text("ᴄʟɪᴄᴋ ᴛᴏ ꜱᴇᴛ ᴀ ᴠᴀʟᴜᴇ ɪɴ ᴄʜᴀᴛ").color(DARK_GRAY)));
    }

    meta.lore(lore);
    item.setItemMeta(meta);
    return item;
  }

  private List<SettingsTabRef> visibleTabs(Player viewer) {
    List<SettingsTabRef> tabs = new ArrayList<>(categories.length + extensionTabs.size());
    for (Category category : categories) {
      tabs.add(new SettingsTabRef(category));
    }
    for (FppSettingsTab tab : extensionTabs) {
      if (tab.isVisible(viewer)) tabs.add(new SettingsTabRef(tab));
    }
    return tabs;
  }

  private ItemStack buildCategoryTab(SettingsTabRef tab, boolean active) {
    Material mat = active ? tab.activeMat() : tab.inactiveMat();
    ItemStack item = new ItemStack(mat);
    ItemMeta meta = item.getItemMeta();
    if (active) {
      meta.addEnchant(Enchantment.UNBREAKING, 1, true);
      meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }
    meta.displayName(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(
                Component.text(tab.label()).color(ACCENT).decoration(TextDecoration.BOLD, active)));
    meta.lore(
        List.of(
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(
                    Component.text(active ? "◈  ᴄᴜʀʀᴇɴᴛʟʏ ᴠɪᴇᴡɪɴɢ" : "ᴄʟɪᴄᴋ ᴛᴏ ꜱᴡɪᴛᴄʜ")
                        .color(active ? ON_GREEN : DARK_GRAY))));
    item.setItemMeta(meta);
    return item;
  }

  private ItemStack buildCatArrow(boolean isNext) {
    Material mat = isNext ? Material.LIME_STAINED_GLASS_PANE : Material.MAGENTA_STAINED_GLASS_PANE;
    String label = isNext ? "▶" : "◄";
    TextColor col = isNext ? ON_GREEN : COMING_SOON_COLOR;
    ItemStack item = new ItemStack(mat);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(label).color(col).decoration(TextDecoration.BOLD, true)));
    meta.lore(
        List.of(
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(
                    Component.text("ꜱᴄʀᴏʟʟ ᴄᴀᴛᴇɢᴏʀɪᴇꜱ " + (isNext ? "ꜰᴏʀᴡᴀʀᴅ" : "ʙᴀᴄᴋᴡᴀʀᴅ") + ".")
                        .color(DARK_GRAY))));
    item.setItemMeta(meta);
    return item;
  }

  private ItemStack buildExtensionSettingItem(FppSettingsItem item) {
    ItemStack stack = new ItemStack(item.getIcon());
    ItemMeta meta = stack.getItemMeta();
    meta.displayName(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(item.getLabel()).color(ACCENT).decoration(TextDecoration.BOLD, true)));
    List<Component> lore = new ArrayList<>();
    lore.add(Component.empty());
    String value = item.getValue();
    if (value != null && !value.isBlank()) {
      lore.add(
          Component.empty()
              .decoration(TextDecoration.ITALIC, false)
              .append(Component.text("ᴠᴀʟᴜᴇ  ").color(DARK_GRAY))
              .append(Component.text(value).color(VALUE_YELLOW).decoration(TextDecoration.BOLD, true)));
      lore.add(Component.empty());
    }
    for (String line : item.getDescription().split("\\\\n|\n")) {
      if (!line.isBlank()) {
        lore.add(
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(line).color(GRAY)));
      }
    }
    lore.add(Component.empty());
    lore.add(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("⚠ ").color(OFF_RED))
            .append(Component.text("ᴄʟɪᴄᴋ ᴛᴏ ᴇxᴇᴄᴜᴛᴇ").color(DARK_GRAY)));
    meta.lore(lore);
    stack.setItemMeta(meta);
    return stack;
  }

  private record SettingsTabRef(Category builtin, FppSettingsTab extension) {
    SettingsTabRef(Category builtin) {
      this(builtin, null);
    }

    SettingsTabRef(FppSettingsTab extension) {
      this(null, extension);
    }

    boolean isExtension() {
      return extension != null;
    }

    String label() {
      return isExtension() ? extension.getLabel() : builtin.label;
    }

    Material activeMat() {
      return isExtension() ? extension.getActiveMaterial() : builtin.activeMat;
    }

    Material inactiveMat() {
      return isExtension() ? extension.getInactiveMaterial() : builtin.inactiveMat;
    }

    Material separatorGlass() {
      return isExtension() ? extension.getSeparatorGlass() : builtin.separatorGlass;
    }

    List<SettingEntry> entries(Player viewer) {
      if (!isExtension()) return builtin.settings;
      List<SettingEntry> out = new ArrayList<>();
      for (FppSettingsItem item : extension.getItems(viewer)) {
        out.add(
            SettingEntry.action(
                item.getId(),
                item.getLabel(),
                item.getDescription(),
                item.getIcon(),
                item.getValue(),
                () -> item.onClick(viewer)));
      }
      return out;
    }
  }

  private ItemStack buildResetAllButton() {
    ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(
                Component.text("⟲  ʀᴇꜱᴇᴛ ᴀʟʟ")
                    .color(YELLOW)
                    .decoration(TextDecoration.BOLD, false)));
    meta.lore(
        List.of(
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("ʀᴇꜱᴇᴛ ᴇᴠᴇʀʏ ꜱᴇᴛᴛɪɴɢ ᴀᴄʀᴏꜱꜱ").color(GRAY)),
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("ᴀʟʟ ᴄᴀᴛᴇɢᴏʀɪᴇꜱ ᴛᴏ ᴅᴇꜰᴀᴜʟᴛꜱ.").color(GRAY))));
    item.setItemMeta(meta);
    return item;
  }

  private ItemStack buildCloseButton() {
    ItemStack item = new ItemStack(Material.BARRIER);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(
                Component.text("✕  ᴄʟᴏꜱᴇ").color(OFF_RED).decoration(TextDecoration.BOLD, true)));
    meta.lore(
        List.of(
            Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("ꜱᴀᴠᴇ & ᴄʟᴏꜱᴇ ᴛʜᴇ ꜱᴇᴛᴛɪɴɢꜱ ᴍᴇɴᴜ.").color(DARK_GRAY))));
    item.setItemMeta(meta);
    return item;
  }

  private ItemStack getOwnerSkull() {
    long now = System.currentTimeMillis();
    ItemStack cached = cachedOwnerSkull;
    if (cached != null && (now - skullRefreshedAt) < SKULL_TTL_MS) {
      return cached.clone();
    }

    ItemStack skull = buildSkullSync();
    cachedOwnerSkull = skull;
    skullRefreshedAt = now;

    scheduleSkullRefresh();
    return skull.clone();
  }

  private ItemStack buildSkullSync() {
    ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
    SkullMeta meta = (SkullMeta) skull.getItemMeta();
    if (meta != null) {
      PlayerProfile profile = Bukkit.createProfile(SKIN_OWNER_UUID, SKIN_OWNER_NAME);
      meta.setPlayerProfile(profile);
      skull.setItemMeta(meta);
    }
    return skull;
  }

  private void scheduleSkullRefresh() {
    FppScheduler.runAsync(
        plugin,
        () -> {
          try {
            PlayerProfile profile = Bukkit.createProfile(SKIN_OWNER_UUID, SKIN_OWNER_NAME);
            profile.complete(true);
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
              meta.setPlayerProfile(profile);
              skull.setItemMeta(meta);
            }
            cachedOwnerSkull = skull;
            skullRefreshedAt = System.currentTimeMillis();
          } catch (Exception ignored) {

          }
        });
  }

  private void resetAllCategories(Player player) {
    var cfg = plugin.getConfig();
    var defaults = cfg.getDefaults();
    for (Category cat : categories) {
      for (SettingEntry entry : cat.settings) {
        switch (entry.type) {
          case TOGGLE -> cfg.set(
              entry.configKey,
              defaults != null ? defaults.getBoolean(entry.configKey, false) : false);
          case CYCLE_INT -> cfg.set(
              entry.configKey,
              defaults != null
                  ? defaults.getInt(entry.configKey, entry.intValues[0])
                  : entry.intValues[0]);
          case CYCLE_DOUBLE -> cfg.set(
              entry.configKey,
              defaults != null
                  ? defaults.getDouble(entry.configKey, entry.dblValues[0])
                  : entry.dblValues[0]);
          default -> {
          }
        }
      }
    }
    plugin.saveConfig();
    Config.reload();
    for (Category cat : categories) {
      for (SettingEntry entry : cat.settings) {
        applyLiveEffect(entry.configKey);
      }
    }
    build(player);
    player.sendActionBar(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("⟲ ").color(YELLOW))
            .append(
                Component.text("ᴀʟʟ ꜱᴇᴛᴛɪɴɢꜱ  ")
                    .color(WHITE)
                    .decoration(TextDecoration.BOLD, false))
            .append(
                Component.text("ʀᴇꜱᴇᴛ ᴛᴏ ᴅᴇꜰᴀᴜʟᴛꜱ")
                    .color(YELLOW)
                    .decoration(TextDecoration.BOLD, true)));
  }

  private ItemStack glassFiller(Material mat) {
    ItemStack item = new ItemStack(mat);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Component.empty());
    meta.lore(List.of());
    item.setItemMeta(meta);
    return item;
  }

  private void applyLiveEffect(String configKey) {
    FakePlayerManager fpm = plugin.getFakePlayerManager();

    if (configKey.equals("body.pushable")
        || configKey.equals("body.damageable")
        || configKey.equals("combat.max-health")) {
      if (fpm != null) fpm.applyBodyConfig();
      return;
    }

    if (configKey.equals("body.pick-up-items")) {
      boolean enabled = plugin.getConfig().getBoolean("body.pick-up-items", false);
      if (fpm != null) {

        fpm.getActivePlayers()
            .forEach(
                fp -> {
                  Player body = fp.getPlayer();
                  if (body != null) body.setCanPickupItems(enabled && fp.isPickUpItemsEnabled());
                });
        if (!enabled) {
          fpm.getActivePlayers().forEach(this::dropBotInventoryWithAnimation);
        }
      }
      return;
    }

    if (configKey.equals("body.pick-up-xp")) {
      boolean enabled = plugin.getConfig().getBoolean("body.pick-up-xp", true);
      if (!enabled && fpm != null) {
        fpm.getActivePlayers()
            .forEach(
                fp -> {
                  Player bot = fp.getPlayer();
                  if (bot == null || !bot.isOnline()) return;
                  int xp = bot.getTotalExperience();
                  if (xp <= 0) return;
                  World world = bot.getWorld();
                  Location loc = bot.getLocation();
                  world.spawn(loc, ExperienceOrb.class, orb -> orb.setExperience(xp));
                  bot.setTotalExperience(0);
                  bot.setLevel(0);
                  bot.setExp(0f);
                });
      }
      return;
    }

    if (configKey.equals("skin.guaranteed-skin")) {
      boolean enabled = plugin.getConfig().getBoolean("skin.guaranteed-skin", false);
      if (fpm != null && plugin.getSkinManager() != null) {
        fpm.getActivePlayers()
            .forEach(
                fp -> {
                  Player bot = fp.getPlayer();
                  if (bot == null || !bot.isOnline()) return;

                  if (enabled) {

                    plugin
                        .getSkinManager()
                        .resolveEffectiveSkin(
                            fp,
                            skin -> {
                              if (skin == null || !skin.isValid()) {
                                Config.debugSkin(
                                    "SettingGui: no valid skin"
                                        + " resolved for bot '"
                                        + fp.getName()
                                        + "'");
                                return;
                              }
                              FppScheduler.runSyncLater(
                                  plugin,
                                  () -> {
                                    Player b = fp.getPlayer();
                                    if (b == null || !b.isOnline()) return;
                                    plugin.getSkinManager().applySkinFromProfile(fp, skin);
                                    Config.debugSkin(
                                        "SettingGui:"
                                            + " re-applied"
                                            + " custom"
                                            + " skin"
                                            + " for bot"
                                            + " '"
                                            + fp.getName()
                                            + "'");
                                  },
                                  3L);
                            });
                  } else {

                    boolean reset = plugin.getSkinManager().resetToDefaultSkin(fp);
                    Config.debugSkin(
                        "SettingGui: reset bot '"
                            + fp.getName()
                            + "' to default skin (success="
                            + reset
                            + ")");
                  }
                });
      }
    }
  }

  private void dropBotInventoryWithAnimation(FakePlayer fp) {
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;

    boolean hasItems = false;
    for (ItemStack item : bot.getInventory().getContents()) {
      if (item != null && item.getType() != Material.AIR) {
        hasItems = true;
        break;
      }
    }
    if (!hasItems) return;

    Location loc = bot.getLocation();
    float origYaw = loc.getYaw();
    float origPitch = loc.getPitch();

    bot.setRotation(origYaw, 90f);
    NmsPlayerSpawner.setHeadYaw(bot, origYaw);

    FppScheduler.runSyncLater(
        plugin,
        () -> {
          Player b = fp.getPlayer();
          if (b == null || !b.isOnline()) return;

          ItemStack[] contents = b.getInventory().getContents().clone();
          b.getInventory().clear();
          for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
              b.getWorld().dropItemNaturally(b.getLocation(), item);
            }
          }

          FppScheduler.runSyncLater(
              plugin,
              () -> {
                Player b2 = fp.getPlayer();
                if (b2 == null || !b2.isOnline()) return;
                b2.setRotation(origYaw, origPitch);
                NmsPlayerSpawner.setHeadYaw(b2, origYaw);
              },
              5L);
        },
        3L);
  }

  private void sendActionBarConfirm(Player player, String label, String newVal) {
    player.sendActionBar(
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("✔ ").color(ON_GREEN))
            .append(
                Component.text(label + "  ").color(WHITE).decoration(TextDecoration.BOLD, false))
            .append(Component.text("→  ").color(DARK_GRAY))
            .append(
                Component.text(newVal).color(VALUE_YELLOW).decoration(TextDecoration.BOLD, true)));
  }

  private static void playUiClick(Player player, float pitch) {
    player.playSound(
        player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, pitch);
  }

  private Category general() {
    return new Category(
        "⚙ ɢᴇɴᴇʀᴀʟ",
        Material.COMPARATOR,
        Material.GRAY_DYE,
        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
        List.of(
            SettingEntry.toggle(
                "persistence.enabled",
                "ᴘᴇʀꜱɪꜱᴛ ᴏɴ ʀᴇꜱᴛᴀʀᴛ",
                "ʙᴏᴛꜱ ʀᴇꜱᴛᴏʀᴇ ᴛᴏ ᴛʜᴇɪʀ ʟᴀꜱᴛ ᴘᴏꜱɪᴛɪᴏɴ\nᴀꜰᴛᴇʀ ᴀ ꜱᴇʀᴠᴇʀ ʀᴇꜱᴛᴀʀᴛ.",
                Material.ENDER_CHEST),
            SettingEntry.toggle(
                "chunk-loading.enabled",
                "ᴄʜᴜɴᴋ ʟᴏᴀᴅɪɴɢ",
                "ʙᴏᴛꜱ ᴋᴇᴇᴘ ꜱᴜʀʀᴏᴜɴᴅɪɴɢ ᴄʜᴜɴᴋꜱ\nʟᴏᴀᴅᴇᴅ ʟɪᴋᴇ ʀᴇᴀʟ ᴘʟᴀʏᴇʀꜱ.",
                Material.GRASS_BLOCK),
            SettingEntry.cycleInt(
                "spawn-cooldown",
                "ꜱᴘᴀᴡɴ ᴄᴏᴏʟᴅᴏᴡɴ (ꜱ)",
                "ꜱᴇᴄᴏɴᴅꜱ ʙᴇᴛᴡᴇᴇɴ /ꜰᴘᴘ ꜱᴘᴀᴡɴ ᴜꜱᴇꜱ\nᴘᴇʀ ᴘʟᴀʏᴇʀ. 0 = ᴅɪꜱᴀʙʟᴇᴅ.",
                Material.CLOCK,
                new int[]{0, 10, 30, 60, 120, 300}),
            SettingEntry.cycleInt(
                "limits.max-bots",
                "ɢʟᴏʙᴀʟ ʙᴏᴛ ᴄᴀᴘ",
                "ᴍᴀxɪᴍᴜᴍ ʙᴏᴛꜱ ꜱᴇʀᴠᴇʀ-ᴡɪᴅᴇ.\n0 = ɴᴏ ʟɪᴍɪᴛ.",
                Material.CHEST,
                new int[]{10, 25, 50, 100, 250, 500, 1000}),
            SettingEntry.cycleInt(
                "limits.user-bot-limit",
                "ᴘᴇʀ-ᴜꜱᴇʀ ʙᴏᴛ ʟɪᴍɪᴛ",
                "ᴅᴇꜰᴀᴜʟᴛ ᴘᴇʀꜱᴏɴᴀʟ ʟɪᴍɪᴛ ꜰᴏʀ\nꜰᴘᴘ.ᴜꜱᴇʀ.ꜱᴘᴀᴡɴ ᴘʟᴀʏᴇʀꜱ.",
                Material.SHIELD,
                new int[]{1, 2, 3, 5, 10}),
            SettingEntry.cycleInt(
                "chunk-loading.radius",
                "ᴄʜᴜɴᴋ ʟᴏᴀᴅ ʀᴀᴅɪᴜꜱ",
                "ʟᴏɴɡᴇꜱᴛ ʀᴀɴᴅᴏᴍ ᴅᴇʟᴀʏ ʙᴇꜰᴏʀᴇ\nᴀ ʙᴏᴛ ʟᴇᴀᴠᴇꜱ. 20 = 1 ꜱᴇᴄᴏɴᴅ.",
                Material.COMPASS,
                new int[]{0, 2, 4, 6, 8, 12, 16}),
            SettingEntry.cycleInt(
                "chunk-loading.radius-duplicate",
                "ᴄʜᴜɴᴋ ʟᴏᴀᴅ ʀᴀᴅɪᴜꜱ",
                "ᴄʜᴜɴᴋ ʀᴀᴅɪᴜꜱ ᴋᴇᴘᴛ ʟᴏᴀᴅᴇᴅ ᴀʀᴏᴜɴᴅ\nᴇᴀᴄʜ ʙᴏᴛ. 0 = ꜱᴇʀᴠᴇʀ ᴅᴇꜰᴀᴜʟᴛ.",
                Material.COMPASS,
                new int[]{0, 2, 4, 6, 8, 12, 16}),
            SettingEntry.action(
                "reset-all-bots",
                "ʀᴇꜱᴇᴛ ᴀʟʟ ʙᴏᴛꜱ",
                "ᴅᴇꜱᴘᴀᴡɴ ᴀʟʟ ᴀᴄᴛɪᴠᴇ ʙᴏᴛꜱ ᴀɴᴅ\n"
                    + "ᴄʟᴇᴀʀ ᴛʜᴇɪʀ ʀᴜɴɴɪɴɢ ᴛᴀꜱᴋꜱ.",
                Material.TNT)));
  }

  private void handleAction(Player player, String key) {
    if ("reset-all-bots".equals(key)) {
      int count = plugin.getFakePlayerManager().getActivePlayers().size();
      plugin.getFakePlayerManager().removeAll();
      player.sendMessage(
          Component.text("Reset " + count + " active bot(s).", NamedTextColor.YELLOW)
              .decoration(TextDecoration.ITALIC, false));
    }
  }

  private Category body() {
    return new Category(
        "🤖 ʙᴏᴅʏ",
        Material.ARMOR_STAND,
        Material.ARMOR_STAND,
        Material.LIME_STAINED_GLASS_PANE,
        List.of(
            SettingEntry.toggle(
                "body.pushable",
                "ᴘᴜꜱʜᴀʙʟᴇ",
                "ᴀʟʟᴏᴡ ᴘʟᴀʏᴇʀꜱ ᴀɴᴅ ᴇɴᴛɪᴛɪᴇꜱ\nᴛᴏ ᴘᴜꜱʜ ʙᴏᴛ ʙᴏᴅɪᴇꜱ.",
                Material.PISTON),
            SettingEntry.toggle(
                "body.damageable",
                "ᴅᴀᴍᴀɢᴇᴀʙʟᴇ",
                "ʙᴏᴛꜱ ᴛᴀᴋᴇ ᴘʟᴀʏᴇʀ/ᴇɴᴛɪᴛʏ ᴅᴀᴍᴀɢᴇ.\nꜰᴀʟꜱᴇ = ɪᴍᴍᴜɴᴇ ᴛᴏ ᴘᴠᴘ/ᴍᴏʙꜱ ᴏɴʟʏ.",
                Material.IRON_SWORD),
            SettingEntry.toggle(
                "body.pick-up-items",
                "ᴘɪᴄᴋ ᴜᴘ ɪᴛᴇᴍꜱ",
                "ʙᴏᴛꜱ ᴘɪᴄᴋ ᴜᴘ ɪᴛᴇᴍꜱ ꜰʀᴏᴍ ᴛʜᴇ ɢʀᴏᴜɴᴅ\nʟɪᴋᴇ ᴀ ʀᴇᴀʟ ᴘʟᴀʏᴇʀ.",
                Material.HOPPER),
            SettingEntry.toggle(
                "body.pick-up-xp",
                "ᴘɪᴄᴋ ᴜᴘ xᴘ",
                "ʙᴏᴛꜱ ᴄᴏʟʟᴇᴄᴛ ᴇxᴘᴇʀɪᴇɴᴄᴇ ᴏʀʙꜱ\nꜰʀᴏᴍ ᴛʜᴇ ɢʀᴏᴜɴᴅ.",
                Material.EXPERIENCE_BOTTLE),
            SettingEntry.toggle(
                "head-ai.enabled",
                "ʜᴇᴀᴅ ᴀɪ",
                "ʙᴏᴛꜱ ꜱᴍᴏᴏᴛʜʟʏ ʀᴏᴛᴀᴛᴇ ᴛᴏ ꜰᴀᴄᴇ\nᴛʜᴇ ɴᴇᴀʀᴇꜱᴛ ᴘʟᴀʏᴇʀ ɪɴ ʀᴀɴɢᴇ.",
                Material.ENDER_EYE),
            SettingEntry.toggle(
                "swim-ai.enabled",
                "ꜱᴡɪᴍ ᴀɪ",
                "ʙᴏᴛꜱ ᴜꜱᴇ ʙᴀꜱɪᴄ ꜰʟᴏᴀᴛ/ᴊᴜᴍᴘ ꜱᴡɪᴍ ᴀɪ\nᴡʜᴇɴ ꜱᴜʙᴍᴇʀɢᴇᴅ ɪɴ ᴡᴀᴛᴇʀ ᴏʀ ʟᴀᴠᴀ.",
                Material.WATER_BUCKET),
            SettingEntry.toggle(
                "death.respawn-on-death",
                "ʀᴇꜱᴘᴀᴡɴ ᴏɴ ᴅᴇᴀᴛʜ",
                "ʙᴏᴛꜱ ᴀᴜᴛᴏᴍᴀᴛɪᴄᴀʟʟʏ ᴄᴏᴍᴇ ʙᴀᴄᴋ\nᴀꜰᴛᴇʀ ʙᴇɪɴɢ ᴋɪʟʟᴇᴅ.",
                Material.TOTEM_OF_UNDYING),
            SettingEntry.toggle(
                "death.suppress-drops",
                "ꜱᴜᴘᴘʀᴇꜱꜱ ᴅʀᴏᴘꜱ",
                "ʙᴏᴛꜱ ᴅʀᴏᴘ ɴᴏ ɪᴛᴇᴍꜱ ᴏʀ xᴘ\nᴡʜᴇɴ ᴛʜᴇʏ ᴅɪᴇ.",
                Material.CHEST),
            SettingEntry.toggle(
                "body.drop-items-on-despawn",
                "ᴅʀᴏᴘ ᴏɴ ᴅᴇꜱᴘᴀᴡɴ",
                "ᴅʀᴏᴘ ɪɴᴠᴇɴᴛᴏʀʏ + xᴘ ᴡʜᴇɴ ᴀ ʙᴏᴛ\nɪꜱ ᴅᴇꜱᴘᴀᴡɴᴇᴅ. ᴏꜰꜰ = ʀᴇᴍᴇᴍʙᴇʀꜱ ɪᴛᴇᴍꜱ\nᴏɴ ɴᴇxᴛ ꜱᴘᴀᴡɴ ᴡɪᴛʜ ᴛʜᴇ ꜱᴀᴍᴇ ɴᴀᴍᴇ.",
                Material.ENDER_CHEST),
            SettingEntry.cycleDouble(
                "combat.max-health",
                "ᴍᴀx ʜᴇᴀʟᴛʜ (½-ʜᴇᴀʀᴛꜱ)",
                "ʙᴏᴛ ʙᴀꜱᴇ ʜᴇᴀʟᴛʜ. 20 = 10 ʜᴇᴀʀᴛꜱ.\n" + "ᴀᴘᴘʟɪᴇᴅ ᴀᴛ ꜱᴘᴀᴡɴ ᴀɴᴅ ᴏɴ /ꜰᴘᴘ ʀᴇʟᴏᴀᴅ.",
                Material.GOLDEN_APPLE,
                new double[]{5, 10, 15, 20, 40}),
            SettingEntry.cycleInt(
                "death.respawn-delay",
                "ʀᴇꜱᴘᴀᴡɴ ᴅᴇʟᴀʏ (ᴛɪᴄᴋꜱ)",
                "ᴛɪᴄᴋꜱ ʙᴇꜰᴏʀᴇ ᴀ ᴅᴇᴀᴅ ʙᴏᴛ ʀᴇᴛᴜʀɴꜱ.\n1 = ɪɴꜱᴛᴀɴᴛ  ·  20 = 1 ꜱᴇᴄᴏɴᴅ.",
                Material.CLOCK,
                new int[]{1, 5, 10, 15, 20, 40, 60, 100})));
  }

  private static final class GuiHolder implements InventoryHolder {
    final UUID uuid;

    GuiHolder(UUID uuid) {
      this.uuid = uuid;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public Inventory getInventory() {
      return null;
    }
  }

  private record Category(
      String label,
      Material activeMat,
      Material inactiveMat,
      Material separatorGlass,
      List<SettingEntry> settings) {
  }

  private enum SettingType {
    TOGGLE,
    CYCLE_INT,
    CYCLE_DOUBLE,
    ACTION,
    COMING_SOON
  }

  private record ChatInputSession(SettingEntry entry, int[] guiState, int cleanupTaskId) {
  }

  private static final class SettingEntry {
    final String configKey;
    final String label;
    final String description;
    final Material icon;
    final SettingType type;
    final int[] intValues;
    final double[] dblValues;
    final String valueOverride;
    final Runnable clickAction;

    private SettingEntry(
        String configKey,
        String label,
        String description,
        Material icon,
        SettingType type,
        int[] intValues,
        double[] dblValues,
        String valueOverride,
        Runnable clickAction) {
      this.configKey = configKey;
      this.label = label;
      this.description = description;
      this.icon = icon;
      this.type = type;
      this.intValues = intValues;
      this.dblValues = dblValues;
      this.valueOverride = valueOverride;
      this.clickAction = clickAction;
    }

    static SettingEntry toggle(String key, String label, String desc, Material icon) {
      return new SettingEntry(key, label, desc, icon, SettingType.TOGGLE, null, null, null, null);
    }

    static SettingEntry cycleInt(
        String key, String label, String desc, Material icon, int[] values) {
      return new SettingEntry(key, label, desc, icon, SettingType.CYCLE_INT, values, null, null, null);
    }

    static SettingEntry cycleDouble(
        String key, String label, String desc, Material icon, double[] values) {
      return new SettingEntry(key, label, desc, icon, SettingType.CYCLE_DOUBLE, null, values, null, null);
    }

    static SettingEntry comingSoon(String key, String label, String desc, Material icon) {
      return new SettingEntry(key, label, desc, icon, SettingType.COMING_SOON, null, null, null, null);
    }

    static SettingEntry action(String key, String label, String desc, Material icon) {
      return new SettingEntry(key, label, desc, icon, SettingType.ACTION, null, null, null, null);
    }

    static SettingEntry action(
        String key,
        String label,
        String desc,
        Material icon,
        String valueOverride,
        Runnable clickAction) {
      return new SettingEntry(
          key, label, desc, icon, SettingType.ACTION, null, null, valueOverride, clickAction);
    }

    String currentValueString(FakePlayerPlugin plugin) {
      if (valueOverride != null) return valueOverride;
      var cfg = plugin.getConfig();
      return switch (type) {
        case TOGGLE -> cfg.getBoolean(configKey, false) ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
        case CYCLE_INT -> String.valueOf(cfg.getInt(configKey, intValues[0]));
        case ACTION -> "ᴄʟɪᴄᴋ ᴛᴏ ʀᴜɴ";
        case CYCLE_DOUBLE -> {
          double d = cfg.getDouble(configKey, dblValues[0]);
          yield (d == Math.floor(d) && !Double.isInfinite(d))
              ? String.valueOf((int) d)
              : String.format("%.2f", d);
        }
        case COMING_SOON -> "⚠ ᴄᴏᴍɪɴɢ ꜱᴏᴏɴ";
      };
    }

    void apply(FakePlayerPlugin plugin) {
      if (type == SettingType.TOGGLE) {
        plugin.getConfig().set(configKey, !plugin.getConfig().getBoolean(configKey, false));
      }
    }
  }
}
