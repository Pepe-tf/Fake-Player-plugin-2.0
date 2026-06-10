# Configuration

Main file: `plugins/FakePlayerPlugin/config.yml`

Run `/fpp reload` to apply most changes without restarting.

## Structure

### `config-version`
Managed automatically by the built-in migrator. **Do not edit.**

### `language`
Default: `en`. Points to `plugins/FakePlayerPlugin/language/<lang>.yml`.

---

## 1. Spawning

### `limits`
- `max-bots: 1000` — global cap (`0` = unlimited)
- `user-bot-limit: 1` — default personal limit for `fpp.use` players
- `spawn-presets: [1, 5, 10, 15, 20]` — tab-complete suggestions for `/fpp spawn`

### `spawn-cooldown`
Seconds between `/fpp spawn` uses. `0` = disabled.

### `persistence`
- `enabled: true` — bots save position on shutdown and rejoin on restart
- `restore-delay-ticks: 0` — delay before restore starts after plugin enable
- `restore-batch-size: 1` — bots restored per batch

### `heartbeat`
- `enabled: true` — controls whether the network heartbeat manager publishes server status

> Note: `join-delay` and `leave-delay` were removed in recent versions.

---

## 2. Appearance

### `bot-name`
- `mode: random` — `random` (generate username) or `pool` (pick from `bot-names.yml`)
- `admin-format: '{bot_name}'` — display name for admin spawns
- `user-format: 'bot-{spawner}-{num}'` — display name for user spawns

### `badword-filter`
- `enabled: true` — block/rename bad names
- `use-global-list: false` — fetch remote profanity list
- `global-list-url: "..."` — remote word list URL
- `global-list-timeout-ms: 5000` — fetch timeout
- `words: []` — inline word list (merged with `bad-words.yml`)
- `whitelist: []` — allowed names even if they match bad words
- `auto-rename: true` — silently rename bad names instead of blocking
- `auto-detection`
  - `enabled: true`
  - `mode: normal` — `off` / `normal` / `strict`

### `bot-interaction`
- `right-click-enabled: true` — right-click opens inventory/executes command
- `shift-right-click-settings: true` — shift+right-click opens bot settings GUI

### `messages`
- `join-message: true` — broadcast join message
- `leave-message: true` — broadcast leave message
- `death-message: true` — broadcast vanilla death message
- `kill-message: false` — broadcast when a real player kills a bot
- `notify-admins-on-join: true` — send compatibility warnings to admins on join

### `metrics`
- `enabled: true` — anonymous FastStats usage statistics

---

## 3. Body & Combat

### `body`
- `pushable: true` — players/explosions can push bots
- `damageable: true` — take all damage (if `false`, still takes environmental)
- `pick-up-items: true`
- `pick-up-xp: true`
- `drop-items-on-despawn: false` — `true` drops inventory on despawn; `false` keeps it

### `combat`
- `max-health: 20.0` — standard player HP
- `hurt-sound: true`
- `fall-damage`
  - `enabled: true`
  - `safe-distance: 3.0` — blocks before damage starts
  - `multiplier: 1.0` — damage scale

### `death`
- `respawn-on-death: false` — respawn at spawn location after death
- `respawn-delay: 1` — ticks before respawn
- `suppress-drops: false` — `true` = suppress all drops

### `skin`
- `mode: player` — `player`, `random`, or `none`
- `clear-cache-on-reload: true` — clear memory cache on `/fpp reload`
- `guaranteed-skin: true` — attempt to ensure every bot gets a valid skin
- `pool: []` — list of Minecraft usernames to use as skin sources
- `overrides: {}` — per-bot-name skin overrides (`bot_name: skin_source_player`)
- `use-skin-folder: false` — load `.png` skin files from `plugins/FakePlayerPlugin/skins/`
- `mineskin`
  - `url-upload-enabled: false`
  - `api-key: ""`
  - `visibility: private`

> Core keeps neutral skin persistence and profile refresh support. The user-facing `/fpp skin` command and spawn `--skin` hook are provided by the first-party `fpp-skin` extension.

### `automation`
Defaults copied to newly spawned bots:
- `auto-eat: true`
- `auto-place-bed: true`
- `auto-milk: true`
- `prevent-bad-omen: true`

---

## 4. AI & Navigation

### `head-ai`
- `enabled: true` — smooth head rotation toward nearest player
- `look-range: 8.0` — detection radius
- `turn-speed: 0.3` — smoothing (0.0 = frozen, 1.0 = instant)
- `tick-rate: 3` — scan every N ticks

### `swim-ai`
- `enabled: true` — automatic upward swimming

### `collision`
- `walk-radius: 0.85` — push radius when walking into a bot
- `walk-strength: 0.22`
- `hit-strength: 0.45`
- `hit-max-horizontal-speed: 0.80`
- `bot-radius: 0.90` — bot-vs-bot separation radius
- `bot-strength: 0.14`
- `max-horizontal-speed: 0.30`

### `pathfinding`
Pathfinding tuning keys:
- `parkour: true` — walk across gaps
- `break-blocks: true` — break obstructing blocks while pathing
- `place-blocks: true` — place bridging blocks while pathing
- `place-material: STONE` — material used for bridging
- `arrival-distance: 1.5` — blocks before a simple destination is considered reached
- `patrol-arrival-distance: 2.0` — arrival threshold for patrol loops
- `waypoint-arrival-distance: 2.0` — arrival threshold for waypoint navigation
- `sprint-distance: 6.0` — distance at which the bot starts sprinting
- `follow-recalc-distance: 5.0` — distance before recalculating follow path
- `follow-recalc-interval: 20` — ticks between follow path recalcs
- `recalc-interval: 40` — ticks between normal path recalculations
- `stuck-ticks: 20` — ticks before a stuck-check fires
- `stuck-threshold: 3` — consecutive stuck ticks before giving up
- `break-ticks: 20` — delay between block-break attempts while moving
- `place-ticks: 20` — delay between block-place attempts while moving
- `max-fall: 10.0` — maximum fall distance the navigator will voluntarily traverse
- `max-range: 128.0` — maximum search distance
- `max-nodes: 3200` — node limit for standard pathfinding
- `max-nodes-extended: 6400` — node limit for long-distance searches
- `detour-attempts: 2` — how many times to try detouring around obstacles
- `detour-radius: 1` — extra blocks to look around obstacles

---

## 5. Database & Network

### `database`
- `enabled: true` — `false` = file-only persistence
- `mode: "LOCAL"` — `"LOCAL"` or `"NETWORK"`
- `server-id: "default"` — unique name per backend (NETWORK mode only)
- `mysql-enabled: false`
- `mysql` — host, port, database, username, password, use-ssl, pool-size, connection-timeout
- `location-flush-interval: 30` — seconds between position DB writes
- `session-history.max-rows: 20` — max rows per `/fpp info` query

### `config-sync`
- `mode: "DISABLED"` — `"DISABLED"`, `"MANUAL"`, `"AUTO_PULL"`, `"AUTO_PUSH"`

---

## 6. Chunk Loading

### `chunk-loading`
- `enabled: true` — keep chunks loaded around bots
- `radius: "auto"` — `"auto"`, `0` = disabled, or fixed number
- `update-interval: 20` — ticks between position checks
- `mass-disable-threshold: 100` — release chunk tickets when active bots exceed this (`0` = never)

---

## 7. Performance

### `performance`
- `position-sync-distance: 128.0` — max distance (blocks) for per-tick position-sync packets. `0` = send to all players regardless of distance.

---

## 8. Heartbeat

### `heartbeat`
- `enabled: true` — controls server liveness publishing to the network database (NETWORK mode only)

---

## 9. Debug & Logging

### `debug.yml` (Separate File)
Debug logging is now controlled by `plugins/FakePlayerPlugin/debug.yml` for better organization.

**Master switch:**
- `enabled: false` — Enable all debug categories at once

**Debug chat broadcast:**
- `debug-chat: false` — When `true`, all debug output is also sent to online **OP / notify** players as in-game chat messages

**NMS debug:**
- `nms.enabled: false` — General NMS operations
- `nms.bot: false` — Bot lifecycle (spawn, despawn, inventory)
- `nms.connection: false` — Network connection/packet events
- `nms.physics: false` — Physics/collision/knockback
- `nms.skin: false` — Skin fetching and application

**Database debug:**
- `database.enabled: false` — General database operations
- `database.connection: false` — Connection pool lifecycle
- `database.operations: false` — SQL queries and transactions
- `database.migration: false` — Schema migration
- `database.persistence: false` — Bot persistence save/restore

**Feature debug:**
- `general: false` — General plugin operations
- `commands: false` — Command execution
- `chat: false` — Bot chat AI
- `network: false` — Cross-server network (Velocity/BungeeCord)
- `config-sync: false` — Config synchronization
- `startup: false` — Plugin initialization
- `right-click: false` — Right-click automation
- `right-click-head: false` — Right-click head rotation
- `head-ai: false` — Head AI tracking
- `swap: false` — Swap system debug category (used when the first-party `fpp-swap` extension is installed)
- `packets: false` — Packet injection/manipulation

> You can toggle any of these at runtime via **`/fpp settings`** → the **🐛 ᴅᴇʙᴜɢ** category. Changes are saved to `debug.yml` immediately. Run `/fpp reload` after manual edits to `debug.yml`.
---

## Extension-Only Settings

The following systems are first-party extensions. Their current defaults live in each extension data folder under `plugins/FakePlayerPlugin/extensions/<extension-name>/config.yml`:

- `fpp-aichat` — AI personalities and direct/public AI chat
- `fpp-chat` — fake chat event triggers, bot-to-bot replies, keyword reactions
- `fpp-command` — stored right-click command editor
- `fpp-groups` — personal bot groups
- `fpp-list` — bot tab-list and server-list count/sample handling
- `fpp-luckperms` — bot LuckPerms group/display integration
- `fpp-nametag` — external NameTag plugin integration
- `fpp-pathfinder` — extension pathfinding settings tabs and tuning
- `fpp-peaks` — peak-hour bot scheduling
- `fpp-ping` — ping command and ping simulation
- `fpp-skin` — skin command and spawn `--skin` hook
- `fpp-swap` — bot session rotation
- `fpp-waypoints` — route and patrol storage

---

The plugin includes a built-in **ConfigMigrator** that:
1. Creates a timestamped backup before any change
2. Automatically upgrades configs when `config-version` is outdated
3. Removes obsolete keys and adds new defaults

Do **not** edit `config-version` manually.
