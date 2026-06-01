# Changelog

## v1.6.6.12.2 (Current)

### Major Features
- **Left/Right Click Commands** — Replaced legacy Mine/Use/Place commands with unified click automation
  - `/fpp left-click <bot> [--once|--repeat|--hold|--stop]` — Bot left-clicks (breaks blocks, attacks entities)
  - `/fpp right-click <bot> [--once|--repeat|--hold|--stop]` — Bot right-clicks (uses items, interacts)
  - Supports walking to target before clicking if out of reach
  - Ray-tracing for block and entity targeting
  - Integrated with FindCommand for automated mining workflows

- **Folia Support Restored** — Full compatibility with Folia's region-threaded architecture
  - Automatic Folia detection at startup
  - Bot spawning routes through region scheduler when on Folia
  - `folia-supported: true` in plugin.yml
  - FppScheduler guards for cross-thread operations

- **Launcher Entrypoint** — Added standalone launcher for JAR execution
  - Opens wiki homepage when JAR is executed directly
  - Main-Class manifest attribute set in shadowJar

### License System Updates
- **Silent Verification** — License verification now runs silently without debug spam
  - Removed verbose logging (Team ID, Product ID, challenge, response JSON)
  - Only shows error message if verification fails
  - Cleaner startup logs for production servers

- **Offline Fallback Mode** — Plugin no longer disables when credentials fetch fails
  - Creates minimal dummy credentials to continue in limited mode
  - Improved error messages and logging
  - Discord support link added to warning messages

### Debug Logging Cleanup
- **Config-Fixed Debug Methods** — All debug methods now independent and respect `debug.yml`
  - Fixed `debugNmsBot()`, `debugNms()`, `debugNmsPhysics()` to not cascade
  - 17 debug log calls in `FakePlayerManager.java` and `NmsPlayerSpawner.java` fixed
  - Bot despawn operations no longer spam console when debug disabled

- **debug.yml Updated** — Removed `license` debug category (no longer needed)
  - All debug settings now in separate file for better organization
  - Master `enabled: false` switch controls all categories

### Startup & Shutdown Logs
- **Startup Banner Simplified** — Removed clutter from startup logs
  - Removed `Backups` count (not useful for most users)
  - Removed `Name pool` size (internal detail)
  - Removed `Debug` section (showed authors when debug enabled)
  - Cleaner, more focused information display

- **Shutdown Banner Minimal** — Reduced from 7 lines to 4 lines
  - Shows only session uptime and bots removed
  - Removed: bots saved, tasks persisted, DB sessions details

### Config Migration System
- **v75 Migration** — Removes `logging.debug.*` keys from config.yml
  - All debug settings now live in `debug.yml` only
  - Automatic migration on first startup after update
  - Keys removed: `logging.debug.startup`, `logging.debug.nms`, `logging.debug.packets`, `logging.debug.network`, `logging.debug.config-sync`, `logging.debug.database`, `logging.debug.skin`, `logging.debug.license`, `logging.debug.commands`, `logging.debug.chat`, `logging.debug.swap`, `logging.debug.right-click`, `logging.debug.right-click-head`, `logging.debug.head-ai`, `logging.debug.general`

### Permission System
- **BotAccess Checks** — Added ownership validation for multi-bot operations
  - `/fpp attack --all` now respects bot ownership
  - `/fpp follow --all` checks admin permissions
  - `/fpp despawn --own` — New flag to despawn only your own bots
  - Non-admin players can only administer bots they spawned

### Command Changes
- **MineCommand Removed** — Functionality moved to LeftClickCommand
- **UseCommand Removed** — Functionality moved to RightClickCommand
- **PlaceCommand** — Still available, integrated with click system
- **FindCommand** — Updated to work with click commands instead of mine
- **StopCommand** — Updated to stop left/right click tasks
- **AttackCommand** — Added `--stop` flag (removed legacy `stop` keyword)
- **DeleteCommand** — Added `--own` flag for user-tier bot removal

### Documentation
- **AGENTS.md Added** — Development guide for AI assistants
  - Project overview and architecture
  - Critical gotchas (license, Folia, command registration)
  - Build commands and testing checklist
  - Package structure reference

### Bug Fixes
- **Tab Complete** — Removed duplicate legacy keywords
- **Permission Checks** — Fixed admin bypass for bulk operations
- **Persistence Wiring** — Updated to use click commands instead of mine/use/place
- **Debug Spam** — Fixed all NMS-BOT debug messages respecting config

### Build System
- **Version Bump** — 1.6.6.12.1 → 1.6.6.12.2
- **Manifest Attributes** — Added Main-Class for launcher support
- **Dependencies** — PlaceholderAPI updated to 2.12.2

### Code Quality
- Removed 1449 lines of legacy MineCommand code
- Removed 713 lines of legacy UseCommand code
- Added 733 lines for LeftClickCommand
- Added 932 lines for RightClickCommand
- Net reduction: ~500 lines of code
- Improved separation of concerns for click automation

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
- Updated command reference with `extension --list`, `spawn --notp`, and `attack --move` flags
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
- Added missing commands (`extension`, `extension --list`) and flags (`spawn --notp`, `spawn <bottype>`, `attack --move`, `find --prefer-visible`, short flags `-r`/`-c`)
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
- Spoofing moved to `fpp-spoof.jar` extension (chat, AI, swap, peak-hours, ping, groups, stored cmds)
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

> **Note:** The built-in ConfigMigrator handles upgrades transparently. Current config version: **73**. Always back up `plugins/FakePlayerPlugin/` before major updates.

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
