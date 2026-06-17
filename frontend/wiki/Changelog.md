# Changelog

## v1.6.6.12.8 (Performance Optimization)

### Performance Optimizations
- **Rotation broadcast cache** — `lastSentVisualRotation` map caches last broadcast yaw/pitch per bot; rotation packets dropped when delta < 0.5°, eliminating redundant head-rotation broadcasts under heavy load.
- **Direct NMS fast-paths** — `sendRotationDirect()` and `sendPositionSync()` call `PacketSendListener` directly on the NMS `ServerGamePacketListenerImpl` instead of Bukkit's `sendPacket(Player)` wrapper.
- **Tab-list batching** — `broadcastTabListRemove()` sends a single `ClientboundPlayerInfoRemovePacket` per bot instead of N individual packet sends. Applied to all despawn paths.
- **Frozen bot early return** — Skips all per-bot work (AI, physics, handlers, fall damage, position sync) for frozen bots at the top of the tick lambda.
- **Location reuse** — `before` Location captured once per tick and reused across head-AI target distance, mining-lock check, gaze vector, fall-damage delta, and position-sync threshold — eliminates redundant `bot.getLocation()` calls.
- **Throttled subsystems** — Auto-eat runs every ~4 ticks per bot. Fall damage runs every other tick (accumulated fall distance is still tracked via NMS `getFallDistance()`).
- **Active-bot UUID snapshot** — `activeBotUuids` set built once per tick for O(1) `contains()` in head-AI filtering and tab-list remove.
- **Mining-lock optimization** — Reuses `before` Location for `distanceSquared` check instead of calling `bot.getLocation()` again.

### Bugfixes
- **Position sync dependency on Head AI** — `onlineSnapshot` and player-position arrays now always populated regardless of `doHeadAi`. Previously, when Head AI was disabled, position sync packets were never sent — bots appeared frozen on other clients.
- **Swim AI jumping-field reset** — Removed incorrect `&& (isNavigating || isInWaterOrBubbleColumn(bot))` guard that skipped `tickSwimAi()`. The `jumping` field stuck at `true` after a bot exited water because `setJumping(bot, false)` was never called.
- **Ground detection for partial blocks** — `isBotOnGround()` restored to `loc.clone().subtract(0, 0.08, 0).getBlock().isPassable()`. The `getBlockAt(getBlockX(), getBlockY()-1, ...)` replacement misdetected slabs and stairs.
- **`isInBubbleColumn()` deprecation** — Replaced deprecated `Player.isInBubbleColumn()` with block-type check. `-Xlint:deprecation` added to `compileJava`.

### Performance Subsystem
- **`/fpp perf` command** — `check`/`top`/`report`/`history`/`spark` subcommands. Background monitor samples TPS, MSPT, CPU, GC, memory every `sample-interval-ticks`, keeps rolling `history-minutes`, warns on consecutive threshold breaches.
- **Built-in self-profiler** — `BuiltinFppProfiler` with lock-free `LongAdder` sampling, thread-local call stack, adaptive detail reduction. Enabled via `performance.self-profiler.enabled`.
- **Benchmark sessions** — `/fpp perf report` starts a 10-minute method-level benchmark, reminds every 2 minutes, exports Spark-style call tree to `plugins/FakePlayerPlugin/performance-report/`.
- **Perf providers** — `SparkPerfProvider` (preferred, reads Spark-API snapshots) and `BuiltinPerfProvider` (fallback via CraftServer + JMX).
- **Auto-export** — `PerformanceReportExporter` writes `.txt` reports on: benchmark finish, threshold warning (`export-on-warning: true`), plugin disable, and fatal exceptions.
- **Perf placeholders** — `%fpp_perf_tps%`, `%fpp_perf_mspt%`, `%fpp_perf_cpu_process%`, `%fpp_perf_cpu_system%`, `%fpp_perf_gc_avg_time%`, `%fpp_perf_gc_avg_frequency%`, `%fpp_perf_health%`.
- **Profiling instrumentation** — Hot paths in `FakePlayerManager.tick()`, `NmsPlayerSpawner.tickPhysics()`, `PacketHelper`, `tickSwimAi()`, `tickAutoEat()`, `fireTickHandlers()`, `tickFallDamage()` profiled when self-profiler is active.
- **Language keys** — Full `perf-*` set in `en.yml` for all `/fpp perf` output.

### Config
- **New `performance:` block** — `enabled`, `spark-enabled`, `placeholders`, `sample-interval-ticks`, `history-minutes`, `warn-mspt`, `warn-tps`, `warn-consecutive-samples`, `warn-cooldown-minutes`, `auto-profiler-timeout-seconds`, `self-profiler.enabled`, `self-profiler.method-level`, `self-profiler.export-on-warning`.
- **Swap player-aware settings** — New `swap.player-aware.*` keys for nearby-player detection radius, idle threshold, idle bonus percent, active penalty percent.
- **`ConfigMigrator`** — Updated for config-version 76 to handle new keys.
- **`FppMetrics` integration** — FastStats metrics startup with graceful fallback when FastStats is unavailable.

### API & Internal
- **`FppApi`** — Added `getOnlineCount()`, `removePlayerBody(UUID)` (shutdown-safe), `disableAllAddons()`.
- **`FppScheduler`** — `runAtEntityRepeatingWithId()` returns `int taskId` for per-entity repeating tasks.
- **`CommandManager`** — `/fpp perf` registered with `fpp.perf` permission (child of `fpp.op` in `plugin.yml`).
- **`NmsPlayerSpawner`** — `tickPhysics()` reverted to original `doTickMethod.invoke()` via reflection; guard restored to `|| doTickMethod == null`.
- **`FakePlayerEntityListener`** — Removed exact damage-canceller detector/tracer in favor of simpler `body.damageable` switch.
- **`FakeChannelPipeline`** — Handler insertion refactored for channel-active vs connection-set path.
- **`BotPersistence`** — `saveForShutdown()` saves snapshot without clearing `active-bots` to prevent destructive overwrite on restart.
- **Shutdown lifecycle** — Profiler stopped before monitor; extension class loaders closed; `disableAllAddons()` called; `saveForShutdown()` runs before body removal.

## v1.6.6.12.7 (nLogin Compatibility & Heavy Listener Suppression)

### Core Updates
- **nLogin compatibility** — `NmsPlayerSpawner` now suppresses nLogin (`com.nickuc.*`) `PlayerJoinEvent` listeners for fake players alongside the existing SimpleVoiceChat suppression. Auth plugins that expect normal client login pipelines no longer kick/despawn FPP bots during spawn.

---

## v1.6.6.12.6 (Synthetic Quit Kick Fix)

### Core Updates
- **Synthetic quit kick handling** — `FakePlayerKickListener` now marks kicked bots as synthetic quits via `FakePlayerManager.addSyntheticQuit(UUID)` before despawning. This ensures the manager treats server kicks as synthetic quits and despawns the bot with consistent quit-event semantics instead of treating it as a raw deletion.
- **`addSyntheticQuit(UUID)` helper** — added null-checked `addSyntheticQuit(UUID)` to `FakePlayerManager` so callers can safely record synthetic quit UUIDs without duplicating null-guard logic.

---

## v1.6.6.12.5 (Core Scope Reduction & Click API)

### Core Updates
- **Version bump** — plugin metadata and Gradle version are now `1.6.6.12.5`.
- **Core scope reduction** — advanced pathfinding movement, follow, sleep, and rich combat behavior moved out of core and into extension-owned systems.
- **Directional move only** — core `/fpp move` now accepts `--direction forward|backward|left|right` with optional `--seconds` / `--ticks` and `--stop`.
- **Basic attack only** — core `/fpp attack` reduced to `--once` / `--stop`. Rich hunting/mob targeting is extension-owned.
- **Sneak command** — added core `/fpp sneak <bot> [on|off|toggle]` with `fpp.sneak` permission.
- **Click API** — added public `FppClickMode` and `FppApi.leftClick/rightClick` overloads so extensions can trigger core click actions without dispatching commands.
- **Tab completion hardening** — `CommandManager` now guards tab completion against exceptions from `canUse()` and `tabComplete()` in both core and addon commands.
- **Spawn location correctness** — normal `/fpp spawn` reasserts requested spawn location after join/spawn redirects; early join handling consumes pending locations at LOWEST priority.
- **Shutdown persistence** — non-destructive shutdown save: empty snapshots do not clear `persistence.active-bots`; addon shutdown runs before final bot persistence save.
- **Inventory persistence fix** — empty inventories saved with `__empty` marker; restore clears slots before applying saved data so old items do not survive.
- **Damage/knockback** — preserved Paper/Bukkit damage event semantics; explicit FPP knockback restored for allowed damage; suppressed for cancelled events; cross-world teleports reset transient damage state.
- **Removed broad core protection gates** — WorldGuard helper removed from core; external protection plugins own cancellation decisions.
- **Removed core commands** — `FollowCommand` and `SleepCommand` removed from core; follow/sleep are extension-owned.
- **Build output** — `shadowJar` copies the shaded runnable jar to workspace root as `fake-player-plugin-1.6.6.12.5.jar`; plain `jar` refreshes `build/fpp.jar` without overwriting the deployable root jar.
- **FastStats packaging** — `shadowJar` verifies FastStats classes are present; metrics initialization is fail-safe so a thin jar cannot break startup.
- **Config** — `config-version` is `74`; reorganized and heavily documented; debug settings moved to `debug.yml`.

### First-Party Extensions
- **Gradle extension build** — `fpp-extensions` is a Gradle multi-project that builds individual module jars into workspace `builds/`.
- **Bundle rename** — aggregate first-party bundle is now `fpp-spoof.jar` instead of `fpp-extensions-bundle.jar`.
- **Current modules** — `fpp-aichat`, `fpp-chat`, `fpp-command`, `fpp-groups`, `fpp-list`, `fpp-luckperms`, `fpp-nametag`, `fpp-pathfinder`, `fpp-peaks`, `fpp-ping`, `fpp-skin`, `fpp-swap`, and `fpp-waypoints`.

---

## v1.6.6.12.4 (Debug GUI, Left-Click Combat & Stability)

### 🎯 Main Focus
- **Fix bot despawn after spawn bug** — bots no longer instantly despawn due to stale spawn-protection checks or missing WorldGuard session state after teleport/respawn
- **PacketEvents fail injection** — suppressed kicks caused by `"packetevents"` + `"inject"` errors that triggered an infinite despawn loop on every bot join
- **LuckPerms patch** — pre-caches LuckPerms user data before `placeNewPlayer()` to prevent `ServerThreadLookupException` on Folia and ensure Vault/WG hooks resolve correctly at spawn time

### 🐛 Debug GUI & Chat Broadcasting
- **Debug Settings GUI** — `/fpp settings` now has a **🐛 ᴅᴇʙᴜɢ** category with 23 clickable toggles for every `debug.yml` category (master, general, startup, NMS, database, packets, network, config-sync, chat, swap, commands, head-ai, right-click, etc.)
- **Debug Chat Broadcast** — new `debug-chat: false` key in `debug.yml`. When enabled, all `FppLogger.debug()` output is sent to online players with `fpp.op` or `fpp.notify` as in-game chat messages (gray prefix: `[ꜰᴘᴘ DEBUG/<topic>] <message>`)
- **Runtime debug toggling** — debug categories can be flipped on/off without restarting via the GUI; changes are saved to `debug.yml` immediately

### 🖱️ Left-Click Command Improvements
- **Auto-target hostile mobs** — bots now automatically detect and attack hostile mobs (Monsters, Slimes, Ghasts, Phantoms, Hoglins, Shulkers, EnderDragon) in their forward cone when no block is targeted
- **Auto-aiming** — bot head and body smoothly rotate to face the targeted mob
- **Multi-flag parsing** — fixed `--once`, `--repeat`, `--hold`, and `--stop` flag handling so multiple flags can be specified correctly in a single command

### 🔧 Bug Fixes & Stability
- **LuckPerms cache warmup** — `NmsPlayerSpawner` pre-loads LuckPerms user data before `placeNewPlayer()` to prevent `ServerThreadLookupException` on Folia
- **WorldGuard session refresh** — complete rewrite using cold re-initialization via reflection (`tryRemoveSession` + `Session.initialize()`) to prevent stale region data after bot teleports/world changes
- **Teleport/respawn WG refresh** — `FakePlayerEntityListener` adds `PlayerTeleportEvent.MONITOR` and `PlayerRespawnEvent` handlers with delayed (1-2 tick) WG session refresh
- **Spawn protection teleport fix** — `BotSpawnProtectionListener` now allows `PLUGIN` and `COMMAND` teleports during the grace window so `/fpp tph` and cross-world moves work correctly; portals are still blocked
- **Despawn reason tracking** — all `removeBot()` calls now pass descriptive reasons (`spawn_body_failed`, `command_despawn`, `gui_delete`, `badword_cleanup`, `packetevents_kick`, `kicked_by_server`, `api_despawn`, `rename_swap`, `body_remove`, etc.) instead of `"unspecified"`
- **PacketEvents kick suppression** — `FakePlayerKickListener` silently cancels kicks containing `"packetevents"` + `"inject"` instead of despawning the bot, preventing instant-despawn loops
- **Attribution/logging cleanup** — silenced license heartbeat, JSON response, and integrity check logs unless explicitly enabled via `debug.yml`
- **Placeholder formatting** — cleaned up `formatUptime` one-liner in `FppPlaceholderExpansion`
- **Help GUI formatting** — fixed indentation in lore builder

---

## v1.6.6.12.3

### 🔧 Folia Config Patch
- **Folia config issue patched** — formatting normalization across `build.gradle.kts`, `Config.java`, and `plugin.yml` to resolve Folia-related configuration loading problems

---

## v1.6.6.12.2

### ⚡ Performance & Cleanup
- **Silent License Verification** — No more startup spam (Team ID, challenge, JSON response removed)
- **Debug Logging Fixed** — All NMS-BOT messages now respect `debug.yml` (17 calls fixed)
- **Cleaner Startup Logs** — Removed backups count, name pool size, debug section from banner
- **Minimal Shutdown Log** — Reduced from 7 lines to 4 lines

### 🖱️ Click Commands
- **Left-Click Command** — Replaced MineCommand (`/fpp left-click`)
- **Right-Click Command** — Replaced UseCommand (`/fpp right-click`)
- **Legacy Removed** — 2162 lines of mine/use/place code deleted
- **Net Reduction** — ~500 lines of code removed overall

### 🔧 Config System
- **debug.yml** — All debug settings moved to separate file
- **Config v75** — Auto-migrates and removes `logging.debug.*` keys from config.yml
- **License Category Removed** — No longer needed (silent verification)

### 📦 Other Changes
- **Folia Support** — Full compatibility with region-threaded spawning
- **Permission Checks** — Bot ownership validation for `/fpp attack --all`, `/fpp follow --all`
- **New Flags** — `/fpp despawn --own`, `/fpp delete --own`
- **PlaceholderAPI** — Updated to 2.12.2

### 📝 Documentation
- Updated: Changelog, Configuration, FAQ, Getting-Started, Home
- AGENTS.md added for development reference

---

## v1.6.6.12.1

### License System Updates
- **License server migration** — Switched license verification from `license.fpp.wtf` to `app.lukittu.com`
- **Frontend credential fetch** — Credentials now fetched from `fpp.wtf/api/license/free` with HMAC signature verification
- **Improved license logging** — Better error messages and debug logging for license verification failures
- **API key authentication** — Added Bearer token authentication for frontend API requests

### Bug Fixes
- **License credentials fetch** — Fixed API key encoding for frontend authentication

---

## v1.6.6.12

### Breaking Changes
- **Folia support restored** — FPP now fully supports Folia with region-threaded bot spawning
- **Body disable system removed** — `body.enabled` config option removed. Bots always spawn with physical bodies (tab-list only mode no longer available).
- **SpigotMC distribution removed** — Plugin no longer distributed on SpigotMC. Download from Modrinth, PaperMC Hangar, or BuiltByBit.

### Features Removed
- **`%fpp_body%` placeholder** — Removed along with body disable system.
- **Body toggle in GUI** — Removed from Settings GUI (body category).
- **Skin system toggle** — Removed from Settings GUI.

### New Features & Improvements
- **Pathfinding overhaul** — Major improvements to `BotPathfinder.java` and `PathfindingService.java` with better A* navigation, gap walking, block break/place support, and stuck detection.
- **Mine command improvements** — Added actual block breaking via `nms.gameMode.destroyBlock()`, improved progress tracking, and pickup flow.
- **Use command enhancements** — Combined Use+Place functionality with `UseMode` enum, flexible targeting from bot look direction, and better ray-tracing.
- **Head AI action locking** — Added `actingBots` concurrent set to fully disable head AI while bots perform actions (mining, using, placing).

### Bug Fixes
- **PacketEvents injection error** — Added try-catch wrapper around PacketEvents registration to prevent GrimAC/ViaVersion compatibility issues from breaking bot spawns.
- **UseCommand NPE** — Fixed null pointer when storing ray-trace targets; only stores non-null targets.
- **Head AI during actions** — Bots now properly disable head rotation while performing mine/use/place actions.
- **Mining not breaking blocks** — MineCommand now actually breaks blocks via NMS game mode.

### Code Quality
- Removed `spawnBody()` config method and all references to body disable logic
- Cleaned up `FakePlayerManager.java` spawn logic (no more bodyless mode)
- Updated startup banner, metrics, and placeholders to remove body enable references
- Removed unused custom metrics from `FppMetrics.java`
- Removed outdated `AGENTS.md` file
- Added `note.md` development tracking document

### Documentation
- Updated all wiki pages to reflect Paper/Purpur/Folia support
- Updated FAQ to explicitly state Folia is supported
- Updated legal documents (copyright, privacy-policy, extensions, terms-of-service)
- Updated README.md with platform changes

---

## v1.6.6.11

### Bug Fixes
- **Online player count** — bots now correctly subtracted from real-player count in `/fpp stats` and network totals (commit `6afca8a`)
- **Database flush** — runs outside the main thread to prevent server lag spikes (`f671781`)
- **Batching logic** — added proper batching for DB writes and network heartbeats (`528cf0e`)
- Removed dead writer/health-check logic that caused unnecessary DB overhead (`fcbe072`)
- Removed pointless bot record update before clearing the list on shutdown (`8c1eb56`)

### Code Quality
- Removed unnecessarily fully qualified class names across codebase (`001416d`)
- General cleanup of dead code, unused fields, and redundant calls (`14d1803`)

### Documentation
- Updated command reference with `extension --list`, `spawn --notp`, and `attack --once` flags
- Synced config docs with `pathfinding.*`, `skin.*`, `help.*`, `ping.*`, `metrics.debug`, and `heartbeat.enabled`

---

## v1.6.6.10.1

### Attribution & Author Updates
- Hardcoded original author updated from `el_pepes` to `F_PP` across codebase

### FastStats Metrics System Overhaul
- **ErrorTracker** — context-aware error tracking via FastStats API
- **Debug toggle** — `metrics.debug` option in `config.yml` (default `false`)
- **onFlush callback** — logs at debug level when metrics are flushed to FastStats
- **New metrics added**: `active_features` (string array), feature flags, installed plugins (LuckPerms, PlaceholderAPI, WorldGuard, WorldEdit, NameTag), server info, PvE settings, automation toggles
- **trackError() helpers** — two public overloads (`Throwable` and `String`) for external error reporting
- Added `getFppMetrics()` public getter on `FakePlayerPlugin.java`

### Bug Fixes
- **FakeChannelPipeline deprecation warning** — added `@SuppressWarnings("deprecation")` to suppress unavoidable Netty `ChannelPipeline` API deprecation warnings for `EventExecutorGroup` overloads
- **PluginRemapper duplicate entries** — `pom.xml` now properly excludes Mojang-mapped `paper-server` NMS classes from shaded JAR, fixing Paper 1.21.11 runtime remapping crash
- **SQLite AUTO_INCREMENT syntax** — split `fpp_network_tasks` table creation into SQLite (`INTEGER PRIMARY KEY AUTOINCREMENT`) and MySQL (`BIGINT AUTO_INCREMENT`) variants, fixing `SQLITE_ERROR near "AUTO_INCREMENT": syntax error`

### Documentation
- Full wiki sync: added missing `pathfinding.*`, `skin.*`, `help.*`, `ping.*`, `metrics.debug`, `heartbeat.enabled`, and `body.drop-items-on-despawn` config keys
- Added missing commands (`extension`, `extension --list`) and flags (`spawn --notp`, `spawn <bottype>`, `attack --once`, `find --prefer-visible`, short flags `-r`/`-c`)
- Added missing permissions (`fpp.mine.wesel`, `fpp.place.wesel`)
- Added extension-dependency notes for placeholders (`peak_hours`, `swap`, etc.) and config keys (`fake-chat`, `swap`, `peak-hours`)

### Deprecations & Removals
- None

---

## v1.6.6.10

**Requires MySQL for cross-server features.**

### Network Architecture  
**Proxy-merged database** — all backends share live bot registry and player counts via MySQL.
- Schema v25: `fpp_network_bots`, `fpp_server_heartbeat`, `fpp_network_tasks`
- **NetworkHeartbeatManager** — publishes local bots / reads remote bots every 5s, stale pruning every 60s
- Proxy companions (Velocity + Bungee) push `NETWORK_STATS` to all backends independently of players
- `RemoteBotCache` now survives restarts via DB (no longer messaging-only)

### PlaceholderAPI — 70+ placeholders  
New cross-server placeholders: `%fpp_network_total%`, `%fpp_network_real%`, `%fpp_network_bots%`  
Also added: server performance, extensions, 30+ config toggles, player-relative per-world, per-bot dynamic lookups.

### Extension System  
- `/fpp extension` bare command → marketplace link  
- `/fpp extension --list` → loaded extensions detail table  
- Extension data folders fixed (`getName()` instead of JAR filename)

### Deprecations & Fixes  
- `getServers()` → `getServersCopy()`, `FixedMetadataValue` → `PersistentDataContainer`, unchecked warnings cleaned
- Startup banner shows extension count  
- Authors updated to `F_PP`

### Legal  
Added `frontend/legal/` pages (copyright, extension policy, privacy, ToS)

---

## v1.6.6.9
- Fall damage tracking + config
- Skin injector fixes
- Config migrator v71→v72
- Extension bundles, API additions
- Wiki marketplace links

## v1.6.6.8
- Spoofing/chat-related features moved out of core into first-party extensions (chat, AI, swap, peak-hours, ping, groups, stored cmds)
- PvE Smart Attack Mode (OFF / ON_NO_MOVE / ON_MOVE)
- `/fpp save`, `/fpp setowner`
- Per-bot overrides: respawn-on-death, auto-eat, auto-place-bed
- BotSettingGui PvE + Pathfinding tabs, share control
- DB schema v22: PvE, automation, ping, LuckPerms

## v1.6.6.6
- Folia scheduling guards
- Water-path stability fixes
- Spawn grace-period protection

## v1.6.6.2
- BungeeCord companion plugin support
- `AttributeCompat` fix

## v1.6.6
- `/fpp follow`
- Skin persistence
- Server-list config additions
- DB schema v17

## v1.6.5
- `/fpp ping`
- `/fpp attack`
- Permission restructure
- Skin mode rename
- `FlagParser` utility

## Older Versions
https://github.com/Pepe-tf/fake-player-plugin/commits/main

---

> **Note:** The built-in ConfigMigrator handles upgrades transparently. Current default config version: **74**. Always back up `plugins/FakePlayerPlugin/` before major updates.

---

## Migration Notes (v1.6.6.12.4)

### New `debug-chat` Key
If you are upgrading from an older version, `debug.yml` will be recreated from the template. The new `debug-chat: false` key controls whether debug output is broadcast to OP/notify players in-game. You can also toggle it via `/fpp settings` → **🐛 ᴅᴇʙᴜɢ**.

### `debug.yml` Runtime Editing
Prior to v1.6.6.12.4, `debug.yml` could only be edited by hand. The Settings GUI now lists every debug category as a clickable toggle. Changes are saved to disk immediately.

---

## Migration Notes (v1.6.6.12)

### From Folia to Paper/Purpur (or vice versa)
FPP now supports both Paper/Purpur and Folia. If you were running FPP on Folia:
1. FPP will work out of the box on both platforms
2. Bot spawning automatically detects Folia and uses region scheduler
3. No migration needed - FPP handles both seamlessly

### Body Disable System Removed
If you were using `body.enabled: false` for tab-list only mode:
- This option has been removed
- All bots now spawn with physical bodies
- Consider using `body.damageable: false` and `body.pushable: false` for invulnerable/immobile bots
