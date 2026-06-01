# Proxy Support

FPP supports **Velocity** and **BungeeCord** proxy networks via companion plugins. Works with both Paper/Purpur and Folia backends.

## Overview

The proxy-merged architecture gives you:

- **Shared MySQL database** — all backends read/write to the same `fpp_network_bots` and `fpp_server_heartbeat` tables
- **True cross-server player counts** — `%fpp_network_total%`, `%fpp_network_real%`, `%fpp_network_bots%` show real sums across all backends
- **Remote bot caching** — bots on other servers appear in `/fpp list`, tab list, and placeholders
- **Config sync** — push/pull config.yml across backends via the shared database
- **Proxy-pushed stats** — the proxy companion broadcasts totals to backends every 5 seconds (no player dependency)

## What You Need

| Component | Required | File |
|-----------|----------|------|
| FPP backend plugin | Yes | `fpp.jar` on every backend |
| Velocity companion | Yes (if Velocity) | `fpp-velocity.jar` on proxy |
| BungeeCord companion | Yes (if BungeeCord/Waterfall) | `fpp-bungee.jar` on proxy |
| MySQL database | **Yes** for proxy-merge | Shared across all backends |

## Setup

### 1. MySQL Database

Create a single MySQL database accessible by **all backend servers**:

```sql
CREATE DATABASE fpp_network CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'fpp_user'@'%' IDENTIFIED BY 'secure_password';
GRANT ALL PRIVILEGES ON fpp_network.* TO 'fpp_user'@'%';
FLUSH PRIVILEGES;
```

### 2. Backend Config (Every Server)

```yaml
# plugins/FakePlayerPlugin/config.yml
database:
  enabled: true
  mode: "NETWORK"          # REQUIRED — enables shared tables
  server-id: "survival"    # MUST be unique per backend (e.g., "survival", "skyblock", "lobby")
  mysql-enabled: true
  mysql:
    host: "your-mysql-host"
    port: 3306
    database: "fpp_network"
    username: "fpp_user"
    password: "secure_password"
    use-ssl: false
    pool-size: 5
    connection-timeout: 30000
```

**Critical:**
- `mode` must be `"NETWORK"` (not `"LOCAL"`)
- Each `server-id` must be unique across your network
- `database.enabled` and `mysql-enabled` must both be `true`

### 3. Proxy Companion

- **Velocity:** Place `fpp-velocity.jar` in `velocity/plugins/`
- **BungeeCord/Waterfall:** Place `fpp-bungee.jar` in `plugins/`

No configuration required — companions auto-detect backends.

### 4. Restart

1. Stop all backend servers and the proxy
2. Start the proxy first
3. Start backends one by one
4. Verify: `/fpp reload` on each backend

## How It Works

### Data Flow

```
Backend A                    MySQL DB                    Backend B
   | ──upsert bots──▶  fpp_network_bots  ◀──upsert bots── |
   | ──heartbeat───▶  fpp_server_heartbeat  ◀──heartbeat── |
   |                                                 |
   └───────reads remote bots ◀──────────────────────┘
```

### Proxy Stats Push

```
Proxy (Velocity/Bungee)
   |
   ├─ pings every backend every 5s for player counts
   ├─ computes total_players + total_bots across all backends
   └─ sends NETWORK_STATS to every connected backend ──────▶ All Backends
```

This means even if Backend B has **zero real players**, Backend A still receives updated totals via the proxy connection.

### Shared Tables

| Table | Written By | Read By |
|-------|-----------|---------|
| `fpp_network_bots` | Each backend (every 5s) | All backends for `/fpp list`, tab list |
| `fpp_server_heartbeat` | Each backend (every 5s) | Placeholders, proxy stats |
| `fpp_network_tasks` | (future: command router) | (future: command router) |

## Placeholders (Cross-Server)

All require PlaceholderAPI. These are the network-aware placeholders:

| Placeholder | Description |
|-------------|-------------|
| `%fpp_network_total%` | **Total players + bots across ALL backends** |
| `%fpp_network_real%` | Total real players across all backends |
| `%fpp_network_bots%` | Total bots across all backends |
| `%fpp_count%` | Bots on this server + remote bots (from DB cache) |
| `%fpp_network_count%` | Remote bots from other servers |
| `%fpp_names%` | Names including remote bots |
| `%fpp_network_names%` | Remote bot names only |

## NetworkHeartBeat Manager

The `NetworkHeartbeatManager` runs on every backend in NETWORK mode:

1. **Every 5 seconds:**
   - Writes local bots to `fpp_network_bots`
   - Heartbeats `fpp_server_heartbeat`
   - Reads remote bots from `fpp_network_bots` into `RemoteBotCache`
   - Reads totals from `fpp_server_heartbeat` for PlaceholderAPI

2. **Every 60 seconds:**
   - Prunes stale servers (no heartbeat >60s) from `fpp_server_heartbeat`

## Config Sync

When `database.mode: "NETWORK"` is active, the ConfigSyncManager can push/pull `config.yml`, `bot-names.yml`, and `language/en.yml` across all backends via the shared database.

### Modes

```yaml
config-sync:
  mode: "AUTO_PULL"   # AUTO_PULL / AUTO_PUSH / MANUAL
```

- `AUTO_PULL` — each server automatically pulls the newest config on startup
- `AUTO_PUSH` — this server pushes its config on every reload
- `MANUAL` — use `/fpp reload network` to sync manually

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `%fpp_network_total%` shows local only | Verify `database.mode: "NETWORK"`, unique `server-id`, and MySQL connectivity |
| Remote bots don't show in tab list | Check that proxy companion is installed and all backends restarted |
| Duplicate bot names across servers | Use unique `server-id` values — the network registry is keyed by `server_id + bot_uuid` |
| High MySQL load | Increase `heartbeat-interval` in config (not yet exposed; defaults to 5s) |
| Server offline but still in network totals | Stale entries are pruned after 60 seconds automatically |

## Limitations

- **Bot commands** (`/fpp follow`, `/fpp mine`, etc.) only work on the server where the bot is physically spawned
- **Remote bots are visual only** on other backends (tab list, placeholders, `/fpp list`)
- **Cross-server task routing** is not yet implemented (table exists, feature coming in a future update)

## See Also

- [Database](Database) — MySQL setup details
- [Config Sync](Config-Sync) — Push/pull configs across backends
- [Placeholders](Placeholders) — Full placeholder reference
