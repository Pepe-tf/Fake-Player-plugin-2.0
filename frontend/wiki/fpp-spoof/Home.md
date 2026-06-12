# FPP First-Party Extensions Wiki

Official documentation for the first-party `fpp-extensions` modules.

## Quick Links

- [Getting Started](Getting-Started) - Installation and setup guide
- [Extensions](Extensions) - Current module reference
- [Commands](Commands) - First-party extension commands
- [Permissions](Permissions) - Permission nodes from extension configs/source
- [Configuration](Configuration) - Extension config locations and highlights
- [Changelog](Changelog) - Version history and updates

## Current Build

- Build system: Gradle multi-project under `fpp-extensions/`
- Java: toolchain 25, release target 21
- Output: individual module jars plus `fpp-spoof.jar` copied to workspace `builds/`
- Runtime install path: `plugins/FakePlayerPlugin/extensions/`

## Modules

| Extension | Description |
|-----------|-------------|
| [fpp-aichat](Extensions#fpp-aichat) | AI personalities, direct messages, and optional public AI replies |
| [fpp-chat](Extensions#fpp-chat) | Fake bot chat, event-triggered messages, bot-to-bot replies |
| [fpp-command](Extensions#fpp-command) | Execute commands as bots and bind one command to right-click |
| [fpp-groups](Extensions#fpp-groups) | Personal bot groups and `--group` task targeting hooks |
| [fpp-list](Extensions#fpp-list) | Bot tab-list team and server-list bot count/sample handling |
| [fpp-luckperms](Extensions#fpp-luckperms) | LuckPerms display and bot rank commands |
| [fpp-nametag](Extensions#fpp-nametag) | External NameTag plugin integration |
| [fpp-pathfinder](Extensions#fpp-pathfinder) | Extension pathfinding service and settings tabs |
| [fpp-peaks](Extensions#fpp-peaks) | Peak-hour bot scheduling |
| [fpp-ping](Extensions#fpp-ping) | Bot ping viewing, overrides, randomization, and simulation |
| [fpp-skin](Extensions#fpp-skin) | Skin command and spawn `--skin` hook |
| [fpp-swap](Extensions#fpp-swap) | Bot session rotation with leave/rejoin behavior |
| [fpp-waypoints](Extensions#fpp-waypoints) | Waypoint routes and patrols |

## Requirements

- FakePlayerPlugin `1.6.6.12.7` compatible API
- Paper/Purpur/Folia 1.21+
- Java 21 runtime
- `fake-player-plugin/build/fpp.jar` available when building from source

## Support

- Source: https://github.com/Pepe-tf/fake-player-plugin
- Discord: https://discord.gg/RfjEJDG2TM
- Marketplace: https://mp.fpp.wtf/resources/
