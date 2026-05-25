# fpp-groups - Groups Extension

Personal bot groups and grouped task dispatch for FPP bots.

## Overview

fpp-groups lets you organize your bots into named groups for bulk operations and task management. Create groups of bots that work together — guards, workers, entertainers — and dispatch commands to entire groups at once.

## How It Works

Groups are stored per-owner (by UUID) in `bot-groups.yml`. Each group is a named collection of bot names. The extension integrates with FPP's task system, allowing task extensions (move, mine, attack, follow, etc.) to target groups using the `--group` flag.

## Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-groups/config.yml`

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

### Group Management

```
/fpp group create <name>                    # Create a new group
/fpp group delete <name>                    # Delete a group
/fpp group list                             # List all your groups
/fpp group add <group> <bot>                # Add a bot to a group
/fpp group remove <group> <bot>             # Remove a bot from a group
/fpp group members <group>                  # List members of a group
```

### GUI Management

The extension also provides a graphical inventory interface for managing groups. Open it with:

```
/fpp groups
```

**Aliases:** `group`, `botgroups`

### Group Task Dispatch

Groups integrate with task extensions. Any task command that supports `--group` can target all bots in a group:

```
/fpp move --group <name> <location>       # Move all bots in group
/fpp mine --group <name> <block>          # All bots mine target
/fpp attack --group <name> <target>       # All bots attack target
/fpp follow --group <name> <player>       # All bots follow player
/fpp stop --group <name>                  # Stop all bots in group
```

**Supported task extensions:**

| Task | Flag | Description |
|------|------|-------------|
| `move` | `--group` | Move all group bots to location |
| `mine` | `--group` | All group bots mine |
| `find` | `--group` | Group bots search |
| `place` | `--group` | Group bots place blocks |
| `use` | `--group` | Group bots use items |
| `attack` | `--group` | Group bots attack |
| `follow` | `--group` | Group bots follow |
| `sleep` | `--group` | Group bots sleep |
| `stop` | `--group` | Stop group bots |
| `storage` | `--group` | Group bots access storage |

## Key Features

- **Per-Player Groups**: Groups are private to each player
- **GUI Management**: Visual inventory interface
- **Task Integration**: Dispatch commands to entire groups
- **Default Group**: Resolves to all owned bots when no group specified
- **Persistence**: Groups saved to `bot-groups.yml`
- **Migration**: Imports existing core groups on first load
- **Bulk Operations**: Manage multiple bots as a single unit

## Permission Model

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.groups` | Access group management | op |

Groups are owned per-player. Players can only manage their own groups.

## Use Cases

- **Guard Squads**: Group of combat bots that defend an area
- **Worker Teams**: Bots that mine, build, or farm together
- **Entertainment Groups**: Bots that perform coordinated dances or shows
- **Area Patrols**: Group of bots patrolling different sections
- **Army Management**: Large-scale bot coordination

## Data Storage

Groups are stored in:

```
plugins/FakePlayerPlugin/extensions/fpp-groups/bot-groups.yml
```

Format:
```yaml
groups:
  <owner-uuid>:
    guards:
      - "GuardBot1"
      - "GuardBot2"
    workers:
      - "MinerBot1"
      - "MinerBot2"
      - "FarmerBot1"
```

## Examples

### Creating a Guard Squad

```
/fpp group create guards
/fpp group add guards GuardBot1
/fpp group add guards GuardBot2
/fpp group add guards GuardBot3

# All guards follow a player
/fpp follow --group guards Player1

# All guards attack a target
/fpp attack --group guards Zombie
```

### Managing Workers

```
/fpp group create miners
/fpp group add miners MinerBot1
/fpp group add miners MinerBot2

/fpp group create farmers
/fpp group add farmers FarmerBot1

# Send miners to work
/fpp move --group miners 100 50 200
/fpp mine --group miners diamond_ore
```

### Using the GUI

1. Run `/fpp groups` (or `/fpp group`)
2. Click on a group to manage it
3. Add/remove bots visually
4. Click to dispatch tasks

## Migration

On first load, the extension checks for existing core groups and imports them automatically (configurable via `migration.import-core-groups`).

## Troubleshooting

### Group Not Found

- Verify group name is spelled correctly
- Groups are per-player — other players can't see your groups
- Use `/fpp group list` to see your groups

### Bot Not in Group

- Check group membership with `/fpp group members <group>`
- Bot must be owned by you
- Bot must be online and loaded

### Task Not Working with Group

- Task must support `--group` flag
- All bots in group must be able to perform the task
- Check individual bot permissions

## Technical Details

- **Priority**: 100 (default)
- **Data File**: `bot-groups.yml`
- **Default Group**: All owned bots
- **Core Migration**: Imports from core group data
