# FakePlayerPlugin Wiki

> Advanced NPC / Bot Plugin for Paper/Purpur/Folia 1.21+

Welcome to the FakePlayerPlugin (FPP) wiki. FPP spawns server-side bot entities that behave like
players - useful for **AFK farms, automated tasks, testing, and NPC simulations**. It is **not** a
fake-online-count or player-spoofing tool: bots are hidden from the tab list and server-list ping,
always wear a mandatory "ʙᴏᴛ ʙʏ {owner}" nametag, and can never take a real account's identity.

---

## Getting Started

| Page | Description |
|------|-------------|
| [Getting Started](Getting-Started) | Installation, first setup, and quick start |
| [Commands](Commands) | Full command reference with examples |
| [Permissions](Permissions) | Permission nodes and setup guide |
| [Configuration](Configuration) | config.yml reference and tuning |

## Systems

| Page | Description |
|------|-------------|
| [Economy](Economy) | Bot rental - pay real currency for a bot and hours; Vault / ExcellentEconomy / custom shop plugins |
| [Placeholders](Placeholders) | PlaceholderAPI integration reference |
| [Database](Database) | SQLite / MySQL setup, network tables, and proxy-merged architecture |
| [Proxy Support](Proxy-Support) | Velocity / BungeeCord multi-server networks with shared MySQL |
| [Config Sync](Config-Sync) | Synchronize configs across proxy backends |

## Reference

| Page | Description |
|------|-------------|
| [FAQ](FAQ) | Common questions and troubleshooting |
| [Changelog](Changelog) | Version history and release notes |

---

## Quick Links

- **Source:** https://github.com/Pepe-tf/Fake-Player-plugin-2.0
- **Discord:** https://discord.gg/Q9cd9frzRt
- **Modrinth:** https://modrinth.com/plugin/fake-player-plugin-(fpp)
- **License:** MIT

---

## Latest Version: v2.0.6.1

**Highlights:**
- 📍 **`/fpp spawn --location`** - admins can spawn a bot at any `<x> <y> <z> <world>` instead of only
  their own location, and can now do it from the console or a command block too (`--location` required)
- 🛠️ **Smarter Left/Right-Click** - `left-click` auto-equips the best available tool before mining,
  and `right-click` now falls back to the off-hand item when the main hand does nothing, matching a
  real client's own hand-loop
- 🔑 **Bot Auth System** - bots auto-register/login against an installed login plugin (nLogin, AuthMe,
  LoginSecurity, and friends), remembering an encrypted per-bot password so only the first join ever
  registers; manage it via `/fpp auth` or the settings GUI's new **🔐 ᴀᴜᴛʜ** category
- 🎨 **"Bot Console" Color Theme** - every message, GUI, and console log now uses one consistent
  violet/lime palette instead of scattered ad-hoc colors
- 💰 **Bot Rental Economy** - pay real currency (Vault, "Vault2.0", or ExcellentEconomy) for a bot and
  hours of runtime via `/fpp rent buy`/`extend`; `/fpp rent give` is a zero-economy-required entry
  point for wiring up your own shop plugin instead - see [Economy](Economy)
- 🧵 **Multitasking** - `move`, `find`, `left-click`, `right-click`, `attack`, and PVE auto-combat can
  all run at once on the same bot instead of one cancelling the others; movement (the one true
  single-body limit) is arbitrated by priority instead of by hijacking
- ⏱️ **Configurable click pacing** - `/fpp left-click`/`/fpp right-click` `--repeat`/`--hold` interval
  is a server-wide default (`config.yml`) with a per-bot override in the settings GUI
- 🔌 **ViaVersion-Aware** - bots register with ViaVersion (if installed) as running the server's own
  native protocol version, so it never treats them as an unrecognized connection
- 🧭 **Real Pathfinding Engine** - Pathetic-backed A* navigation with parkour, block-breaking,
  bridging, stuck detection with a hard give-up budget, pre-flight path verification, and a
  Baritone-style particle debug view (per bot or globally)
- 🗡️ **PVE Combat** - per-bot smart attack with mob-type selection, detect range, target priority,
  weapon-cooldown pacing, and pathfinding-linked chasing; now always active alongside other tasks
- ⛏️ **`/fpp find` Automation** - search → path → mine loops with tool auto-equip, anti-stuck
  watchdogs, and inventory-aware deposits into registered storages
- 🎯 **Precise Clicking** - `/fpp left-click` / `/fpp right-click` aim at the exact point you're
  looking at, walk to a reachable vantage (preferring your standing spot) when out of reach, and
  only trigger a button/lever when actually aimed at its hit box
- 🍗 **Auto-Eat** - per bot: eats from its inventory when hungry (off-hand → hotbar → inventory),
  configurable food list + hunger threshold; off-hand eating runs fully in parallel with everything
  else, main-hand eating still pauses briefly and switches back to the held item afterward
- 🎨 **Rarity Skin Pools** - bots roll their skin from configurable rarity tiers
  (`skins/1-<N>%.txt`), signed once via MineSkin and cached forever; slim/classic model
  auto-detection
- 🆔 **Readable Bot UUIDs** - deterministic `fb07…` UUIDs with the bot number embedded
  (`bot2` → `fb070000-…-000000000002`); zero collision risk with real accounts
- 🏷️ **Live Nametags** - three-row mannequin-style tag with a real-time activity line
  (idle / moving / mining / fighting / eating / searching / sneaking …)
- 🖥️ **GUI Suite** - bot list hub (search, sort, live refresh, spawn & settings shortcuts),
  per-bot settings (general, PVE, pathfinding, skin, auto-eat, rename, danger zone), global settings
  with runtime debug toggles, and a categorized help GUI
- 🫥 **Invisible to Players** - no tab-list entry, no server-list ping count/sample entry, no
  advancements, no join/leave/death chat noise
- ✅ **Folia Support** - region-thread-safe ticking, nametag rendering, and Head-AI on Folia's
  regionised architecture
- 🔐 **LuckPerms-Faithful Permissions** - no hard-coded operator bypasses anywhere; every `fpp.*`
  node (including for server operators) is decided purely by your permissions plugin
- 📊 **Performance Tooling** - `/fpp perf` dashboard, history, benchmark reports, and Spark
  integration; hot per-tick NMS paths call directly instead of through reflection
