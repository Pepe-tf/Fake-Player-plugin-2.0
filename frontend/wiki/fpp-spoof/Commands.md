# FPP First-Party Extension Commands

All commands are registered as `/fpp` addon commands by first-party modules in `fpp-extensions/`.

| Extension | Command | Aliases | Usage | Permission |
|-----------|---------|---------|-------|------------|
| `fpp-aichat` | `personality` | `persona`, `aipersonality` | `<list\|reload\|providers> \| <bot> <set <name>\|reset\|show>` | `fpp.aichat.personality` |
| `fpp-chat` | `chat` | none | `[on\|off\|status\|all] \| <bot> [on\|off\|status\|info\|mute [sec]\|say <msg>]` | `fpp.chat` by default |
| `fpp-command` | `cmd` | `command` | `<bot> <command...> \| <bot> --add <command...> \| <bot> --clear \| <bot> --show` | `fpp.cmd.admin` or legacy `fpp.cmd` |
| `fpp-groups` | `groups` | `group`, `botgroups` | `[gui\|list\|create\|delete\|add\|remove]` | `fpp.settings` by default |
| `fpp-luckperms` | `lpinfo` | none | no arguments | `fpp.lpinfo` |
| `fpp-luckperms` | `rank` | none | `<bot> <group\|clear> \| random <group> [num] \| list` | `fpp.rank` |
| `fpp-peaks` | `peaks` | none | `[on\|off\|status\|next\|force\|list\|wake [name]\|sleep <name>]` | `fpp.peaks` |
| `fpp-ping` | `ping` | none | `[<bot>\|--count <n>] [--ping <ms>\|--random\|--reset]` | `fpp.ping` plus action permissions |
| `fpp-skin` | `skin` | none | `<bot> <username\|reset\|--url <url>>` | `fpp.skin` |
| `fpp-swap` | `swap` | none | `[on\|off\|status\|now <bot>\|list\|info <bot>]` | `fpp.swap` |
| `fpp-waypoints` | `waypoint` | `wp` | `add <route> \| create <route> \| remove <route> <index> \| delete <route> \| clear <route> \| list [route] \| patrol <bot\|all> <route> [--random] \| stop <bot\|all>` | `fpp.waypoint` |

## Command Hooks

- `fpp-skin` extends `/fpp spawn` and `/fpp sp` with `--skin <username|url>`.
- `fpp-groups` adds `--group <group>` hooks to supported task commands such as `move`, `attack`, `follow`, `sleep`, `stop`, and older task command hooks when those commands exist.
- `fpp-list`, `fpp-nametag`, and `fpp-pathfinder` do not add `/fpp` subcommands; they work through services, listeners, or settings tabs.

## Examples

```text
/fpp personality Bot1 set friendly
/fpp chat Bot1 say Hello everyone!
/fpp cmd Bot1 --add say I was right-clicked
/fpp groups create guards
/fpp groups add guards Bot1
/fpp rank Bot1 vip
/fpp peaks status
/fpp ping Bot1 --random
/fpp ping --count 5 --ping 80
/fpp skin Bot1 Notch
/fpp spawn --name GuardBot --skin Notch
/fpp swap now Bot1
/fpp wp add patrol-a
/fpp wp patrol Bot1 patrol-a --random
```

## Notes

- `/fpp ping` has no `--all` flag. Omitting a bot/count targets all active bots for bulk operations.
- `/fpp skin` supports direct username, direct URL value, `--url <url>`, and `reset`; it does not implement `--all`, `--random`, or `--clear` command flags.
- `/fpp lpinfo` is a no-argument diagnostic command for the LuckPerms extension.
- `fpp-peaks` is a peak-hour scheduler, not a TPS/memory stats command.
