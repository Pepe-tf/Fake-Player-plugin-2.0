# FakePlayerPlugin (FPP)

[![License: MIT](https://img.shields.io/badge/License-MIT-green?style=flat-square)](LICENSE)
[![Paper](https://img.shields.io/badge/Paper-1.21%2B-blue?style=flat-square)](https://papermc.io)
[![Folia](https://img.shields.io/badge/Folia-supported-blueviolet?style=flat-square)](https://papermc.io/software/folia)

> **Advanced NPC / Bot plugin for Paper, Purpur, and Folia 1.21+**

FPP spawns server-side bot entities that behave like real players — useful for **AFK farms,
automated tasks, testing, and NPC simulations**. It is deliberately **not** a fake-online-count or
player-spoofing tool: bots are hidden from the tab list and server-list ping, always wear a
mandatory "ʙᴏᴛ ʙʏ {owner}" nametag, earn no advancements, and can never take a real account's
identity.

---

## Features

- 🧭 **Real pathfinding engine** — [Pathetic](https://github.com/bsommerfeld/pathetic)-backed A*
  navigation with parkour, block-breaking, bridging, stuck detection with a hard give-up budget,
  pre-flight path verification, and a Baritone-style particle debug view
- 🗡️ **PVE combat** — per-bot smart attack with mob-type selection, detect range, target priority,
  real weapon-cooldown pacing, and pathfinding-linked chasing
- ⛏️ **Find automation** — `/fpp find` search → path → mine loops with tool auto-equip, anti-stuck
  watchdogs, and inventory-aware deposits into registered storages
- 🎨 **Rarity skin pools** — bots roll their skin from configurable rarity tiers
  (`skins/1-<N>%.txt` files of NameMC URLs), signed once via MineSkin and cached forever, with
  slim/classic model auto-detection
- 🆔 **Readable bot UUIDs** — deterministic `fb07…` UUIDs with the bot number embedded
  (`bot2` → `fb070000-…-000000000002`); zero collision risk with real accounts
- 🏷️ **Live nametags** — three-row mannequin-style tag with a real-time activity line
  (idle / moving / mining / fighting / searching / sneaking …)
- 🖥️ **Full GUI suite** — bot list hub (search, sort, live refresh, spawn & settings shortcuts),
  per-bot settings, global settings with runtime debug toggles, and a categorized help GUI
- 🫥 **Invisible to players** — no tab-list entry, no server-list count/sample entry, no
  advancements, no join/leave/death chat noise
- 💾 **Persistence** — bots, inventories, XP, and tasks survive restarts (SQLite or MySQL)
- 🌐 **Proxy networks** — Velocity/BungeeCord support with shared MySQL, cross-server placeholders,
  and config sync
- ✅ **Folia support** — full compatibility with Folia's region-threaded architecture
- 📊 **Performance tooling** — `/fpp perf` dashboard, history, benchmark reports, Spark integration

## Quick Start

1. Drop `fake-player-plugin-<version>-all.jar` into `plugins/` and restart.
2. Grant yourself access: `/lp user <you> permission set fpp.admin true`
3. `/fpp spawn` — spawns one auto-named bot (`bot`, `bot2`, …) at your location.
4. `/fpp spawn --name Miner` — spawns one custom-named bot.
5. `/fpp list` — the GUI hub: click a bot to manage it, spawn more, or open global settings.

Full documentation lives in the [wiki](frontend/wiki/Home.md): commands, permissions,
configuration, placeholders, database/proxy setup, and FAQ.

## Building from Source

Requires **JDK 21+**. The paperweight dev bundle downloads automatically.

```bash
./gradlew clean shadowJar
# → build/libs/fake-player-plugin-<version>-all.jar
```

Run the test suite with `./gradlew test`.

## Deliberately Removed Capabilities

Several capabilities were **intentionally removed** from this codebase and should not be
reintroduced — pull requests restoring them will be declined. See
[CONTRIBUTING.md](CONTRIBUTING.md) for the full policy and rationale:

- **Player spoofing / impersonation** — anything that lets a bot pose as a specific real player
  (real-account UUIDs, premium skin/identity lookups by player name, removing the mandatory bot
  nametag, showing bots in the tab list or server-list ping)
- **Bot chat** — bots are fully silent in chat by design
- **The runtime extension/addon loader** — external code cannot be loaded into FPP; integrate from
  a normal Bukkit plugin via the fired `FppBot*` events instead
- **License/DRM checks** — the plugin is open source and runs unconditionally

## Links

- **Discord:** https://discord.gg/RfjEJDG2TM
- **Modrinth:** https://modrinth.com/plugin/fake-player-plugin-(fpp)
- **Wiki:** [frontend/wiki/Home.md](frontend/wiki/Home.md)

## License

MIT — see [LICENSE](LICENSE).
