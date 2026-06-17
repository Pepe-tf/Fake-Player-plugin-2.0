# FakePlayerPlugin Wiki

> Advanced NPC / Bot Plugin for Paper/Purpur/Folia 1.21+

Welcome to the FakePlayerPlugin (FPP) wiki. FPP spawns server-side bot entities that behave like players — useful for **AFK farms, automated tasks, testing, and NPC simulations**. It is **not** a fake-online-count or player-spoofing tool.

This documentation covers the **base plugin only** — features that ship in the core `fpp.jar`.

Some advanced subsystems are implemented as extensions and are not part of the base plugin. Those are noted where applicable. You can find official extensions on the [FPP Marketplace](https://mp.fpp.wtf/resources/).

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
| [Placeholders](Placeholders) | PlaceholderAPI integration reference (80+ placeholders including cross-server totals) |
| [Database](Database) | SQLite / MySQL setup, network tables, and proxy-merged architecture |
| [Proxy Support](Proxy-Support) | Velocity / BungeeCord multi-server networks with shared MySQL |
| [Config Sync](Config-Sync) | Synchronize configs across proxy backends |
| [Extensions](Extensions) | Extension API for third-party developers |

## Reference

| Page | Description |
|------|-------------|
| [FAQ](FAQ) | Common questions and troubleshooting |
| [Changelog](Changelog) | Version history and release notes |

---

## Quick Links

- **Source:** https://github.com/Pepe-tf/fake-player-plugin
- **Discord:** https://discord.gg/RfjEJDG2TM
- **Modrinth:** https://modrinth.com/plugin/fake-player-plugin-(fpp)
- **Marketplace:** https://mp.fpp.wtf/resources/
- **License:** MIT

---

## Latest Version: v1.6.6.12.8

**Highlights:**
- 🚪 **nLogin Compatibility** — `NmsPlayerSpawner` suppresses nLogin `PlayerJoinEvent` listeners for fake players, preventing auth plugins from kicking/despawning bots during spawn
- 🚪 **Synthetic Quit on Kick** — `FakePlayerKickListener` now marks kicked bots as synthetic quits before despawning, ensuring consistent despawn/quit-event handling
- ✅ **System Check** — `/fpp check` runs targeted or full health checks for commands, listeners, NMS, database, Folia, world, config, extensions, and memory
- 🖱️ **Left/Right Click Automation** — `/fpp left-click` and `/fpp right-click` replace older mine/use style automation with once, repeat, hold, and stop modes
- 👁️ **Sneak Command** — `/fpp sneak` toggles or sets the sneaking state for a live bot body
- ✅ **Debug GUI** — Toggle every debug category at runtime from `/fpp settings`
- 💬 **Debug Chat** — Broadcast debug output to OP/notify players in-game
- ✅ **Folia Support** — Full compatibility with Folia's region-threaded architecture
- 🖱️ **Click Commands** — Unified left-click/right-click automation system
- 🔇 **Silent License** — License verification runs silently without debug spam
- 📊 **80+ Placeholders** — Extensive PlaceholderAPI integration
- 🔒 **Extension Ownership** — Advanced movement, follow, sleep, and rich combat are now extension-owned responsibilities
