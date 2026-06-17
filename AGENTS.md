# AGENTS.md — FakePlayerPlugin

## Build
```bash
./gradlew shadowJar           # Build fat plugin JAR (build/libs/fake-player-plugin-<version>-all.jar)
./gradlew test                # Only 2 string-assertion JUnit tests; do not rely on coverage
./gradlew runServer           # Paper 1.21.11 dev server
./gradlew runFolia            # Folia 1.21.11 dev server
./gradlew runDevBundleServer  # Mojang-mapped dev server (paperweight)
./gradlew spotlessApply       # Auto-format Java + Gradle KTS
./gradlew spotlessCheck       # Verify formatting before CI
```

**Important:**
- Use `shadowJar`, not `build` or `jar`, to produce the runnable plugin JAR.
- Java toolchain is **25** but release target is **21**. Paper dev bundle `26.1.2.build.65-stable`.
- Spotless uses `palantirJavaFormat("2.56.0")` with import order: `java, javax, org, com, me.bill`.
- CI runs `test` then `shadowJar` on Java 21 Temurin; Qodana (`qodana.starter` profile, JDK 25) runs on push to `master`/`Dev` and PRs.

---

## Critical Dev Gotchas

### License/Attribution Check Blocks Startup
`FakePlayerPlugin.onEnable()` fetches credentials from `fpp.wtf` and **disables the plugin** if the license check throws.
- **Internet is required for local dev/testing.**
- The check runs before most initialization.
- If `fpp.wtf` is unreachable, the code falls back to offline credentials and continues, but any exception in `licenseManager.verify()` still disables the plugin.

### Command Registration
Commands are instantiated and registered in `FakePlayerPlugin.onEnable()` through `CommandManager.register(...)`. Also add permissions to `Perm.java`, `plugin.yml`, and language keys when the command needs user-facing messages.

### Core vs Extension Command Ownership
- Core `/fpp move` is **directional input only**: `MoveCommand.java` accepts `--direction forward|backward|left|right`, optional duration flags `--seconds <n>` / `--ticks <n>`, and `--stop`. Do not re-add core `--to`, `--coords`, `--pos`, or `--roam`; pathfinding movement belongs in an extension.
- Core no longer registers `/fpp follow` or `/fpp sleep`. Follow/pathfinding behavior and sleep automation should be extension-owned if needed. (These names are only referenced in ownership/tab-complete lists in `CommandManager` to keep extension hook points clear.)
- Core `/fpp attack` is the basic swing/attack command only (`--once`, `--stop`). Do not re-add `--mob`, `--hunt`, `--move`, `--range`, `--type`, or `--priority` to core; richer combat belongs in an extension.
- Core `/fpp sneak <bot> [on|off|toggle]` is registered in core and owns the `fpp.sneak` permission.

### Config Migration
`ConfigMigrator` auto-runs on startup. The current `config-version` is **76** (in `src/main/resources/config.yml`). **Do not edit `config-version` manually.**

---

## Performance Monitoring (`/fpp perf`)

Added in v1.6.6.12.7/12.8. A lightweight performance subsystem designed to make FPP self-diagnosing:

- **Provider interface:** `PerfDataProvider` in `src/main/java/me/bill/fakePlayerPlugin/perf/`; current implementations are `SparkPerfProvider` (preferred) and `BuiltinPerfProvider` (fallback).
- **Spark integration:** `me.lucko:spark-api:0.1-SNAPSHOT` is a `compileOnly` dependency. If Spark is installed, FPP reads TPS/MSPT/CPU/GC from Spark; otherwise it falls back to Bukkit/JMX.
- **Command:** `/fpp perf [check|top|report|history [1|5|15]|spark]`
  - `check`/`top`: live TPS, MSPT, CPU, GC, memory, players, bots, entities, health score.
  - `report`: logs the same to console and broadcasts to online staff with `fpp.perf`.
  - `history [1|5|15]`: rolling min/max/avg for that window.
  - `spark`: dispatches `/spark profiler --thread "Server Thread" --timeout <auto-profiler-timeout-seconds>`.
- **Background monitor:** samples every `performance.sample-interval-ticks` (default 20), keeps `performance.history-minutes` (default 15) of history, and warns after `performance.warn-consecutive-samples` consecutive threshold breaches (MSPT ≥ `warn-mspt`, TPS ≤ `warn-tps`) with a `warn-cooldown-minutes` suppression.
- **Placeholders:** `%fpp_perf_tps%`, `%fpp_perf_mspt%`, `%fpp_perf_cpu_process%`, `%fpp_perf_cpu_system%`, `%fpp_perf_gc_avg_time%`, `%fpp_perf_gc_avg_frequency%`, `%fpp_perf_health%`.
- **Config block:** under `performance:`; keys include `enabled`, `spark-enabled`, `placeholders`, `sample-interval-ticks`, `history-minutes`, `warn-mspt`, `warn-tps`, `warn-consecutive-samples`, `warn-cooldown-minutes`, `auto-profiler-timeout-seconds`.
- **Self-profiler (v1.6.6.12.8+):**
  - API: `FppProfiler` + `ProfilerToken`; use `try (ProfilerToken token = plugin.getProfiler().enter("MySection")) { ... }` or `PacketHelper.profile("...")` / `FakePlayerPlugin.profile("...")`.
  - Implementation: `BuiltinFppProfiler` with lock-free `LongAdder`, concurrent sample deque, thread-local call stack, and adaptive detail reduction (method-level disabled when MSPT ≥ 100 or single sample > 150 ms).
  - Reports: `PerformanceReportExporter` writes plain UTF-8 `.txt` to `plugins/FakePlayerPlugin/performance-report/` (`latest.txt` + timestamped + archive).
  - Benchmark session: `/fpp perf report` starts a 10-minute method-level benchmark, clears prior samples, reminds every 2 minutes, and auto-exports a Spark-style call tree. `/fpp perf report stop` ends early.
  - Triggers: manual `/fpp perf report` (benchmark), threshold warnings when `self-profiler.export-on-warning: true`, plugin disable, and fatal exceptions.
  - Config keys under `performance.self-profiler`: `enabled` (default `false`), `method-level` (default `false`), `export-on-warning` (default `false`).
  - Window defaults: rolling 1/5/15/60 minutes; report export uses configured `performance.history-minutes`.
- **Permission:** `fpp.perf`; added to `fpp.op` children in `plugin.yml`.
- **Lifecycle:** created and started in `FakePlayerPlugin.onEnable()` after FastStats; stopped in `onDisable()`. `BuiltinFppProfiler` is created immediately after `PerformanceMonitor` and stopped before monitor shutdown.

**Important:** Do not call Spark APIs on threads other than documented; the provider always reads from the server-thread-safe snapshot tick.

---

## Architecture

- **Entry:** `FakePlayerPlugin.java` — standard Bukkit `JavaPlugin`
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
- Spark API (`0.1-SNAPSHOT`) — used for `/fpp perf` metrics and Spark profiler integration

(WorldGuard is also declared `compileOnly` but the plugin README describes WorldEdit as the optional dependency; the build file excludes Gson/Guava/FastUtil from the WorldGuard artifact to avoid Paper bundle conflicts.)
