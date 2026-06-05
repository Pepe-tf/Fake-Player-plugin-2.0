# AGENTS.md — FakePlayerPlugin

## Build
```bash
./gradlew shadowJar           # Build fat plugin JAR (build/libs/fake-player-plugin-*.jar)
./gradlew test                # Only 2 string-assertion JUnit tests; do not rely on coverage
./gradlew runServer           # Paper 1.21.11 dev server
./gradlew runFolia            # Folia 1.21.11 dev server
./gradlew runDevBundleServer  # Mojang-mapped dev server (paperweight)
./gradlew spotlessApply       # Auto-format Java + Gradle KTS
```

**Important:** 
- Use `shadowJar`, not `build` or `jar`, to produce the runnable plugin JAR.
- Java toolchain is **25** but release target is **21**. Paper dev bundle `26.1.2.build.65-stable`.
- Spotless uses `palantirJavaFormat("2.56.0")` with import order: `java, javax, org, com, me.bill`.
- CI runs `test` then `shadowJar` on Java 21 Temurin; Qodana (`qodana.starter` profile, JDK 25) runs on push to `master`/`Dev` and PRs.

---

## Critical Dev Gotchas

### License Check Blocks Startup
`FakePlayerPlugin.onEnable()` (lines ~184-198) fetches credentials from `fpp.wtf` and **disables the plugin** if unreachable.
- **Internet is required for local dev/testing.**
- The check runs before most initialization.

### Command Registration Requires Two Steps
New commands must be registered in **both**:
1. `CommandManager.java` (`register()` in constructor or init)
2. `FakePlayerPlugin.onEnable()` (lines ~330-370) — commands are instantiated there and wired to manager

### Config Migration
`ConfigMigrator` auto-runs on startup. The current `config-version` is **74** (in `src/main/resources/config.yml`). **Do not edit `config-version` manually.**

---

## Architecture

- **Entry:** `FakePlayerPlugin.java:89` — standard Bukkit `JavaPlugin` extending `JavaPlugin`
- **Main shadow JAR manifest:** `Main-Class = me.bill.fakePlayerPlugin.Launcher` (for standalone launcher), but Bukkit loads via `plugin.yml` → `FakePlayerPlugin`
- **Bot lifecycle:** `FakePlayerManager` owns spawn/despawn/tick loop and `actingBots` action-lock set
- **Pathfinding:** `PathfindingService` + `BotPathfinder` — A* with door/parkour/swim handling
- **Scheduler abstraction:** `FppScheduler` routes tasks through Folia-compatible APIs; legacy `Bukkit.getScheduler()` is prohibited (enforced by test)
- **Folia:** Runtime detected via `Class.forName("io.papermc.paper.threadedregions.ThreadedRegionizer")`; `NmsPlayerSpawner.isFoliaServer()` used in spawn chain; `folia-supported: true` in `plugin.yml`

## Current Runtime Invariants

- `NmsPlayerSpawner.spawnFakePlayer(...)` creates an NMS `ServerPlayer`, runs `placeNewPlayer(...)`, then forces the returned Bukkit `Player` back to the requested world/coordinates/rotation. Keep this correction because existing playerdata can temporarily load a saved position during login.
- `BotSpawnProtectionListener` protects newly spawned bots from delayed first-join/world-manager teleports away from the requested spawn target. It must allow only teleports to the protected target during the short protection window.
- Bot physics is not automatic for fake connections. Every live, non-frozen bot body must reach `NmsPlayerSpawner.tickPhysics(...)` every tick through `FakePlayerManager`; do not reintroduce idle-maintenance gates that skip inactive bots, or gravity/fall behavior breaks.
- `BotPersistence.saveActiveListAsync(...)` snapshots the bot list immediately before delayed async serialization. Do not store live `activePlayers.values()` views for later writes.
- WorldGuard protection must be evaluated at the bot body's current live location. `FakePlayerEntityListener`, `FakePlayerManager.tickFallDamage(...)`, `BotCollisionListener`, and `FakeServerGamePacketListenerImpl` all participate in damage/knockback protection.
- Cancelled player damage must not produce player-hit knockback. If `EntityDamageByEntityEvent` is cancelled, skip explicit `BotCollisionListener` velocity and block fake-connection motion-packet velocity.
- Manual FPP fall damage is applied from `FakePlayerManager.tickFallDamage(...)`; keep a WorldGuard current-location check before calling `bot.damage(...)`.

---

## Tests

Only `FoliaCompatibilityTest.java`:
- Asserts `plugin.yml` contains `folia-supported: true`
- Asserts `FppScheduler.java` does not contain `Bukkit.getScheduler()`

There is **no integration test harness**; Minecraft-specific logic is untested in CI.

---

## Dependencies

**Bundled:** Paper dev bundle `26.1.2.build.65-stable`, FastStats metrics `0.22.0`

**compileOnly (soft at runtime):**
- LuckPerms API (`5.5`)
- PlaceholderAPI (`2.12.2`)
- WorldGuard (`7.0.12`) — excludes Gson, Guava, fastutil
