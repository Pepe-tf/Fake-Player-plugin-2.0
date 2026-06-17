# Extensions

FPP supports a lightweight **Extension API** for third-party developers. Drop `.jar` files into `plugins/FakePlayerPlugin/extensions/` to load them on startup or `/fpp reload`.

## Table of Contents

1. [Getting Started](#getting-started)
2. [FppExtension Lifecycle](#fppextension-lifecycle)
3. [FppApi Reference](#fppapi-reference)
4. [FppBot Reference](#fppbot-reference)
5. [Extension Hooks](#extension-hooks)
6. [Events](#events)
7. [Extension Config & Resources](#extension-config--resources)
8. [Extension Bundles](#extension-bundles)
9. [Service Registry](#service-registry)
10. [Working Examples](#working-examples)
11. [Building an Extension](#building-an-extension)
12. [Troubleshooting](#troubleshooting)

---

## Getting Started

### Requirements

- JDK 21
- Your extension JAR must contain a class implementing `me.bill.fakePlayerPlugin.api.FppExtension`
- That class must have a **public no-arg constructor**
- Compile against the FPP plugin JAR (or include it as a `provided` dependency)

### Installation

1. Build your extension as a JAR.
2. Place it in `plugins/FakePlayerPlugin/extensions/`.
3. Run `/fpp reload` or restart the server.

### Extension Folder Structure

```
plugins/FakePlayerPlugin/
├── extensions/
│   ├── MyExtension.jar
│   └── MyExtension/              # auto-created data folder
│       ├── config.yml            # auto-extracted or created
│       └── extension-resources/  # auto-extracted bundled files
```

---

## FppExtension Lifecycle

The entry point for every extension. Implement this interface.

```java
public interface FppExtension {
    @NotNull String getName();           // Unique name (e.g., "MyExtension")
    @NotNull String getVersion();        // SemVer string (e.g., "1.0.0")
    
    default @NotNull String getDescription() { return ""; }
    default @NotNull List<String> getAuthors() { return List.of(); }
    default @NotNull List<String> getDependencies() { return List.of(); }
    default @NotNull List<String> getSoftDependencies() { return List.of(); }
    
    /** Lower = earlier load order (default: 100) */
    default int getPriority() { return 100; }
    
    /** Called after FPP onEnable completes */
    void onEnable(@NotNull FppApi api);
    
    /** Called when plugin disables or extension reloads */
    void onDisable();
    
    // ── Config helpers (default methods — no need to implement) ──
    default @Nullable File getDataFolder() { ... }
    default void saveDefaultConfig() { ... }
    default void saveDefaultResources() { ... }
    default @Nullable File saveResource(@NotNull String jarPath) { ... }
    default @NotNull YamlConfiguration getConfig() { ... }
    default void reloadConfig() { ... }

    // ── Service registry helpers (see Service Registry section) ──
    default <T> void registerService(@NotNull Class<T> type, @NotNull T instance) { ... }
    default <T> void unregisterService(@NotNull Class<T> type, @NotNull T instance) { ... }
    default <T> @Nullable T getService(@NotNull Class<T> type) { ... }
    default boolean hasService(@NotNull Class<?> type) { ... }

    // ── Cross-extension config helpers ──
    default @Nullable YamlConfiguration getExtensionConfig(@NotNull String extensionName) { ... }
    default void saveDefaultExtensionConfig(@NotNull String extensionName) { ... }
}
```

### Important Notes

- `onEnable` receives the `FppApi` instance — store it in a field for later use.
- The extension class is instantiated via reflection with a no-arg constructor. Do **not** require constructor parameters.
- `onDisable` must clean up any registered handlers, commands, or tasks to avoid memory leaks on reload.

---

## FppApi Reference

The main API entry point obtained in `onEnable(FppApi api)`.

### Bot Queries

| Method | Returns | Description |
|--------|---------|-------------|
| `getBots()` | `Collection<FppBot>` | All active bots |
| `getBotsControllableBy(Player)` | `Collection<FppBot>` | Bots a player can administer |
| `getBotsOwnedBy(Player)` | `Collection<FppBot>` | Bots owned by a player |
| `getBot(String name)` | `Optional<FppBot>` | Bot by name |
| `getBot(UUID uuid)` | `Optional<FppBot>` | Bot by UUID |
| `isBot(Player)` | `boolean` | True if this player is a bot entity |
| `asBot(Player)` | `Optional<FppBot>` | Cast player to bot wrapper |
| `canControlBot(Player, FppBot)` | `boolean` | Admin permission check |
| `getBotCount()` | `int` | Number of active bots |
| `isBotOnline(UUID)` | `boolean` | Whether bot is still active |

### Spawning / Despawning

| Method | Description |
|--------|-------------|
| `spawnBot(Location, Player spawner, String name)` | Spawn a bot at location |
| `spawnBot(Location, Player spawner, String name, UUID uuid)` | Spawn with specific UUID |
| `despawnBot(String name)` | Remove bot by name |
| `despawnBot(FppBot bot)` | Remove bot wrapper |
| `despawnBotForLoginHandoff(String)` | Remove without death effects |

### Commands

| Method | Description |
|--------|-------------|
| `registerCommand(FppAddonCommand)` | Add a new `/fpp` subcommand |
| `unregisterCommand(FppAddonCommand)` | Remove addon command |
| `registerCommandExtension(FppCommandExtension)` | Hook into existing command |
| `unregisterCommandExtension(FppCommandExtension)` | Remove command extension |
| `getRegisteredCommands()` | List all commands (core + addons + extensions) |
| `getRegisteredCommands(CommandSender)` | Filtered by permission |

### Tick Handlers

| Method | Description |
|--------|-------------|
| `registerTickHandler(FppBotTickHandler)` | Run code every tick per bot |
| `unregisterTickHandler(FppBotTickHandler)` | Stop ticking |

### Settings GUI

| Method | Description |
|--------|-------------|
| `registerSettingsTab(FppSettingsTab)` | Add tab to global settings GUI |
| `unregisterSettingsTab(FppSettingsTab)` | Remove global tab |
| `registerBotSettingsTab(FppSettingsTab)` | Add tab to per-bot `BotSettingGui` |
| `unregisterBotSettingsTab(FppSettingsTab)` | Remove per-bot tab |

### Navigation

| Method | Description |
|--------|-------------|
| `navigateTo(FppBot, Location, Runnable onArrive)` | Simple path to location |
| `navigateTo(FppBot, Location, onArrive, onFail, onCancel)` | With failure/cancel callbacks |
| `navigateTo(FppBot, Location, onArrive, onFail, onCancel, double arrivalDistance)` | Custom arrival threshold |
| `cancelNavigation(FppBot)` | Stop current path |
| `isNavigating(FppBot)` | True if bot has an active path |
| `setNavigationGoal(FppBot, FppNavigationGoal)` | Custom waypoint provider |
| `clearNavigationGoal(FppBot)` | Remove custom goal |

### Bot Data / Metadata

| Method | Description |
|--------|-------------|
| `sayAsBot(FppBot, String message)` | Force bot to send chat message |
| `setBotPing(FppBot, int pingMs)` | Override tab-list ping |
| `resetBotPing(FppBot)` | Restore default ping |
| `persistBotSettings(FppBot)` | Save bot state to database |
| `setBotExtensionData(FppBot, String extKey, String dataKey, String value)` | Store key-value data |
| `removeBotExtensionData(FppBot, String extKey, String dataKey)` | Remove stored data |
| `getBotExtensionData(FppBot, String extKey)` | Get all stored data for an extension |
| `runAsBot(FppBot, String command)` | Execute command as bot entity |

### Utility

| Method | Description |
|--------|-------------|
| `getVersion()` | Plugin version string |
| `getPlugin()` | Get the FPP `Plugin` instance |
| `getOnlinePlayer(String name)` | Lookup online player |
| `getOnlineCount()` | Total online players (real + bots) |

---

## FppBot Reference

`FppBot` is a safe wrapper around internal `FakePlayer` objects. All methods are thread-safe for reads.

### Identity

| Method | Returns | Description |
|--------|---------|-------------|
| `getName()` | `String` | Internal bot name |
| `getUuid()` | `UUID` | Bot UUID |
| `getDisplayName()` | `String` | Formatted display name |
| `setDisplayName(String)` | — | Update display name |
| `getSkinName()` | `String` | Active skin name (nullable) |
| `getSpawnedBy()` | `String` | Spawner name |
| `getSpawnedByUuid()` | `UUID` | Spawner UUID |
| `isOwnedBy(UUID)` | `boolean` | Check ownership |
| `hasControllerAccess(UUID)` | `boolean` | Check shared controller access |
| `getSharedControllerUuids()` | `Set<UUID>` | All shared controllers |
| `grantControllerAccess(UUID)` | `boolean` | Add controller |
| `revokeControllerAccess(UUID)` | `boolean` | Remove controller |

### State

| Method | Description |
|--------|-------------|
| `isBodyless()` | No physical entity (tab-only mode) |
| `isFrozen()` | Bot is frozen |
| `setFrozen(boolean)` | Freeze/unfreeze |
| `isAlive()` | Health > 0 |
| `isRespawning()` | Currently in respawn delay |
| `isSleeping()` | Bot is in bed |
| `isSneaking()` | Sneak state |
| `setSneaking(boolean)` | Set sneak |
| `isSprinting()` | Sprint state |
| `setSprinting(boolean)` | Set sprint |
| `isOnGround()` | Ground contact |
| `isClimbing()` | On ladder/vines |
| `isPassenger()` | Riding something |
| `hasVehicle()` | Has a passenger |
| `isInWater()` | Submerged in water |
| `isInLava()` | In lava |
| `isOnline()` | Entity still active |
| `getUptime()` | `Duration` since spawn |
| `getDeathCount()` | Times died |
| `getTotalDamageTaken()` | Cumulative damage |
| `getPing()` | Current ping ms |
| `hasCustomPing()` | Ping was manually set |
| `getReachDistance()` | Attack reach |
| `performRespawn()` | Force respawn now |

### Location / Movement

| Method | Description |
|--------|-------------|
| `getLocation()` | Current `Location` |
| `getWorldName()` | World name |
| `getEyeLocation()` | Eye-level location |
| `getVelocity()` | Current velocity vector |
| `setVelocity(Vector)` | Apply velocity |
| `teleport(Location)` | Teleport bot |
| `lookAt(Location)` | Rotate head toward point |
| `getEntity()` | `Player` entity (nullable) |

### Health / GameMode

| Method | Description |
|--------|-------------|
| `getHealth()` / `setHealth(double)` | Current HP |
| `getMaxHealth()` / `setMaxHealth(double)` | Max HP cap |
| `isDead()` | Health <= 0 |
| `getGameMode()` / `setGameMode(GameMode)` | Bot gamemode |

### Inventory

| Method | Description |
|--------|-------------|
| `getInventory()` | `PlayerInventory` (nullable) |
| `getItemInMainHand()` | Main hand item |
| `setItemInMainHand(ItemStack)` | Set main hand |
| `getItemInOffHand()` | Off hand item |
| `setItemInOffHand(ItemStack)` | Set off hand |

### Experience

| Method | Description |
|--------|-------------|
| `getLevel()` / `setLevel(int)` | XP level |
| `getExp()` / `setExp(float)` | Progress bar 0-1 |
| `getTotalExperience()` / `setTotalExperience(int)` | Total XP points |

### Chat / AI

| Method | Description |
|--------|-------------|
| `isChatEnabled()` / `setChatEnabled(boolean)` | Chat participation |
| `getChatTier()` / `setChatTier(String)` | Chat tier/category |
| `getAiPersonality()` / `setAiPersonality(String)` | AI personality key |
| `sendMessage(String)` | Send MiniMessage-formatted message to bot |
| `hasPermission(String)` | Check bot's effective permission |

### AI / Navigation Toggles

| Method | Description |
|--------|-------------|
| `isHeadAiEnabled()` / `setHeadAiEnabled(boolean)` | Head tracking |
| `isSwimAiEnabled()` / `setSwimAiEnabled(boolean)` | Auto-swim |
| `isPickUpItemsEnabled()` / `setPickUpItemsEnabled(boolean)` | Item pickup |
| `isPickUpXpEnabled()` / `setPickUpXpEnabled(boolean)` | XP orb pickup |
| `isNavParkour()` / `setNavParkour(boolean)` | Jump gaps |
| `isNavBreakBlocks()` / `setNavBreakBlocks(boolean)` | Break obstructing blocks |
| `isNavPlaceBlocks()` / `setNavPlaceBlocks(boolean)` | Place bridging blocks |
| `isNavAvoidWater()` / `setNavAvoidWater(boolean)` | Water avoidance |
| `isNavAvoidLava()` / `setNavAvoidLava(boolean)` | Lava avoidance |
| `getChunkLoadRadius()` / `setChunkLoadRadius(int)` | Per-bot chunk radius |

### PvE

| Method | Description |
|--------|-------------|
| `isPveEnabled()` / `setPveEnabled(boolean)` | Attack mode |
| `getPveRange()` / `setPveRange(double)` | Target detection range |
| `getPvePriority()` / `setPvePriority(String)` | `nearest` or `lowest-health` |

### Sleep

| Method | Description |
|--------|-------------|
| `getSleepOrigin()` / `setSleepOrigin(Location)` | Sleep center point |
| `getSleepRadius()` / `setSleepRadius(double)` | Search radius for beds |

### LuckPerms

| Method | Description |
|--------|-------------|
| `getLuckpermsGroup()` / `setLuckpermsGroup(String)` | LuckPerms group |

### Animation

| Method | Description |
|--------|-------------|
| `swingMainHand()` | Play swing animation |
| `swingOffHand()` | Play off-hand swing |

### Metadata (Addon Storage)

| Method | Description |
|--------|-------------|
| `setMetadata(String key, Object value)` | Store arbitrary data |
| `getMetadata(String key)` | Retrieve value |
| `hasMetadata(String key)` | Check existence |
| `removeMetadata(String key)` | Delete key |
| `getMetadataMap()` | Get all metadata |

---

## Extension Hooks

### Addon Commands (`FppAddonCommand`)

Register entirely new `/fpp` subcommands visible in the help menu.

```java
public interface FppAddonCommand {
    @NotNull String getName();
    @NotNull String getDescription();
    @NotNull String getUsage();
    @NotNull String getPermission();
    
    default @NotNull Material getIcon() { return Material.COMMAND_BLOCK; }
    default @NotNull List<String> getAliases() { return List.of(); }
    default boolean canUse(@NotNull CommandSender sender) { ... }
    
    boolean execute(@NotNull CommandSender sender, @NotNull String[] args);
    default @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) { return List.of(); }
}
```

Register: `api.registerCommand(myCommand);`

### Command Extensions (`FppCommandExtension`)

Hook into existing core commands to add flags or modify behavior.

```java
public interface FppCommandExtension {
    @NotNull String getCommandName(); // e.g., "spawn", "mine"
    default @NotNull List<String> getAliases() { return List.of(); }
    default @NotNull String getUsage() { return ""; }
    default @NotNull String getDescription() { return "Extends /fpp " + getCommandName() + "."; }
    default @NotNull String getPermission() { return ""; }
    default boolean canUse(@NotNull CommandSender sender) { ... }
    
    boolean execute(@NotNull CommandSender sender, @NotNull String[] args);
    default @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) { return List.of(); }
}
```

Register: `api.registerCommandExtension(myExtension);`

### Tick Handlers (`FppBotTickHandler`)

Run code every server tick for every active bot. Lightweight handlers only!

```java
@FunctionalInterface
public interface FppBotTickHandler {
    void onTick(@NotNull FppBot bot, @NotNull Player entity);
}
```

Register: `api.registerTickHandler((bot, entity) -> { ... });`

**Warning:** Tick handlers run on the main thread. Keep them fast. If you need async work, schedule it via BukkitScheduler.

### Settings Tabs (`FppSettingsTab` + `FppSettingsItem`)

Add custom tabs to `/fpp settings` and per-bot `BotSettingGui`.

```java
public interface FppSettingsTab {
    @NotNull String getId();
    @NotNull String getLabel();
    @NotNull Material getActiveMaterial();
    @NotNull Material getInactiveMaterial();
    @NotNull Material getSeparatorGlass();
    
    default boolean isVisible(@NotNull Player viewer) { return true; }
    @NotNull List<FppSettingsItem> getItems(@NotNull Player viewer);
}

public interface FppSettingsItem {
    @NotNull String getId();
    @NotNull String getLabel();
    @NotNull String getDescription();
    @NotNull Material getIcon();
    @Nullable String getValue(); // Shown on the item
    void onClick(@NotNull Player viewer);
}
```

Register global tab: `api.registerSettingsTab(tab);`
Register per-bot tab: `api.registerBotSettingsTab(tab);`

**Note:** `getItems` receives the **viewer**, not the bot. The per-bot GUI context is managed internally.

### Navigation Goals (`FppNavigationGoal`)

Provide custom waypoint sequences for pathfinding.

```java
public interface FppNavigationGoal {
    @Nullable Location getNextWaypoint(@NotNull FppBot bot);
    boolean isComplete(@NotNull FppBot bot);
    default double getSpeedModifier() { return 1.0; }
    default double getArrivalDistance() { return 1.5; }
    default double getRecalcDistance() { return 3.5; }
}
```

Register: `api.setNavigationGoal(bot, myGoal);`

---

## Events

All events extend `FppBotEvent` (which extends Bukkit `Event`).

| Event | Fields | Cancellable |
|-------|--------|-------------|
| `FppBotSpawnEvent` | `isRestored()` | No |
| `FppBotDespawnEvent` | — | No |
| `FppBotDeathEvent` | `getKiller()` (Player, nullable) | No |
| `FppBotDamageEvent` | `getDamage()`, `setDamage()`, `getCause()`, `getDamager()` | **Yes** |
| `FppBotAttackEvent` | `getTarget()`, `getDamage()`, `setDamage()` | **Yes** |
| `FppBotChatEvent` | `getMessage()`, `setMessage()` | **Yes** |
| `FppBotChunkLoadEvent` | `getChunk()` | No |
| `FppBotBlockBreakEvent` | `getBlock()` | **Yes** |
| `FppBotBlockPlaceEvent` | `getBlock()` | **Yes** |
| `FppBotFollowEvent` | `getAction()` (START/STOP/TARGET_CHANGE), `getTarget()` | No |
| `FppBotFreezeEvent` | `isFrozen()` | **Yes** |
| `FppBotTaskEvent` | `getTaskType()`, `getAction()` (START/STOP) | No |
| `FppBotInventoryEvent` | `getAction()` (PICKUP/DROP/EQUIP/UNEQUIP), `getItem()`, `getSlot()` | **Yes** |
| `FppBotInteractEvent` | `getTarget()`, `getHand()` | **Yes** |
| `FppBotSaveEvent` | — | No |
| `FppBotRenameEvent` | `getOldName()`, `getNewName()` | No |
| `FppBotSettingChangeEvent` | `getSettingKey()`, `getOldValue()`, `getNewValue()` | No |
| `FppBotNavigationEvent` | `getAction()` (START/RECALC/ARRIVE/FAIL/CANCEL), `getLocation()` | No |
| `FppBotTeleportEvent` | — | No |
| `FppBotWorldChangeEvent` | — | No |
| `FppBotGameModeChangeEvent` | — | No |
| `FppBotSleepStartEvent` | — | No |
| `FppBotSleepEndEvent` | — | No |
| `FppBotTargetEvent` | — | No |

**Listening:** Use Bukkit's standard `@EventHandler` pattern. Events fire on the main thread.

---

## Extension Config & Resources

### Config Auto-Management

FPP automatically manages a `config.yml` per extension:

```java
// In your FppExtension implementation:
@Override
public void onEnable(FppApi api) {
    // Extract config.yml from JAR to data folder (only if missing)
    saveDefaultConfig();
    
    // Read current config
    YamlConfiguration cfg = getConfig();
    String mode = cfg.getString("mode", "default");
    
    // Reload from disk
    reloadConfig();
}
```

**Config sync behavior:**
- On first load: extracts `config.yml` from JAR root or `extension-resources/config.yml`
- On updates: if the JAR's config has new keys, they are **auto-merged** into the existing disk config
- If the disk config is a stale core-config copy (missing `config-version`), it is **replaced** with the JAR default

### Resource Extraction

Bundle files in your JAR under `extension-resources/`:

```
MyExtension.jar
├── me/myextension/
│   └── MyExtension.class
├── extension-resources/
│   ├── messages.yml
│   └── templates/
│       └── default.txt
└── config.yml
```

Extract all bundled resources:
```java
saveDefaultResources(); // Extracts extension-resources/* to data folder
```

Extract a single file:
```java
File extracted = saveResource("templates/default.txt");
```

**Important:** Resource extraction **never overwrites** existing user files. It's safe to call on every reload.

### Cross-Extension Config Access

One extension can read another's config:

```java
// From extension A, read extension B's config
api.getExtensionConfig("OtherExtension");
api.saveDefaultExtensionConfig("OtherExtension");
```

---

## Extension Bundles

Multiple extension JARs can be packaged into a single "bundle" JAR for distribution.

The first-party `fpp-extensions` Gradle build currently creates `fpp-spoof.jar` with all official modules embedded under `extensions/<module>.jar` and copies it to workspace `builds/`.

### Manifest Format

```
FPP-Extension-Bundle: true
FPP-Extension-Jars: extensions/ext-a.jar, extensions/ext-b.jar
```

If `FPP-Extension-Jars` is omitted, FPP scans the `extensions/` directory inside the bundle JAR automatically.

### Bundle Layout

```
MyBundle.jar
├── META-INF/
│   └── MANIFEST.MF          # FPP-Extension-Bundle: true
├── extensions/
│   ├── ext-a.jar
│   └── ext-b.jar
```

On load, bundled JARs are extracted to `plugins/FakePlayerPlugin/extensions/.cache/<bundle-name>/` and loaded individually.

---

## Service Registry

Extensions can register typed services for other extensions to discover:

```java
// Extension A registers a service
api.registerService(MyService.class, new MyServiceImpl());

// Extension B retrieves it
MyService svc = api.getService(MyService.class);
if (svc != null) { ... }
```

| Method | Description |
|--------|-------------|
| `registerService(Class<T>, T instance)` | Publish a service |
| `unregisterService(Class<T>, T instance)` | Remove a service |
| `getService(Class<T>)` | Get service instance (nullable) |
| `hasService(Class<?>)` | Check if service exists |

---

## Working Examples

### Example 1: Minimal Extension

```java
package com.example;

import me.bill.fakePlayerPlugin.api.FppApi;
import me.bill.fakePlayerPlugin.api.FppExtension;
import org.jetbrains.annotations.NotNull;

public class MyExtension implements FppExtension {
    
    @Override
    public @NotNull String getName() { return "MyExtension"; }
    
    @Override
    public @NotNull String getVersion() { return "1.0.0"; }
    
    @Override
    public void onEnable(@NotNull FppApi api) {
        // Spawn a test bot
        api.spawnBot(
            new Location(Bukkit.getWorld("world"), 0, 64, 0),
            null,
            "TestBot"
        );
    }
    
    @Override
    public void onDisable() {
        // Cleanup
    }
}
```

### Example 2: Addon Command

```java
public class DanceCommand implements FppAddonCommand {
    private final FppApi api;
    
    public DanceCommand(FppApi api) { this.api = api; }
    
    @Override public @NotNull String getName() { return "dance"; }
    @Override public @NotNull String getDescription() { return "Make a bot dance!"; }
    @Override public @NotNull String getUsage() { return "<bot>"; }
    @Override public @NotNull String getPermission() { return "fpp.dance"; }
    
    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 0) return false;
        api.getBot(args[0]).ifPresent(bot -> {
            bot.swingMainHand();
            bot.swingOffHand();
            bot.setSneaking(true);
            Bukkit.getScheduler().runTaskLater(api.getPlugin(), () -> bot.setSneaking(false), 20L);
        });
        return true;
    }
}

// In onEnable:
api.registerCommand(new DanceCommand(api));
```

### Example 3: Tick Handler

```java
// Make bots bob up and down when in water
api.registerTickHandler((bot, entity) -> {
    if (bot.isInWater() && !bot.isNavigating()) {
        Vector vel = bot.getVelocity();
        vel.setY(Math.sin(System.currentTimeMillis() / 200.0) * 0.05);
        bot.setVelocity(vel);
    }
});
```

### Example 4: Event Listener

```java
public class MyListener implements Listener {
    @EventHandler
    public void onBotDeath(FppBotDeathEvent event) {
        FppBot bot = event.getBot();
        Player killer = event.getKiller();
        
        Bukkit.broadcastMessage(bot.getDisplayName() + " was slain!");
        if (killer != null) {
            killer.giveExpLevels(1);
        }
    }
    
    @EventHandler
    public void onBotDamage(FppBotDamageEvent event) {
        // Bots take double damage from lava
        if (event.getCause() == EntityDamageEvent.DamageCause.LAVA) {
            event.setDamage(event.getDamage() * 2);
        }
    }
}

// In onEnable:
Bukkit.getPluginManager().registerEvents(new MyListener(), api.getPlugin());
```

### Example 5: Settings Tab

```java
public class MyTab implements FppSettingsTab {
    @Override public @NotNull String getId() { return "mytab"; }
    @Override public @NotNull String getLabel() { return "My Tab"; }
    @Override public @NotNull Material getActiveMaterial() { return Material.DIAMOND; }
    @Override public @NotNull Material getInactiveMaterial() { return Material.DIAMOND_ORE; }
    @Override public @NotNull Material getSeparatorGlass() { return Material.GRAY_STAINED_GLASS_PANE; }
    
    @Override
    public @NotNull List<FppSettingsItem> getItems(@NotNull Player viewer) {
        return List.of(new FppSettingsItem() {
            @Override public @NotNull String getId() { return "hello"; }
            @Override public @NotNull String getLabel() { return "Say Hello"; }
            @Override public @NotNull String getDescription() { return "Click to say hello"; }
            @Override public @NotNull Material getIcon() { return Material.PAPER; }
            @Override public @Nullable String getValue() { return "Click me"; }
            @Override public void onClick(@NotNull Player viewer) {
                viewer.sendMessage("Hello from extension!");
            }
        });
    }
}

// In onEnable:
api.registerBotSettingsTab(new MyTab());
```

### Example 6: Navigation Goal

```java
public class SquarePatrolGoal implements FppNavigationGoal {
    private final Location center;
    private final double radius;
    private int corner = 0;
    
    public SquarePatrolGoal(Location center, double radius) {
        this.center = center;
        this.radius = radius;
    }
    
    @Override
    public @Nullable Location getNextWaypoint(@NotNull FppBot bot) {
        double[] dx = {radius, 0, -radius, 0};
        double[] dz = {0, radius, 0, -radius};
        Location next = center.clone().add(dx[corner], 0, dz[corner]);
        corner = (corner + 1) % 4;
        return next;
    }
    
    @Override
    public boolean isComplete(@NotNull FppBot bot) {
        return false; // Never ends
    }
    
    @Override public double getArrivalDistance() { return 2.0; }
}

// Usage:
api.setNavigationGoal(bot, new SquarePatrolGoal(bot.getLocation(), 10));
```

### Example 7: Extension Data Storage

```java
// Store custom data per bot (persists across reloads if DB is enabled)
api.setBotExtensionData(bot, "MyExtension", "kills", "42");
Map<String, String> data = api.getBotExtensionData(bot, "MyExtension");
String kills = data.get("kills");
```

---

## Building an Extension

### Gradle Setup

```kotlin
dependencies {
    compileOnly(files("libs/fpp.jar"))
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}
```

### Maven Setup

```xml
    <dependencies>
    <!-- FPP plugin JAR (provided scope) -->
    <dependency>
        <groupId>me.bill</groupId>
        <artifactId>fpp</artifactId>
        <version>1.6.6.12.8</version>
        <scope>system</scope>
        <systemPath>${project.basedir}/libs/fpp.jar</systemPath>
    </dependency>
</dependencies>
```

### Important Build Notes

- Shade **only** your own classes. Do **not** shade FPP classes or Bukkit/Paper APIs.
- Mark FPP as `provided` or `system` scope so it's not bundled.
- Target Java 21 (`release` 21).
- No `plugin.yml` needed in the extension JAR (unlike full Bukkit plugins).

---

## Troubleshooting

### Extension not loading
- Check server logs for `[Extensions] Could not load class ...` messages.
- Ensure your class implements `FppExtension` and is **not abstract**.
- Verify the class has a **public no-arg constructor**.
- Check for `NoClassDefFoundError` — you may be using classes from a missing dependency.

### ClassNotFoundException for FPP classes
- You compiled against a different FPP version. Use the exact JAR running on the server.
- Do not relocate (shade) `me.bill.fakePlayerPlugin.*` packages.

### Config not extracting
- Ensure `config.yml` is at the JAR root or inside `extension-resources/`.
- Check that `saveDefaultConfig()` is called in `onEnable()`.

### Commands not showing in help
- Verify `canUse(sender)` returns `true` for the test player.
- Check the command name doesn't conflict with a core command (case-insensitive).
- Ensure `api.registerCommand()` was actually called.

### Memory leaks on reload
- Always call the corresponding `unregister*` methods in `onDisable()`.
- Cancel any Bukkit tasks your extension started.
- Unregister Bukkit event listeners.

### First-Party Extension Bundle

The following features are **not in core** and are currently implemented as first-party `fpp-extensions` modules. Install the relevant individual jar or `fpp-spoof.jar`:

- AI conversations and personalities (`fpp-aichat`)
- Fake chat / broadcast messaging (`fpp-chat`)
- Stored right-click commands (`fpp-command`)
- Bot groups (`fpp-groups`)
- Tab-list/server-list handling (`fpp-list`)
- LuckPerms bot ranks (`fpp-luckperms`)
- NameTag integration (`fpp-nametag`)
- Extension pathfinding settings/service (`fpp-pathfinder`)
- Peak-hour scheduler (`fpp-peaks`)
- Ping command (`fpp-ping`)
- Skin command and spawn `--skin` hook (`fpp-skin`)
- Swap system (`fpp-swap`)
- Waypoint routes and patrols (`fpp-waypoints`)

Build from source with `cmd /c "fake-player-plugin\\gradlew.bat -p fpp-extensions build"` after `fake-player-plugin/build/fpp.jar` exists.

---

## Read More

For implementation details, browse the source:
- `src/main/java/me/bill/fakePlayerPlugin/api/` — All public API interfaces
- `src/main/java/me/bill/fakePlayerPlugin/api/event/` — All event classes
- `src/main/java/me/bill/fakePlayerPlugin/api/impl/FppApiImpl.java` — API implementation
- `src/main/java/me/bill/fakePlayerPlugin/extension/ExtensionLoader.java` — Loading logic
