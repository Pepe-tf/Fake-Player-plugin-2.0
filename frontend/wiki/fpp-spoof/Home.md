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
- Output: individual module jars plus `fpp-spoof-1.2.1.jar` copied to `fpp-extensions/builds/`
- Runtime install path: `plugins/FakePlayerPlugin/extensions/`

## Modules

| Extension | Description |
|-----------|-------------|
| [fpp-aichat](Extensions#fpp-aichat) | Provider-backed AI direct messages and public chat reactions |
| [fpp-chat](Extensions#fpp-chat) | Bot chat with event-driven messages, bot-to-bot conversations |
| [fpp-luckperms](Extensions#fpp-luckperms) | LuckPerms display and bot rank commands |
| [fpp-peaks](Extensions#fpp-peaks) | Peak-hour bot scheduling with staggered transitions |
| [fpp-personality](Extensions#fpp-personality) | Shared bot personality, profiles, and identity data |
| [fpp-ping](Extensions#fpp-ping) | Bot ping viewing, overrides, randomization, and simulation |
| [fpp-skin](Extensions#fpp-skin) | Skin command and spawn `--skin` hook |
| [fpp-swap](Extensions#fpp-swap) | Bot session rotation with leave/rejoin behavior |

## Requirements

- FakePlayerPlugin `1.6.6.12.8` compatible API
- Paper/Purpur/Folia 1.21+
- Java 21 runtime
- `fake-player-plugin/build/libs/fake-player-plugin-1.6.6.12.8-all.jar` available when building from source

## Support

- Source: https://github.com/Pepe-tf/fake-player-plugin
- Discord: https://discord.gg/RfjEJDG2TM
- Marketplace: https://mp.fpp.wtf/resources/
