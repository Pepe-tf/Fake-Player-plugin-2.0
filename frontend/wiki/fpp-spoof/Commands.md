# FPP First-Party Extension Commands

All commands are registered as `/fpp` addon commands by first-party modules in `fpp-extensions/`.

| Extension | Command | Aliases | Usage | Permission |
|-----------|---------|---------|-------|------------|
| `fpp-aichat` | `aichat` | none | (config-driven; no direct command) | `fpp.aichat` |
| `fpp-chat` | `chat` | none | `[on\|off\|status\|all] \| <bot> [on\|off\|status\|info\|mute [sec]\|say <msg>]` | `fpp.chat` |
| `fpp-luckperms` | `lpinfo` | none | no arguments | `fpp.lpinfo` |
| `fpp-luckperms` | `rank` | none | `<bot> <group\|clear> \| random <group> [num] \| list` | `fpp.rank` |
| `fpp-peaks` | `peaks` | none | `[on\|off\|status\|next\|force\|list\|wake [name]\|sleep <name>]` | `fpp.peaks` |
| `fpp-personality` | `personality` | none | `<debug\|reload\|list\|show <bot>>` | `fpp.personality.admin` |
| `fpp-ping` | `ping` | none | `[<bot>\|--count <n>] [--ping <ms>\|--random\|--reset]` | `fpp.ping` plus action permissions |
| `fpp-skin` | `skin` | none | `<bot> <username\|reset\|--url <url>>` | `fpp.skin` |
| `fpp-swap` | `swap` | none | `[on\|off\|status\|now <bot>\|list\|info <bot>]` | `fpp.swap` |

## Command Hooks

- `fpp-skin` extends `/fpp spawn` and `/fpp sp` with `--skin <username|url>`.

## Examples

```text
/fpp personality debug
/fpp personality list
/fpp personality show Bot1
/fpp chat Bot1 say Hello everyone!
/fpp rank Bot1 vip
/fpp peaks status
/fpp ping Bot1 --random
/fpp ping --count 5 --ping 80
/fpp skin Bot1 Notch
/fpp spawn --name GuardBot --skin Notch
/fpp swap now Bot1
```

## Notes

- `/fpp ping` has no `--all` flag. Omitting a bot/count targets all active bots for bulk operations.
- `/fpp skin` supports direct username, direct URL value, `--url <url>`, and `reset`; it does not implement `--all`, `--random`, or `--clear` command flags.
- `/fpp lpinfo` is a no-argument diagnostic command for the LuckPerms extension.
- `fpp-peaks` is a peak-hour scheduler, not a TPS/memory stats command.
