# FPP First-Party Extension Permissions

These permissions come from the current `fpp-extensions` source and extension configs.

| Extension | Permission | Description | Default Source |
|-----------|------------|-------------|----------------|
| `fpp-aichat` | `fpp.aichat` | AI chat features | Config `permissions.command` |
| `fpp-chat` | `fpp.chat` | Use `/fpp chat` | Config `permissions.command` |
| `fpp-luckperms` | `fpp.lpinfo` | Use `/fpp lpinfo` | Config `permissions.lpinfo` |
| `fpp-luckperms` | `fpp.rank` | Use `/fpp rank` | Config `permissions.rank` |
| `fpp-peaks` | `fpp.peaks` | Use `/fpp peaks` | Config `permissions.command` |
| `fpp-personality` | `fpp.personality.admin` | Use `/fpp personality` | Hard-coded |
| `fpp-ping` | `fpp.ping` | View/use base ping command | Config `permissions.base` |
| `fpp-ping` | `fpp.ping.set` | Set explicit ping values | Config `permissions.set` |
| `fpp-ping` | `fpp.ping.random` | Apply random ping values | Config `permissions.random` |
| `fpp-ping` | `fpp.ping.bulk` | Target multiple bots with ping operations | Config `permissions.bulk` |
| `fpp-skin` | `fpp.skin` | Use `/fpp skin` and spawn `--skin` hook | Config `permissions.command` |
| `fpp-swap` | `fpp.swap` | Use `/fpp swap` | Config `permissions.command` |

## LuckPerms Examples

```text
/lp group admin permission set fpp.personality.admin true
/lp group admin permission set fpp.rank true
/lp group moderator permission set fpp.ping true
/lp group moderator permission set fpp.ping.set true
/lp group builder permission set fpp.swap true
```

## Notes

- Extension permissions are not guaranteed to follow `fpp.<extension>.*` wildcard patterns.
- Use the exact nodes above unless you have changed the relevant extension `config.yml` permission value.
