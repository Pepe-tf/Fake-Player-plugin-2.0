# FakePlayerPlugin — Agent Notes

## Build
- **Tool:** Gradle. JDK 21 required (enforced by `gradle-daemon-jvm.properties`).
- **Commands:** `./gradlew clean shadowJar` (build), `./gradlew test` (no-op, no tests).
- **Output:** `build/libs/fake-player-plugin-<version>-all.jar`.
- **CI:** GitHub Actions runs `./gradlew test` then `./gradlew shadowJar`.

## Architecture
- **Single-module Gradle project** (Paper/Folia Minecraft plugin).
- **Main:** `me.bill.fakePlayerPlugin.FakePlayerPlugin` (`plugin.yml`).
- **NMS:** Uses Mojang-mapped classes via `paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")`.
- **FastStats:** Binary JARs under `src/main/resources/faststats/`, loaded via `URLClassLoader` at runtime. Do not text-filter or relocate.
- **Shaded deps:** None by default (shadowJar enabled but no shading configured). `sqlite-jdbc` / `mysql-connector-j` not present in `build.gradle.kts`.
- **Provided deps:** `luckperms`, `placeholderapi`, `worldguard` (compileOnly).

## Key Resources
- `src/main/resources/plugin.yml` — Bukkit descriptor (commands, permissions).
- `src/main/resources/velocity-plugin.json` — Velocity proxy descriptor.
- `src/main/resources/config.yml` — Main config (auto-migrates to version 73).
- `src/main/resources/language/en.yml` — MiniMessage format.
- `src/main/resources/bot-names.yml`, `bad-words.yml` — Name/badword lists.

## Companion Modules
- `velocity-companion/` and `bungee-companion/` are **gitignored**, separate Gradle projects.
- Built independently; not part of root build.

## Constraints
- Paper API targets `1.21` (supports up to `1.21.11`).
- Folia-compatible (regionised threading).
- Config auto-migration runs on enable; do not edit `config-version` manually.

## Docs
- User docs: `frontend/wiki/` (not built by Gradle).
- Repo: `https://github.com/Pepe-tf/fake-player-plugin`
