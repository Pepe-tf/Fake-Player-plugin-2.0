# FPP First-Party Extensions FAQ

## Are These Official Extensions?

Yes. This section documents first-party modules from `fpp-extensions/`.

## What Should I Install?

Install `fpp-spoof-1.2.1.jar` if you want every first-party module, or install individual jars if you only need specific features.

## Where Do I Put Extension Jars?

```text
plugins/FakePlayerPlugin/extensions/
```

Restart the server (or run `/fpp reload`) after adding jars.

## How Do I Check What Loaded?

```text
/fpp extension --list
```

## Where Are Configs?

```text
plugins/FakePlayerPlugin/extensions/<extension-name>/config.yml
```

Each module owns its own config.

## Why Are Extensions Reporting "Requires 'FPP-Personality' Which Is Not Loaded"?

Your core plugin must include the shared personality API classes and the spoof jar must contain `fpp-personality.jar`. Rebuild both the core plugin and the extensions, then deploy both. The personality API lives at `me.bill.fakePlayerPlugin.api.personality.*` in the core plugin.

## Does `/fpp ping --all` Exist?

No. Current `fpp-ping` targets all active bots when you omit both `<bot>` and `--count`.

## Does `/fpp skin --all` Or `/fpp skin --random` Exist?

No. Current `fpp-skin` supports `<bot> <username>`, `<bot> <url>`, `<bot> --url <url>`, `<bot> reset`, and spawn `--skin <username|url>`.

## Is `/fpp peaks` A Performance Command?

No. `fpp-peaks` is a peak-hour bot scheduler that wakes/sleeps bots according to configured schedules.

## Why Is `/fpp lpinfo <bot>` Rejected?

Current `/fpp lpinfo` takes no arguments. Use `/fpp rank <bot> <group|clear>` for bot group changes.

## How Do I Build The Extensions?

From `fpp-extensions/`:

```powershell
cmd /c "..\\fake-player-plugin\\gradlew.bat -p . build"
```

The output jars are copied to `fpp-extensions/builds/`.
