# FPP First-Party Extensions Reference

This page documents the current first-party modules in `fpp-extensions/`.

## Build And Packaging

- Source folder: `fpp-extensions/`
- Build system: Gradle multi-project
- Modules: 8 extension projects + `fpp-spoof` bundle
- Outputs: individual jars and `fpp-spoof-1.2.1.jar` copied to `fpp-extensions/builds/`
- Install path: `plugins/FakePlayerPlugin/extensions/`

```powershell
cmd /c "..\\fake-player-plugin\\gradlew.bat -p . build"
```

From the workspace root:
```powershell
cmd /c "fake-player-plugin\\gradlew.bat -p fpp-extensions build"
```

## Classloader Architecture

Each extension JAR gets its own `URLClassLoader`. Only classes from the core plugin (parent classloader) are visible to all extensions. Shared API classes (`BotProfile`, `Personality`, `ProfileService`, etc.) live in the core plugin at `me.bill.fakePlayerPlugin.api.personality`.

## Modules

### fpp-aichat

Provider-backed AI direct messages and public chat reactions for FPP bots with profile integration.

- Command: `/fpp aichat`
- Permission: `fpp.aichat`
- Config: `direct-messages`, `typing-delay`, `public-chat`, AI provider settings
- Resources: AI provider secrets and templates
- Uses `BotProfile` from the shared personality API (core plugin)

### fpp-chat

Bot chat system with event-driven messages, bot-to-bot conversations, and profile-aware behavior.

- Command: `/fpp chat`
- Usage: `[on|off|status|all] | <bot> [on|off|status|info|mute [sec]|say <msg>]`
- Permission: `fpp.chat`
- Config: `fake-chat`, event triggers, bot-to-bot replies, keyword reactions
- Resource: `bot-messages.yml`
- Reads `BotProfile` via `ProfileApi` for personality-driven chat behavior

### fpp-luckperms

LuckPerms integration for bot display and rank management.

- Commands: `/fpp lpinfo`, `/fpp rank`
- `/fpp lpinfo`: no arguments
- `/fpp rank`: `<bot> <group|clear> | random <group> [num] | list`
- Permissions: `fpp.lpinfo`, `fpp.rank`
- Config: `default-group`, command permissions

### fpp-peaks

Peak-hour scheduler for waking/sleeping bot sessions with staggered transitions.

- Command: `/fpp peaks`
- Usage: `[on|off|status|next|force|list|wake [name]|sleep <name>]`
- Permission: `fpp.peaks`
- Config: `peak-hours.enabled`, timezone, schedules, day overrides, stagger/min-online rules
- Note: this is not a TPS or memory performance command

### fpp-personality

Shared bot personality, profiles, and long-lived identity data for FPP extensions. This extension provides the implementation for the core API's `ProfileService` and persists profiles through the FPP API generic extension data methods.

- Command: `/fpp personality`
- Usage: `<debug|reload|list|show <bot>>`
- Permission: `fpp.personality.admin`
- Config: `default.personality`, `default.activity-level`, `default.chat-frequency`, `default.peak-participation`
- Priority: 0 (loads before all other extensions)

### fpp-ping

Bot ping viewing, overrides, randomization, and simulation.

- Command: `/fpp ping`
- Usage: `[<bot>|--count <n>] [--ping <ms>|--random|--reset]`
- Permissions: `fpp.ping`, `fpp.ping.set`, `fpp.ping.random`, `fpp.ping.bulk`
- Config: `random.min/max`, `ping.enabled`, variability, spike settings
- Note: no `--all` flag; omit bot/count to target all bots

### fpp-skin

Bot skin command and spawn skin hook, with profile-driven skin presets and fallback chains.

- Command: `/fpp skin`
- Usage: `<bot> <username|reset|--url <url>>`
- Spawn hook: `/fpp spawn --skin <username|url>` and `/fpp sp --skin <username|url>`
- Permission: `fpp.skin`
- Config: `skin.mode`, `guaranteed-skin`, `overrides`, `pool`, `use-skin-folder`, MineSkin settings
- Reads `BotProfile` skin preferences when a personality profile is available

### fpp-swap

Bot session rotation with leave/rejoin behavior, personality-driven session lengths, and farewell/greeting messages.

- Command: `/fpp swap`
- Usage: `[on|off|status|now <bot>|list|info <bot>]`
- Permission: `fpp.swap`
- Settings tab: `fpp-swap`
- Config: swap enable/debug, max swapped-out, min-online, greetings/farewells, retry delay, session and absence ranges
- Reads `BotProfile` swap group and peak participation settings via the shared personality API

## Bundle Contents

The aggregate `fpp-spoof-1.2.1.jar` embeds jars under `extensions/<module>.jar` for all 8 modules above. You can install the bundle or individual jars depending on which features you want enabled.
