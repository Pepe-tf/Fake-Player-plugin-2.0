# fpp-list - Player List Extension

Tab list team management and server player-list integration for FPP bots.

## Overview

fpp-list manages how bots appear in the player tab list and on the server list ping. It ensures bots are properly grouped, collidable, and counted as players in the server's online player display.

## How It Works

The extension consists of two main systems:

1. **BotTabTeam**: Manages a scoreboard team (`~fpp`) for all bots, controlling tab list ordering, collision rules, and team color
2. **ServerPlayerListListener**: Intercepts the server list ping to include bots in the player count and sample

## Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-list/config.yml`

```yaml
enabled: true

bot-tab-list:
  enabled: true
  sync-interval-ticks: 40

server-player-list:
  enabled: true
  count-bots: true
  include-remote-bots: false
```

## Key Features

### Bot Tab List Team

- **Scoreboard Team**: All bots are added to a hidden `~fpp` team
- **Collision Control**: Prevents bots from pushing other entities (uses FPP's `body-pushable` config)
- **Incremental Sync**: Bot entries are synced to all player scoreboards at configurable intervals
- **Team Prefix/Suffix**: Maintains compatibility with other scoreboard plugins

### Server Player List

- **Bot Counting**: Bots are counted in the online player count shown in the server list
- **Sample Entries**: Bot display names appear in the sample player list (up to 12 shuffled entries)
- **NameTag Compatibility**: Respects NameTag plugin display names for bot samples
- **Remote Bot Support**: Optionally include remote (proxy) bots in count
- **Server Properties Respect**: Respects `hide-online-players` setting

## Configuration Options

### bot-tab-list

| Setting | Default | Description |
|---------|---------|-------------|
| `enabled` | `true` | Enable tab list team management |
| `sync-interval-ticks` | `40` | How often to sync bot entries (2 seconds) |

### server-player-list

| Setting | Default | Description |
|---------|---------|-------------|
| `enabled` | `true` | Enable server list modification |
| `count-bots` | `true` | Include bots in player count |
| `include-remote-bots` | `false` | Include proxy-managed bots |

## Permission Model

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.list` | Access list extension features | op |

## Use Cases

- **Populated Server List**: Makes servers appear more active to potential joiners
- **Organized Tab List**: Bots are grouped consistently rather than scattered
- **No Pushing**: Prevents bots from interfering with player movement
- **Realistic Display**: Bots appear as real players in server listings

## Visual Examples

### Server List (Multiplayer Screen)

Without fpp-list:
```
Online: 3/20
Player1, Player2, Player3
Bot1 (not listed)
```

With fpp-list (count-bots: true):
```
Online: 8/20
Player1, Player2, Player3, Bot1, Bot2, Bot3, Bot4, Bot5
```

### Tab List (In-Game)

Bots are placed in the `~fpp` team, which can be:
- Ordered before or after other teams
- Given specific colors or prefixes
- Set to no-collision mode

## Compatibility

### LuckPerms

If using LuckPerms for tab sorting, it's recommended to disable the bot tab list team:

```yaml
bot-tab-list:
  enabled: false
```

This prevents conflicts between the two systems' team management.

### NameTag Plugin

fpp-list respects NameTag plugin's display name changes when building server list samples.

## Troubleshooting

### Bots Not in Tab List

- Verify `bot-tab-list.enabled: true`
- Check `sync-interval-ticks` is not too high
- Ensure no conflicting scoreboard plugins

### Wrong Player Count in Server List

- Check `server-player-list.count-bots: true`
- Verify `server-player-list.enabled: true`
- Test with different `include-remote-bots` setting

### Conflict with Other Plugins

- Disable `bot-tab-list.enabled` if using another tab management plugin
- Disable `server-player-list.enabled` if another plugin handles server list

## Technical Details

- **Priority**: 100 (default)
- **Team Name**: `~fpp` (hidden from most scoreboard displays)
- **Sync Method**: Incremental — only changed entries are updated
- **Sample Limit**: Maximum 12 entries in server list sample
- **Events Handled**: `PaperServerListPingEvent`
