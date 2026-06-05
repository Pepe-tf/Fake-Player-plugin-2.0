# Building FPP First-Party Extensions

This page covers `fpp-extensions/` only.

## Prerequisites

- Java runtime/toolchain capable of building Java 21 targets
- Core FPP API jar at `fake-player-plugin/build/fpp.jar`
- Gradle wrapper from `fake-player-plugin/gradlew.bat`

Build or restore core first if `fake-player-plugin/build/fpp.jar` is missing.

## Build All Extensions

From the workspace root:

```powershell
cmd /c "fake-player-plugin\\gradlew.bat -p fpp-extensions build"
```

## Build Output

The Gradle build copies final artifacts directly to workspace `builds/`:

```text
builds/fpp-aichat.jar
builds/fpp-chat.jar
builds/fpp-command.jar
builds/fpp-groups.jar
builds/fpp-list.jar
builds/fpp-luckperms.jar
builds/fpp-nametag.jar
builds/fpp-pathfinder.jar
builds/fpp-peaks.jar
builds/fpp-ping.jar
builds/fpp-skin.jar
builds/fpp-swap.jar
builds/fpp-waypoints.jar
builds/fpp-extensions-bundle.jar
```

Do not use `fake-player-plugin/build/extensions/` as the final extension output location.

## Current Modules

`settings.gradle.kts` includes:

```text
fpp-aichat
fpp-chat
fpp-command
fpp-groups
fpp-list
fpp-luckperms
fpp-nametag
fpp-pathfinder
fpp-peaks
fpp-ping
fpp-skin
fpp-swap
fpp-waypoints
```

## Build Configuration

The current root `build.gradle.kts` uses:

- Java toolchain 25
- `options.release = 21`
- `compileOnly` Paper API `1.21.8-R0.1-SNAPSHOT`
- `compileOnly` LuckPerms API `5.5`
- `compileOnly` Gson `2.11.0`
- `compileOnly(files("../fake-player-plugin/build/fpp.jar"))`
- Unversioned jar names via `archiveVersion.set("")`
- `copyExtension` tasks that copy each module jar to `../builds`
- `bundleExtensions` that embeds all module jars under `extensions/<module>.jar`

## Build One Module

```powershell
cmd /c "fake-player-plugin\\gradlew.bat -p fpp-extensions :fpp-ping:build"
```

The module build also runs its `copyExtension` finalizer and copies the jar to `builds/`.

## Install

Copy either individual jars or `fpp-extensions-bundle.jar` from `builds/` into:

```text
plugins/FakePlayerPlugin/extensions/
```

Then restart the server or run `/fpp reload extensions`.
