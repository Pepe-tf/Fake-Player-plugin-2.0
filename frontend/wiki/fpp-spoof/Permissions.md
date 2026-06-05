# FPP First-Party Extension Permissions

These permissions come from the current `fpp-extensions` source and extension configs.

| Extension | Permission | Description | Default Source |
|-----------|------------|-------------|----------------|
| `fpp-aichat` | `fpp.aichat.personality` | Use `/fpp personality` | Hard-coded command permission |
| `fpp-chat` | `fpp.chat` | Use `/fpp chat` | `permissions.command` |
| `fpp-command` | `fpp.cmd.admin` | Use `/fpp cmd` | `permissions.command` |
| `fpp-command` | `fpp.cmd` | Legacy access for `/fpp cmd` | `permissions.legacy` |
| `fpp-groups` | `fpp.settings` | Use `/fpp groups` and group command extensions | `permissions.command` |
| `fpp-luckperms` | `fpp.lpinfo` | Use `/fpp lpinfo` | `permissions.lpinfo` |
| `fpp-luckperms` | `fpp.rank` | Use `/fpp rank` | `permissions.rank` |
| `fpp-peaks` | `fpp.peaks` | Use `/fpp peaks` | `permissions.command` |
| `fpp-ping` | `fpp.ping` | View/use base ping command | `permissions.base` |
| `fpp-ping` | `fpp.ping.set` | Set explicit ping values | `permissions.set` |
| `fpp-ping` | `fpp.ping.random` | Apply random ping values | `permissions.random` |
| `fpp-ping` | `fpp.ping.bulk` | Target multiple bots with ping operations | `permissions.bulk` |
| `fpp-skin` | `fpp.skin` | Use `/fpp skin` and spawn `--skin` hook | `permissions.command` |
| `fpp-swap` | `fpp.swap` | Use `/fpp swap` | `permissions.command` |
| `fpp-waypoints` | `fpp.waypoint` | Use `/fpp waypoint` and `/fpp wp` | Hard-coded command permission |

## No Command Permissions

These extensions do not register addon commands in the current source:

- `fpp-list`
- `fpp-nametag`
- `fpp-pathfinder`

## LuckPerms Examples

```text
/lp group admin permission set fpp.cmd.admin true
/lp group admin permission set fpp.rank true
/lp group moderator permission set fpp.ping true
/lp group moderator permission set fpp.ping.set true
/lp group builder permission set fpp.waypoint true
```

## Notes

- Extension permissions are not guaranteed to follow `fpp.<extension>.*` wildcard patterns.
- Use the exact nodes above unless you have changed the relevant extension `config.yml` permission value.
