# Config Sync

Config Sync pushes or pulls `config.yml` across backend servers via the shared MySQL database. Only active in **NETWORK** mode with the database enabled.

## Modes

| Mode | Description |
|------|-------------|
| `DISABLED` | No syncing (default) |
| `MANUAL` | Sync only triggered by integrations or admin tooling |
| `AUTO_PULL` | Pull the latest config on startup and `/fpp reload` |
| `AUTO_PUSH` | Push config changes automatically |

## Setup

```yaml
database:
  mode: "NETWORK"
  mysql-enabled: true

config-sync:
  mode: "AUTO_PULL"
```

## How It Works

- A config blob is stored in the MySQL database under a shared key
- On `AUTO_PULL`, each backend downloads the latest config on startup/reload
- On `AUTO_PUSH`, any `/fpp reload` that touches config pushes the new version
- The migrator still runs locally after the pull to ensure version compatibility

## Use Cases

- Keep bot limits, badword settings, and formatting consistent across a proxy network
- Update one backend and have the rest auto-pull on next reload

## Notes

- Only the **base config** is synced.
- Backups are still created locally before applying pulled configs.
- Conflicts are not resolved; the latest pushed config wins (last-write-wins).
