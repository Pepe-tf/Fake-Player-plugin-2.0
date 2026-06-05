# Getting Started With FPP First-Party Extensions

This guide covers installing the current first-party modules from `fpp-extensions/`.

## Requirements

- FakePlayerPlugin installed and running
- Java 21 runtime
- Paper/Purpur/Folia 1.21+
- Optional: LuckPerms for `fpp-luckperms`
- Optional: NameTag plugin for `fpp-nametag`

## Build From Source

From the workspace root, make sure `fake-player-plugin/build/fpp.jar` exists, then run:

```powershell
cmd /c "fake-player-plugin\\gradlew.bat -p fpp-extensions build"
```

The build writes individual jars and `fpp-extensions-bundle.jar` to workspace `builds/`.

## Install

Copy either the bundle or only the individual jars you need into:

```text
plugins/FakePlayerPlugin/extensions/
```

Then restart the server or run:

```text
/fpp reload extensions
```

## Verify

```text
/fpp extension --list
```

You should see the loaded first-party extensions in the extension list.

## First Tests

```text
/fpp ping
/fpp skin <bot> Notch
/fpp chat status
/fpp personality list
/fpp wp list
```

Only commands from installed extension jars will be available.

## Config Files

Each extension creates its own data folder:

```text
plugins/FakePlayerPlugin/extensions/fpp-ping/config.yml
plugins/FakePlayerPlugin/extensions/fpp-skin/config.yml
plugins/FakePlayerPlugin/extensions/fpp-chat/config.yml
```

See [Configuration](Configuration) for the current config highlights.

## Next Steps

- [Commands](Commands) - Current command syntax
- [Permissions](Permissions) - Current permission nodes
- [Extensions](Extensions) - Module reference
- [Building](Building) - Build details and outputs
