# fpp-luckperms - LuckPerms Integration

Integrates FPP bots with LuckPerms group/display data and provides rank commands.

## Requirements

- LuckPerms installed on the server
- `fpp-luckperms.jar` loaded as an FPP extension

## Configuration

File: `plugins/FakePlayerPlugin/extensions/fpp-luckperms/config.yml`

```yaml
enabled: true

default-group: default

permissions:
  lpinfo: fpp.lpinfo
  rank: fpp.rank
```

## Commands

```text
/fpp lpinfo
/fpp rank <bot> <group>
/fpp rank <bot> clear
/fpp rank random <group> [num]
/fpp rank list
```

## Permissions

| Permission | Description |
|------------|-------------|
| `fpp.lpinfo` | Show LuckPerms extension/API status |
| `fpp.rank` | Set, clear, randomize, or list bot groups |

## Notes

- `/fpp lpinfo` takes no bot argument in the current source.
- `/fpp rank random <group> [num]` applies a group to random active bots.
- `/fpp rank list` lists known LuckPerms groups/counts.
- Bot display integration is exposed through the FPP bot display service path.
