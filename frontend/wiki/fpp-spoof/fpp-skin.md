# fpp-skin - Skin Extension

Set bot skins by Minecraft username or direct skin URL and optionally apply skins during spawn.

## Configuration

File: `plugins/FakePlayerPlugin/extensions/fpp-skin/config.yml`

```yaml
enabled: true

permissions:
  command: fpp.skin

skin:
  mode: player
  guaranteed-skin: true
  clear-cache-on-reload: true
  overrides: {}
  pool: []
  use-skin-folder: true
  mineskin:
    url-upload-enabled: true
    api-key: ""
    visibility: public
```

## Commands

```text
/fpp skin <bot> <username>
/fpp skin <bot> <url>
/fpp skin <bot> --url <url>
/fpp skin <bot> reset
```

## Spawn Hook

```text
/fpp spawn --name GuardBot --skin Notch
/fpp spawn --skin https://example.com/skin.png
/fpp sp --skin Notch
```

## Permissions

| Permission | Description |
|------------|-------------|
| `fpp.skin` | Use `/fpp skin` and spawn `--skin` |

## Notes

- The current command does not implement `--all`, `--random`, or `--clear` flags.
- URL skins require MineSkin URL upload support when a raw PNG URL must be converted to signed texture data.
- Core stores resolved texture/signature data so custom URL skins can survive restarts when `FPP-Skin` is loaded.
