# FPP First-Party Extensions Reference

This page documents the current first-party modules in `fpp-extensions/`.

## Build And Packaging

- Source folder: `fpp-extensions/`
- Build system: Gradle multi-project
- Modules: 13 first-party extension projects
- Outputs: individual jars and `fpp-spoof.jar` copied to workspace `builds/`
- Install path: `plugins/FakePlayerPlugin/extensions/`

```powershell
cmd /c "fake-player-plugin\\gradlew.bat -p fpp-extensions build"
```

## Modules

### fpp-aichat

AI personalities and conversation support.

- Command: `/fpp personality`
- Aliases: `persona`, `aipersonality`
- Usage: `<list|reload|providers> | <bot> <set <name>|reset|show>`
- Permission: `fpp.aichat.personality`
- Config: `personality`, `direct-messages`, `typing-delay`, `public-chat`
- Resources: personality files and AI provider secrets/templates

### fpp-chat

Fake bot chat and event-triggered messaging.

- Command: `/fpp chat`
- Usage: `[on|off|status|all] | <bot> [on|off|status|info|mute [sec]|say <msg>]`
- Permission: `fpp.chat` by default
- Config: `fake-chat`, event triggers, bot-to-bot replies, keyword reactions
- Resource: `bot-messages.yml`

### fpp-command

Runs commands as bots and manages the command bound to bot right-click interaction.

- Command: `/fpp cmd`
- Alias: `command`
- Usage: `<bot> <command...> | <bot> --add <command...> | <bot> --clear | <bot> --show`
- Permission: `fpp.cmd.admin` or legacy `fpp.cmd`

### fpp-groups

Personal bot group storage and group-based task targeting hooks.

- Command: `/fpp groups`
- Aliases: `group`, `botgroups`
- Usage: `[gui|list|create|delete|add|remove]`
- Permission: `fpp.settings` by default
- Storage: `bot-groups.yml`
- Hooks: adds `--group <group>` handling to supported task commands

### fpp-list

Tab-list and server-list handling for bots.

- Commands: none
- Config: `bot-tab-list.enabled`, `sync-interval-ticks`, `server-player-list.count-bots`, `include-remote-bots`
- Behavior: maintains bot scoreboard team `~fpp`, syncs bot tab-list entries, optionally counts local/remote bots in server list ping data

### fpp-luckperms

LuckPerms integration for bot display and rank management.

- Commands: `/fpp lpinfo`, `/fpp rank`
- `/fpp lpinfo`: no arguments
- `/fpp rank`: `<bot> <group|clear> | random <group> [num] | list`
- Permissions: `fpp.lpinfo`, `fpp.rank`
- Config: `default-group`, command permissions

### fpp-nametag

Integration with the external NameTag plugin.

- Commands: none
- Config: `nametag.block-nick-conflicts`, `bot-isolation`, `sync-nick-as-rename`, `refresh-display-names`
- Behavior: exposes `FppNameTagService`, protects against nick conflicts, can preserve NameTag skin data, and can optionally sync nicknames into FPP renames

### fpp-pathfinder

Extension-owned pathfinding service and settings pages.

- Commands: none
- Settings tabs: global `fpp-pathfinder`, per-bot `fpp-pathfinder-bot`
- Config: parkour, break/place toggles, arrival distances, recalc/stuck tuning, range and node limits

### fpp-peaks

Peak-hour scheduler for waking/sleeping bot sessions.

- Command: `/fpp peaks`
- Usage: `[on|off|status|next|force|list|wake [name]|sleep <name>]`
- Permission: `fpp.peaks`
- Config: `peak-hours.enabled`, timezone, schedules, day overrides, stagger/min-online rules
- Note: this is not a TPS or memory performance command

### fpp-ping

Bot ping viewing, overrides, randomization, and simulation.

- Command: `/fpp ping`
- Usage: `[<bot>|--count <n>] [--ping <ms>|--random|--reset]`
- Permissions: `fpp.ping`, `fpp.ping.set`, `fpp.ping.random`, `fpp.ping.bulk`
- Config: `random.min/max`, `ping.enabled`, variability, spike settings
- Note: no `--all` flag; omit bot/count to target all bots

### fpp-skin

Bot skin command and spawn skin hook.

- Command: `/fpp skin`
- Usage: `<bot> <username|reset|--url <url>>`
- Spawn hook: `/fpp spawn --skin <username|url>` and `/fpp sp --skin <username|url>`
- Permission: `fpp.skin`
- Config: `skin.mode`, `guaranteed-skin`, `overrides`, `pool`, `use-skin-folder`, MineSkin settings

### fpp-swap

Bot session rotation with leave/rejoin behavior.

- Command: `/fpp swap`
- Usage: `[on|off|status|now <bot>|list|info <bot>]`
- Permission: `fpp.swap`
- Settings tab: `fpp-swap`
- Config: swap enable/debug, max swapped-out, min-online, greetings/farewells, retry delay, session and absence ranges

### fpp-waypoints

Named route positions and bot patrols.

- Command: `/fpp waypoint`
- Alias: `wp`
- Usage: `add <route> | create <route> | remove <route> <index> | delete <route> | clear <route> | list [route] | patrol <bot|all> <route> [--random] | stop <bot|all>`
- Permission: `fpp.waypoint`
- Config: patrol arrival distance, random reshuffle, core waypoint migration

## Bundle Contents

The aggregate bundle embeds jars under `extensions/<module>.jar` for every module listed above. You can install the bundle or individual jars depending on which features you want enabled.
