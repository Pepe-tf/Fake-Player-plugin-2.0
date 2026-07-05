# FakePlayerPlugin Wiki

> Advanced NPC / Bot Plugin for Paper/Purpur/Folia 1.21+

Welcome to the FakePlayerPlugin (FPP) wiki. FPP spawns server-side bot entities that behave like
players — useful for **AFK farms, automated tasks, testing, and NPC simulations**. It is **not** a
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

## Latest Version: v2.0.0

**Highlights:**
- 🧭 **Real Pathfinding Engine** — Pathetic-backed A* navigation with parkour, block-breaking,
  bridging, stuck detection with a hard give-up budget, pre-flight path verification, and a
  Baritone-style particle debug view (per bot or globally)
- 🗡️ **PVE Combat** — per-bot smart attack with mob-type selection, detect range, target priority,
  weapon-cooldown pacing, and pathfinding-linked chasing
- ⛏️ **`/fpp find` Automation** — search → path → mine loops with tool auto-equip, anti-stuck
  watchdogs, and inventory-aware deposits into registered storages
- 🎯 **Precise Clicking** — `/fpp left-click` / `/fpp right-click` aim at the exact point you're
  looking at, walk to a reachable vantage (preferring your standing spot) when out of reach, and
  only trigger a button/lever when actually aimed at its hit box
- ⏸️ **One Action at a Time** — tasks are mutually exclusive (no multitasking); interrupts like
  auto-eat pause the current task and resume it afterward
- 🍗 **Auto-Eat** — per bot: eats from its inventory when hungry (off-hand → hotbar → inventory),
  configurable food list + hunger threshold, pauses/resumes the current task and switches back to
  the held item
- 🎨 **Rarity Skin Pools** — bots roll their skin from configurable rarity tiers
  (`skins/1-<N>%.txt`), signed once via MineSkin and cached forever; slim/classic model
  auto-detection
- 🆔 **Readable Bot UUIDs** — deterministic `fb07…` UUIDs with the bot number embedded
  (`bot2` → `fb070000-…-000000000002`); zero collision risk with real accounts
- 🏷️ **Live Nametags** — three-row mannequin-style tag with a real-time activity line
  (idle / moving / mining / fighting / eating / searching / sneaking …)
- 🖥️ **GUI Suite** — bot list hub (search, sort, live refresh, spawn & settings shortcuts),
  per-bot settings (general, PVE, pathfinding, skin, auto-eat, rename, danger zone), global settings
  with runtime debug toggles, and a categorized help GUI
- 🫥 **Invisible to Players** — no tab-list entry, no server-list ping count/sample entry, no
  advancements, no join/leave/death chat noise
- ✅ **Folia Support** — full compatibility with Folia's region-threaded architecture
- 📊 **Performance Tooling** — `/fpp perf` dashboard, history, benchmark reports, and Spark
  integration
