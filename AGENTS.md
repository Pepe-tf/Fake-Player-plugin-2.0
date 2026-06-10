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
- `shadowJar` also copies the runnable jar to the workspace root as `fake-player-plugin-<version>.jar`.
- Java toolchain is **25** but release target is **21**. Paper dev bundle `26.1.2.build.65-stable`.
- Spotless uses `palantirJavaFormat("2.56.0")` with import order: `java, javax, org, com, me.bill`.
- CI runs `test` then `shadowJar` on Java 21 Temurin; Qodana (`qodana.starter` profile, JDK 25) runs on push to `master`/`Dev` and PRs.

---

## Critical Dev Gotchas

### License Check Blocks Startup
`FakePlayerPlugin.onEnable()` (lines ~184-198) fetches credentials from `fpp.wtf` and **disables the plugin** if unreachable.
- **Internet is required for local dev/testing.**
- The check runs before most initialization.

### Command Registration
Commands are instantiated and registered in `FakePlayerPlugin.onEnable()` through `CommandManager.register(...)` (lines ~300-350). Also add permissions to `Perm.java`, `plugin.yml`, and language keys when the command needs user-facing messages.

### Core vs Extension Command Ownership
- Core `/fpp move` is **directional input only**: `MoveCommand.java` accepts `--direction forward|backward|left|right`, optional duration flags `--seconds <n>` / `--ticks <n>`, and `--stop`. Do not re-add core `--to`, `--coords`, `--pos`, or `--roam`; pathfinding movement belongs in an extension.
- Core no longer registers `/fpp follow` or `/fpp sleep`. Follow/pathfinding behavior and sleep automation should be extension-owned if needed.
- Core `/fpp attack` is the basic swing/attack command only (`--once`, `--stop`). Do not re-add `--mob`, `--hunt`, `--move`, `--range`, `--type`, or `--priority` to core; richer combat belongs in an extension.
- Core `/fpp sneak <bot> [on|off|toggle]` is registered in core and owns the `fpp.sneak` permission.

### Config Migration
`ConfigMigrator` auto-runs on startup. The current `config-version` is **74** (in `src/main/resources/config.yml`). **Do not edit `config-version` manually.**

---

## Architecture

- **Entry:** `FakePlayerPlugin.java:89` — standard Bukkit `JavaPlugin` extending `JavaPlugin`
- **Main shadow JAR manifest:** `Main-Class = me.bill.fakePlayerPlugin.Launcher` (for standalone launcher), but Bukkit loads via `plugin.yml` → `FakePlayerPlugin`
- **Bot lifecycle:** `FakePlayerManager` owns spawn/despawn/tick loop and `actingBots` action-lock set
- **Pathfinding:** `PathfindingService` + `BotPathfinder` remain available to internal legacy services, but user-facing pathfinding movement commands (`move --to/--coords/--roam`, follow, sleep navigation) are no longer core-owned.
- **Scheduler abstraction:** `FppScheduler` routes tasks through Folia-compatible APIs; legacy `Bukkit.getScheduler()` is prohibited (enforced by test)
- **Folia:** Runtime detected via `Class.forName("io.papermc.paper.threadedregions.ThreadedRegionizer")`; `NmsPlayerSpawner.isFoliaServer()` used in spawn chain; `folia-supported: true` in `plugin.yml`

## Current Runtime Invariants

- `NmsPlayerSpawner.spawnFakePlayer(...)` creates an NMS `ServerPlayer`, publishes a short-lived pending requested spawn location, runs `placeNewPlayer(...)`, then forces the returned Bukkit `Player` back to the requested world/coordinates/rotation. Keep both the early join correction and the post-place fallback because Paper can place new fake players in the main/default level before login finalization.
- `PlayerJoinListener.onJoinEarly(...)` consumes pending fake-player spawn locations by UUID before manager lookup and applies the requested world/coordinates/rotation at LOWEST priority. This must cover normal spawns, `/fpp spawn --notp`, and restart-persistence restores. `BotSpawnProtectionListener` and delayed spawn-location reassertions have been removed.
- Bot physics is not automatic for fake connections. Every live, non-frozen bot body must reach `NmsPlayerSpawner.tickPhysics(...)` every tick through `FakePlayerManager`; do not reintroduce idle-maintenance gates that skip inactive bots, or gravity/fall behavior breaks.
- `BotPersistence.saveActiveListAsync(...)` snapshots the bot list immediately before delayed async serialization. Do not store live `activePlayers.values()` views for later writes.
- Shutdown persistence must be non-destructive. `FakePlayerPlugin.onDisable()` saves the active bot snapshot before body removal, `BotPersistence.saveForShutdown(...)` disables later active-list rewrites, and empty shutdown snapshots must not overwrite/clear `persistence.active-bots`. Do not clear `active-bots` during `/stop`, `/restart`, plugin disable, shutdown, or restore scheduling; let manual despawns and successful restore completion rewrite the list.
- External protection plugins should own PvP/god-mode cancellation. Do not re-add broad core WorldGuard/PvP gates in `FakePlayerEntityListener` or `BotCollisionListener` that make bots immune or unpushable in wilderness.
- `FakePlayerEntityListener` keeps the built-in `body.damageable` switch: when false, entity/player damage to bots is cancelled; when true, normal damage is allowed. The old exact damage-canceller detector/tracer has been removed.
- Bot damage must preserve Bukkit/Paper event semantics. Cancelled damage stays cancelled; do not manually subtract health or force damage through external plugin cancellations.
- `BotCollisionListener` applies explicit FPP knockback for allowed damage because fake connections do not receive reliable vanilla player knockback. It must continue to suppress explicit knockback for cancelled damage events.
- `LeftClickCommand` and `RightClickCommand` must never select, store, attack, or interact with the acting bot as their own target. Use UUID equality checks instead of object-reference checks because CraftBukkit wrappers can differ.
- Cross-world bot teleports must reset transient damage/knockback state (`noDamageTicks`, velocity, fall tracking, jump/head caches) after the teleport completes.
- Manual FPP fall damage is applied from `FakePlayerManager.tickFallDamage(...)`; keep its safety/reset-block behavior and minimum 4-block damage start intact.

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
- WorldEdit Bukkit (`7.3.0`) — used for compatible selection helpers
