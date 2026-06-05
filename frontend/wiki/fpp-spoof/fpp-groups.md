# fpp-groups - Groups Extension

Personal bot groups and grouped task targeting for FPP bots.

## Configuration

File: `plugins/FakePlayerPlugin/extensions/fpp-groups/config.yml`

```yaml
enabled: true

migration:
  import-core-groups: true

permissions:
  command: fpp.settings

messages:
  prefix: "&8[&bFPP Groups&8]&r "
```

## Commands

`/fpp groups` aliases: `/fpp group`, `/fpp botgroups`

```text
/fpp groups gui
/fpp groups list
/fpp groups create <name>
/fpp groups delete <name>
/fpp groups add <group> <bot>
/fpp groups remove <group> <bot>
```

## Group Task Hooks

The extension registers `--group <group>` hooks for supported task commands. Current source hooks include `move`, `mine`, `find`, `place`, `use`, `attack`, `follow`, `sleep`, `stop`, and `storage`; only hooks for commands present on your installed FPP build can run.

## Permissions

| Permission | Description |
|------------|-------------|
| `fpp.settings` | Use group commands by default |

## Storage

Groups are stored in:

```text
plugins/FakePlayerPlugin/extensions/fpp-groups/bot-groups.yml
```

Groups are owner-scoped, so players manage their own group sets.

## Example

```text
/fpp groups create guards
/fpp groups add guards GuardBot1
/fpp groups add guards GuardBot2
/fpp groups list
/fpp attack --group guards --hunt zombie
```
