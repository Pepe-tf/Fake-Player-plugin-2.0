# Changelog

## v1.6.6.12.1 (Current)

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
- **Folia support removed** — FPP no longer supports Folia due to fundamental incompatibilities with regionised threading and entity ticking. Use Paper/Purpur instead.
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
- Updated all wiki pages to reflect Paper/Purpur-only support
- Removed Folia-Support wiki page
- Updated FAQ to explicitly state Folia is not supported
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
- Authors updated to `F_PP` and `Kyttu`

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

### From Folia to Paper/Purpur
If you were running FPP on Folia:
1. Switch to **Paper** or **Purpur** 1.21.11
2. Migrate your world data using standard Folia→Paper migration tools
3. FPP will work out of the box on Paper/Purpur

### Body Disable System Removed
If you were using `body.enabled: false` for tab-list only mode:
- This option has been removed
- All bots now spawn with physical bodies
- Consider using `body.damageable: false` and `body.pushable: false` for invulnerable/immobile bots
