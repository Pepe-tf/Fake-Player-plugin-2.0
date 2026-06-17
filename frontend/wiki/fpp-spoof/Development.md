# FPP Extensions Development Guide

Guide for developing custom FPP Extensions.

## Table of Contents

- [Extension Architecture](#extension-architecture)
- [Creating Your First Extension](#creating-your-first-extension)
- [Extension API Reference](#extension-api-reference)
- [Best Practices](#best-practices)
- [Debugging](#debugging)
- [Publishing Extensions](#publishing-extensions)

---

## Extension Architecture

### Extension Lifecycle

FPP Extensions follow a specific lifecycle:

1. **Discovery** - FPP scans `extensions/` folder for JAR files
2. **Loading** - Extension classes are loaded via classloader
3. **Instantiation** - No-arg constructor is called via reflection
4. **Enable** - `onEnable(FppApi api)` is called
5. **Runtime** - Extension runs and handles events/commands
6. **Disable** - `onDisable()` is called on reload/shutdown

### Classloader Isolation

Each extension JAR gets its own `URLClassLoader` with the core plugin as parent. This means:

- **Extensions cannot see each other's classes at runtime.** Only classes from the core plugin (parent classloader) are visible to all extensions.
- **Shared API classes must live in the core plugin.** The personality API (`BotProfile`, `Personality`, `ProfileService`, etc.) was moved from the `fpp-personality` extension into `me.bill.fakePlayerPlugin.api.personality` in the core plugin for this reason.
- **Use the FPP API for cross-extension communication.** The `ProfileApi` static accessor and the core API's `registerService`/`getService` methods provide safe cross-extension access without direct classloader dependency.

### Required Interface

All extensions must implement `FppExtension`:

```java
public interface FppExtension {
    String getName();
    String getVersion();
    String getDescription();
    List<String> getAuthors();
    List<String> getDependencies();
    int getPriority();
    
    void onEnable(FppApi api);
    void onDisable();
    
    // Default methods for config, services, etc.
    File getDataFolder();
    void saveDefaultConfig();
    YamlConfiguration getConfig();
    // ... more methods
}
```

### Extension Class Structure

```java
package com.example.myextension;

import me.bill.fakePlayerPlugin.api.FppApi;
import me.bill.fakePlayerPlugin.api.FppExtension;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class MyExtension implements FppExtension, Listener {
    
    private FppApi api;
    private boolean enabled = false;
    
    @Override
    public @NotNull String getName() {
        return "MyExtension";
    }
    
    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public @NotNull String getDescription() {
        return "My awesome FPP extension!";
    }
    
    @Override
    public @NotNull List<String> getAuthors() {
        return List.of("YourName");
    }
    
    @Override
    public @NotNull List<String> getDependencies() {
        return List.of(); // Or ["LuckPerms"] if required
    }
    
    @Override
    public int getPriority() {
        return 100; // Lower = loads earlier
    }
    
    @Override
    public void onEnable(@NotNull FppApi api) {
        this.api = api;
        
        // Save default config
        saveDefaultConfig();
        
        // Check if enabled in config
        if (!getConfig().getBoolean("enabled", true)) {
            return;
        }
        
        // Register commands
        api.registerCommand(new MyCommand(api));
        
        // Register event listeners
        Bukkit.getPluginManager().registerEvents(this, api.getPlugin());
        
        // Register tick handlers
        api.registerTickHandler(this::onTick);
        
        enabled = true;
        api.getPlugin().getLogger().info("MyExtension enabled!");
    }
    
    @Override
    public void onDisable() {
        if (!enabled) {
            return;
        }
        
        // Cleanup resources
        // Unregister commands
        // Cancel tasks
        
        enabled = false;
        api.getPlugin().getLogger().info("MyExtension disabled!");
    }
    
    private void onTick(me.bill.fakePlayerPlugin.api.FppBot bot, org.bukkit.entity.Player entity) {
        // Called every server tick for each bot
    }
}
```

---

## Creating Your First Extension

### Step 1: Project Setup

Create project structure:

```bash
mkdir fpp-myextension
cd fpp-myextension
mkdir -p src/main/java/com/example/myextension
mkdir -p src/main/resources
```

### Step 2: Build Configuration

Create `build.gradle.kts`:

```kotlin
plugins {
    id("java")
}

group = "com.example"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    compileOnly(files("/path/to/fpp-1.6.6.12.2.jar"))
}

tasks.jar {
    archiveClassifier.set("")
}
```

### Step 3: Extension Class

Create `src/main/java/com/example/myextension/MyExtension.java`:

```java
package com.example.myextension;

import me.bill.fakePlayerPlugin.api.FppApi;
import me.bill.fakePlayerPlugin.api.FppExtension;
import me.bill.fakePlayerPlugin.api.FppAddonCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class MyExtension implements FppExtension {
    
    private FppApi api;
    
    @Override
    public @NotNull String getName() {
        return "MyExtension";
    }
    
    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public void onEnable(@NotNull FppApi api) {
        this.api = api;
        
        // Register a command
        api.registerCommand(new HelloCommand(api));
        
        api.getPlugin().getLogger().info("MyExtension enabled!");
    }
    
    @Override
    public void onDisable() {
        api.getPlugin().getLogger().info("MyExtension disabled!");
    }
    
    // Simple command implementation
    private static class HelloCommand implements FppAddonCommand {
        private final FppApi api;
        
        HelloCommand(FppApi api) {
            this.api = api;
        }
        
        @Override
        public @NotNull String getName() {
            return "hello";
        }
        
        @Override
        public @NotNull String getDescription() {
            return "Make a bot say hello!";
        }
        
        @Override
        public @NotNull String getUsage() {
            return "<bot>";
        }
        
        @Override
        public @NotNull String getPermission() {
            return "fpp.myextension.hello";
        }
        
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
            if (args.length == 0) {
                sender.sendMessage("Usage: /fpp hello <bot>");
                return false;
            }
            
            Optional<me.bill.fakePlayerPlugin.api.FppBot> bot = api.getBot(args[0]);
            if (bot.isEmpty()) {
                sender.sendMessage("Bot not found: " + args[0]);
                return false;
            }
            
            // Make bot say hello
            api.sayAsBot(bot.get(), "Hello! I'm " + bot.get().getName());
            
            sender.sendMessage("Bot said hello!");
            return true;
        }
        
        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
            if (args.length == 1) {
                return api.getBots().stream()
                    .map(me.bill.fakePlayerPlugin.api.FppBot::getName)
                    .toList();
            }
            return List.of();
        }
    }
}
```

### Step 4: Build and Test

```bash
# Build
./gradlew build --no-daemon

# Copy to server
cp build/libs/fpp-myextension-1.0.0.jar /path/to/server/plugins/FakePlayerPlugin/extensions/

# Restart server
```

### Step 5: Test Command

In-game:
```
/fpp hello Bot1
```

---

## Extension API Reference

### FppApi - Main API Interface

#### Bot Management

```java
// Get all bots
Collection<FppBot> bots = api.getBots();

// Get specific bot
Optional<FppBot> bot = api.getBot("BotName");
Optional<FppBot> bot = api.getBot(uuid);

// Check if player is a bot
boolean isBot = api.isBot(player);

// Spawn a bot
FppBot newBot = api.spawnBot(location, spawner, "BotName");

// Despawn a bot
api.despawnBot("BotName");
```

#### Bot Properties

```java
// Get bot info
String name = bot.getName();
UUID uuid = bot.getUuid();
String displayName = bot.getDisplayName();
Location location = bot.getLocation();
int ping = bot.getPing();

// Modify bot
bot.setDisplayName("New Name");
bot.setFrozen(true);
bot.setHealth(20.0);
bot.teleport(location);
```

#### Commands

```java
// Register addon command
api.registerCommand(new MyAddonCommand());

// Register command extension
api.registerCommandExtension(new MyCommandExtension());

// Execute command as bot
api.runAsBot(bot, "say Hello!");
```

#### Events

```java
// Listen to FPP events
@EventHandler
public void onBotSpawn(FppBotSpawnEvent event) {
    FppBot bot = event.getBot();
    // Handle spawn
}

@EventHandler
public void onBotDamage(FppBotDamageEvent event) {
    if (event.isCancelled()) {
        return;
    }
    // Modify damage
    event.setDamage(event.getDamage() * 2);
}
```

#### Tick Handlers

```java
// Register tick handler
api.registerTickHandler((bot, entity) -> {
    // Called every tick for each bot
    if (bot.isInWater()) {
        bot.setVelocity(new Vector(0, 0.1, 0));
    }
});
```

#### Navigation

```java
// Navigate to location
api.navigateTo(bot, location, () -> {
    // On arrival callback
});

// Cancel navigation
api.cancelNavigation(bot);

// Custom navigation goal
api.setNavigationGoal(bot, new MyNavigationGoal());
```

#### Metadata

```java
// Store data on bot
bot.setMetadata("mykey", "myvalue");

// Retrieve data
Object value = bot.getMetadata("mykey");

// Check existence
boolean has = bot.hasMetadata("mykey");

// Remove data
bot.removeMetadata("mykey");
```

### FppBot - Bot Wrapper

#### State Methods

```java
boolean isOnline = bot.isOnline();
boolean isDead = bot.isDead();
boolean isFrozen = bot.isFrozen();
boolean isNavigating = bot.isNavigating();
boolean isInWater = bot.isInWater();
boolean isSneaking = bot.isSneaking();
```

#### Movement

```java
Location loc = bot.getLocation();
Vector vel = bot.getVelocity();
bot.setVelocity(new Vector(0, 0, 0));
bot.teleport(location);
bot.lookAt(targetLocation);
```

#### Inventory

```java
PlayerInventory inv = bot.getInventory();
ItemStack mainHand = bot.getItemInMainHand();
bot.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
```

#### Chat & AI

```java
boolean chatEnabled = bot.isChatEnabled();
bot.setChatEnabled(true);
bot.setAiPersonality("friendly");
```

---

## Best Practices

### 1. Extension Loading Order

Use priority to control load order. Lower values load earlier.

```java
@Override
public int getPriority() {
    return 100; // Default
    // Lower = earlier (0-50 for service providers)
    // Higher = later (150-200 for dependent extensions)
}
```

For example, `FPP-Personality` uses priority 0 so its `ProfileService` is registered before dependent extensions like `FPP-Chat` and `FPP-Swap` attempt to use it during their `onEnable`.

### 2. Configuration Management

Always save default config:

```java
@Override
public void onEnable(FppApi api) {
    saveDefaultConfig();
    
    YamlConfiguration config = getConfig();
    boolean enabled = config.getBoolean("enabled", true);
    
    if (!enabled) {
        return;
    }
    
    // Continue initialization
}
```

### 3. Event Listener Cleanup

Unregister listeners on disable:

```java
private HandlerList handlerList;

@Override
public void onEnable(FppApi api) {
    handlerList = new HandlerList();
    // Register listeners
}

@Override
public void onDisable() {
    if (handlerList != null) {
        handlerList.unregisterAll(this);
    }
}
```

### 4. Command Registration

Check for command conflicts:

```java
@Override
public void onEnable(FppApi api) {
    // Check if command already exists
    if (api.getRegisteredCommands().stream()
        .anyMatch(cmd -> cmd.getName().equals("mycommand"))) {
        api.getPlugin().getLogger().warning("Command already exists!");
        return;
    }
    
    api.registerCommand(new MyCommand());
}
```

### 5. Async Operations

Use BukkitScheduler for async tasks:

```java
CompletableFuture.supplyAsync(() -> {
    // Async work
    return fetchData();
}).thenAccept(result -> {
    // Back to main thread
    Bukkit.getScheduler().runTask(api.getPlugin(), () -> {
        useResult(result);
    });
});
```

### 6. Resource Management

Clean up resources on disable:

```java
private List<Integer> taskIds = new ArrayList<>();

@Override
public void onEnable(FppApi api) {
    int taskId = Bukkit.getScheduler()
        .runTaskTimer(api.getPlugin(), this::tick, 0L, 20L)
        .getTaskId();
    taskIds.add(taskId);
}

@Override
public void onDisable() {
    for (int taskId : taskIds) {
        Bukkit.getScheduler().cancelTask(taskId);
    }
    taskIds.clear();
}
```

### 7. Error Handling

Graceful error handling:

```java
try {
    // Risky operation
    fetchDataFromAPI();
} catch (IOException e) {
    api.getPlugin().getLogger().warning("Failed to fetch data: " + e.getMessage());
    // Fallback behavior
}
```

### 8. Logging

Use proper logging:

```java
Plugin plugin = api.getPlugin();
plugin.getLogger().info("Extension enabled");
plugin.getLogger().warning("Deprecated config option used");
plugin.getLogger().severe("Critical error occurred");
```

### 9. Permission Checks

Always check permissions:

```java
@Override
public boolean execute(CommandSender sender, String[] args) {
    if (!sender.hasPermission("fpp.myextension.command")) {
        sender.sendMessage("No permission!");
        return false;
    }
    
    // Execute command
    return true;
}
```

### 10. Documentation

Document your extension:

```java
/**
 * MyExtension adds cool features to FPP.
 * 
 * Features:
 * - Feature 1
 * - Feature 2
 * 
 * Commands:
 * /fpp mycommand <arg>
 * 
 * Permissions:
 * fpp.myextension.command
 */
public class MyExtension implements FppExtension {
    // ...
}
```

---

## Debugging

### Enable Debug Logging

In server startup:
```bash
java -jar server.jar --debug
```

### Add Debug Statements

```java
@Override
public void onEnable(FppApi api) {
    api.getPlugin().getLogger().info("=== MyExtension Debug ===");
    api.getPlugin().getLogger().info("API Version: " + api.getVersion());
    api.getPlugin().getLogger().info("Bot Count: " + api.getBotCount());
    api.getPlugin().getLogger().info("===========================");
}
```

### Use Breakpoints

In IDE:
1. Set breakpoint in `onEnable()`
2. Start server in debug mode
3. Attach debugger to server
4. Trigger extension load

### Check Server Logs

```bash
# View recent logs
tail -f logs/latest.log

# Search for extension messages
grep "MyExtension" logs/latest.log
```

### Common Issues

#### Extension Not Loading

Check logs for:
- `ClassNotFoundException` - Missing class
- `NoClassDefFoundError` - Missing dependency
- `InstantiationException` - No no-arg constructor

#### Commands Not Working

- Verify command name doesn't conflict
- Check permission nodes
- Ensure `onEnable()` completed successfully

#### Memory Leaks

- Check for unregistered listeners
- Cancel all tasks on disable
- Clear static references

---

## Publishing Extensions

### Prepare for Release

1. **Update version:**
   ```kotlin
   version = "1.0.0"  // Use semantic versioning
   ```

2. **Update changelog:**
   ```markdown
   ## [1.0.0] - 2026-05-25
   
   ### Added
   - Initial release
   ```

3. **Build release JAR:**
   ```bash
   ./gradlew clean build --no-daemon
   ```

4. **Test thoroughly** on clean server

### Distribution Options

#### GitHub Releases

1. Create release on GitHub
2. Upload JAR file
3. Add release notes
4. Tag version (v1.0.0)

#### SpigotMC

1. Create resource page
2. Upload JAR
3. Add description and screenshots
4. Set price (free or paid)

#### Modrinth

1. Create project
2. Upload JAR
3. Add metadata
4. Set dependencies

### Documentation

Include in your release:

- README.md with installation instructions
- CONFIG.md with configuration options
- COMMANDS.md with command reference
- PERMISSIONS.md with permission nodes
- CHANGELOG.md with version history

### Version Numbering

Follow semantic versioning:

```
MAJOR.MINOR.PATCH

1.0.0  - Initial release
1.0.1  - Bug fix
1.1.0  - New feature (backwards compatible)
2.0.0  - Breaking change
```

---

## Example Extensions

### Simple Command Extension

```java
public class PingCommand implements FppAddonCommand {
    private final FppApi api;
    
    public PingCommand(FppApi api) {
        this.api = api;
    }
    
    @Override
    public String getName() { return "botping"; }
    
    @Override
    public String getDescription() { return "Show bot ping"; }
    
    @Override
    public String getUsage() { return "<bot>"; }
    
    @Override
    public String getPermission() { return "fpp.ping.view"; }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) return false;
        
        api.getBot(args[0]).ifPresent(bot -> {
            sender.sendMessage(bot.getName() + " ping: " + bot.getPing() + "ms");
        });
        
        return true;
    }
}
```

### Event Listener Extension

```java
public class BotDeathListener implements Listener {
    private final FppApi api;
    
    public BotDeathListener(FppApi api) {
        this.api = api;
    }
    
    @EventHandler
    public void onBotDeath(FppBotDeathEvent event) {
        FppBot bot = event.getBot();
        Player killer = event.getKiller();
        
        Bukkit.broadcastMessage(bot.getDisplayName() + " died!");
        
        if (killer != null) {
            killer.giveExpLevels(1);
        }
    }
}
```

### Tick Handler Extension

```java
public class BobbingBots {
    private final FppApi api;
    private long startTime;
    
    public BobbingBots(FppApi api) {
        this.api = api;
        this.startTime = System.currentTimeMillis();
        
        api.registerTickHandler(this::tick);
    }
    
    private void tick(FppBot bot, Player entity) {
        if (bot.isInWater() && !bot.isNavigating()) {
            double offset = Math.sin((System.currentTimeMillis() - startTime) / 200.0) * 0.5;
            Vector vel = bot.getVelocity();
            vel.setY(offset);
            bot.setVelocity(vel);
        }
    }
}
```

---

## Resources

- [FPP API Javadoc](https://github.com/yourusername/fpp-extensions/javadoc)
- [FPP Extension Examples](https://github.com/yourusername/fpp-extensions/tree/main/fpp-ping/src/main/java)
- [Bukkit Plugin Development](https://www.spigotmc.org/wiki/plugin-development-tutorials/)
- [Gradle Build Tool](https://docs.gradle.org/)
- [Java Documentation](https://docs.oracle.com/en/java/)

---

## Support

- **Discord:** https://discord.gg/WRvfmV24Hh
- **GitHub Issues:** https://github.com/yourusername/fpp-extensions/issues
- **Wiki:** https://github.com/yourusername/fpp-extensions/wiki
