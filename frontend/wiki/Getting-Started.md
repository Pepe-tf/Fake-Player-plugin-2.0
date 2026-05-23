# Getting Started

FPP spawns server-side bot entities that behave like players — useful for **AFK farms, automated tasks, testing, and NPC simulations**. It is **not** a fake-online-count or player-spoofing tool.

## Requirements

- **Server:** Paper/Purpur 1.21+ (up to `1.21.11`)
- **Java:** JDK 21+
- **RAM:** 2GB+ recommended for optimal performance
- **Optional:** PlaceholderAPI, LuckPerms, WorldGuard, WorldEdit

## Installation

1. Download `fpp.jar` from [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp)) or build from source.
2. Drop the JAR into your server's `plugins/` folder.
3. Restart the server.
4. The plugin creates `plugins/FakePlayerPlugin/` with:
   - `config.yml` — main configuration
   - `language/en.yml` — messages and translations
   - `bot-names.yml` — name pool
   - `bad-words.yml` — profanity filter word list
   - `data/` — SQLite database and persistence files
   - `extensions/` — third-party extension JARs
5. Configure permissions (see [Permissions](Permissions)).
6. Run `/fpp reload` to apply most changes without restarting.

## Building from Source

Requires JDK 21 and Gradle.

```bash
./gradlew clean shadowJar
```

- Output: `build/libs/fake-player-plugin-<version>-all.jar`
- Optional profiles: `-Pbuild-velocity-companion`, `-Pbuild-bungee-companion`

The build depends on the **paperweight** Paper dev bundle (`paper-1.21.11-R0.1-SNAPSHOT`). Gradle downloads this automatically via the Paperweight plugin.

## First Steps

1. Grant yourself admin access: `/lp user <you> permission set fpp.admin true`
2. Spawn a bot: `/fpp spawn`
3. Open its settings: shift+right-click the bot entity
4. Teleport it to you: `/fpp tph <bot>`
5. Make it follow you: `/fpp follow <bot> <player>`
