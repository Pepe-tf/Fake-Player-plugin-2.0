# fpp-luckperms - LuckPerms Integration

Full LuckPerms permission management for FPP bots.

## Overview

fpp-luckperms integrates FPP bots with the LuckPerms permission plugin. Assign ranks, manage groups, and control permissions for your bots just like you would for real players. Bots inherit permissions from their assigned LuckPerms group.

## How It Works

When a bot spawns, the extension ensures it has a LuckPerms user entry with the configured default group. When you change a bot's group via `/fpp rank`, the LuckPerms API updates the user's primary group and recalculates permissions. The extension subscribes to `UserDataRecalculateEvent` to refresh cached data automatically.

## Requirements

- **LuckPerms plugin** installed on the server (tested with LuckPerms 5.5+)
- LuckPerms API dependency included in the extension

## Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-luckperms/config.yml`

```yaml
enabled: true
default-group: default

permissions:
  lpinfo: fpp.lpinfo
  rank: fpp.rank
```

## Commands

### Info Command

```
/fpp lpinfo <bot>           # Show bot's LuckPerms info
/fpp lpinfo <bot> --groups  # Show all groups the bot is in
/fpp lpinfo <bot> --perms   # Show effective permissions
```

### Rank Command

```
/fpp rank <bot> <group>                  # Set bot's group
/fpp rank random <group> [count]         # Assign group to random bots
/fpp rank list                           # List all groups and counts
/fpp rank list <group>                   # List bots in a specific group
/fpp rank <bot> --reset                  # Reset to default group
/fpp rank clear <group>                  # Remove group from all bots
```

## Key Features

- **Auto-Assign Group**: New bots automatically get the configured default group
- **Rank Management**: View and change bot ranks with simple commands
- **Batch Operations**: Assign ranks to random bots for variety
- **Permission Inheritance**: Bots inherit all permissions from their group
- **Cache Refresh**: Automatically refreshes on LuckPerms data changes
- **Group Listing**: See how many bots are in each group
- **Prefix/Suffix Resolution**: Reads group display settings

## Permission Model

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.lpinfo` | View bot LuckPerms info | op |
| `fpp.rank` | Change bot ranks | op |

## Example Setups

### Roleplay Server

```
/fpp rank KnightBot knight
/fpp rank MageBot mage
/fpp rank VillagerBot villager
```

Each bot inherits different permissions based on their role.

### Automated Variety

Assign random bots to different groups:
```
/fpp rank random default 5
/fpp rank random member 3
/fpp rank random vip 1
```

### Permission Groups

```
# Create LuckPerms groups
lp creategroup guards
lp group guards permission set fpp.combat true
lp group guards permission set fpp.protect true

# Assign bots
/fpp rank GuardBot1 guards
/fpp rank GuardBot2 guards
```

## Use Cases

- **Ranked Bots**: Different bot tiers with varying permissions
- **Permission Control**: Granular control over what bots can do
- **Group-Based Behavior**: Integrate with other extensions that check groups
- **Visual Differentiation**: Groups control prefix/suffix via nametag
- **Staff Bots**: Moderator bots with elevated permissions

## Architecture

```
FppLuckPermsExtension (main)
├── /fpp lpinfo — Display bot permissions
├── /fpp rank  — Manage bot groups
└── LuckPermsHelper — API wrapper
    ├── setGroup(user, group)
    ├── getGroup(user)
    ├── getPrefix(user) / getSuffix(user)
    ├── listGroups()
    └── ensureBeforeSpawn(user, group)
```

## Troubleshooting

### Bot Not Getting Permissions

- Verify LuckPerms is installed and loaded
- Check `default-group` exists in LuckPerms
- Ensure `enabled: true` in config
- Verify player has `fpp.rank` permission

### Group Not Found

- Check group name is case-sensitive
- Verify group exists in LuckPerms: `lp listgroups`
- Avoid spaces in group names

### Permission Not Applying

- Run `lp sync` to reload LuckPerms
- Check for group inheritance issues
- Verify no conflicting permission plugins

## Technical Details

- **Priority**: 100 (default)
- **Dependency**: LuckPerms API 5.5 (compileOnly)
- **API Integration**: Full LuckPerms API for group/permission management
- **Events**: Subscribes to `UserDataRecalculateEvent` for cache refresh
- **Backward Compatibility**: Deprecated methods maintained for older integrations
