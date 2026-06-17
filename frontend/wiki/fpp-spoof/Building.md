# Building FPP First-Party Extensions

This page covers `fpp-extensions/` only.

## Prerequisites

- Java runtime/toolchain capable of building Java 21 targets
- Core FPP API jar at `fake-player-plugin/build/libs/fake-player-plugin-1.6.6.12.8-all.jar`
- Gradle wrapper from `fake-player-plugin/gradlew.bat`

Build or restore core first if the API jar is missing.

## Build All Extensions

From the fpp-extensions directory:

```powershell
cmd /c "..\\fake-player-plugin\\gradlew.bat -p . build"
```

From the workspace root:

```powershell
cmd /c "fake-player-plugin\\gradlew.bat -p fpp-extensions build"
```

To do a clean build:
```powershell
cmd /c "..\\fake-player-plugin\\gradlew.bat -p . clean build"
```

## Build Output

The Gradle build copies final artifacts directly to `fpp-extensions/builds/`:

```text
fpp-extensions/builds/fpp-aichat.jar
fpp-extensions/builds/fpp-chat.jar
fpp-extensions/builds/fpp-luckperms.jar
fpp-extensions/builds/fpp-peaks.jar
fpp-extensions/builds/fpp-personality.jar
fpp-extensions/builds/fpp-ping.jar
fpp-extensions/builds/fpp-skin.jar
fpp-extensions/builds/fpp-swap.jar
fpp-extensions/builds/fpp-spoof-1.2.1.jar
```

## Current Modules

`settings.gradle.kts` includes (active modules):

```text
fpp-aichat
fpp-chat
fpp-luckperms
fpp-peaks
fpp-personality
fpp-ping
fpp-skin
fpp-swap
fpp-spoof (aggregator bundle)
```

Dead modules removed from source: `fpp-command`, `fpp-groups`, `fpp-list`, `fpp-nametag`, `fpp-waypoints`.

## Build Configuration

The current root `build.gradle.kts` uses:

- Java toolchain 25
- `options.release = 21`
- `compileOnly` Paper API `1.21.11-R0.1-SNAPSHOT`
- `compileOnly` LuckPerms API `5.5`
- `compileOnly` Gson `2.11.0`
- `compileOnly(files("../fake-player-plugin/build/libs/fake-player-plugin-1.6.6.12.8-all.jar"))`
- Unversioned jar names via `archiveVersion.set("")`
- `copyExtension` tasks that copy each module jar to `fpp-extensions/builds/`
- `copySpoof` task that copies `fpp-spoof-1.2.1.jar` to `fpp-extensions/builds/`
- `cleanAll` task that wipes all module build directories and the shared builds folder
- Root `:build` depends on all subproject `:build` tasks for reliable output

## Build One Module

```powershell
cmd /c "..\\fake-player-plugin\\gradlew.bat -p . :fpp-ping:build"
```

The module build also runs its `copyExtension` finalizer and copies the jar to `builds/`.

## Install

Copy either individual jars or `fpp-spoof-1.2.1.jar` from `fpp-extensions/builds/` into:

```text
plugins/FakePlayerPlugin/extensions/
```

Then restart the server or run `/fpp reload extensions`.
