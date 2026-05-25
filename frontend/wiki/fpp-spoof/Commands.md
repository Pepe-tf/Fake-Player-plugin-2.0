# FPP Extensions Commands

Complete command reference for all FPP Extensions.

## Table of Contents

- [fpp-command Commands](#fpp-command-commands)
- [fpp-groups Commands](#fpp-groups-commands)
- [fpp-peaks Commands](#fpp-peaks-commands)
- [fpp-ping Commands](#fpp-ping-commands)
- [fpp-skin Commands](#fpp-skin-commands)
- [fpp-waypoints Commands](#fpp-waypoints-commands)

---

## fpp-command Commands

Execute commands as bots.

### Syntax

```
/fpp cmd <bot> <command>             # Execute command as bot
/fpp cmd <bot> --clear               # Clear assigned command
```

### Parameters

| Parameter | Description | Required |
|-----------|-------------|----------|
| `<bot>` | Bot name | Yes |
| `<command>` | Command to execute | Yes |
| `--clear` | Clear assigned command | No |

### Examples

```
# Execute a say command as Bot1
/fpp cmd Bot1 say Hello everyone!

# Make Bot2 teleport
/fpp cmd Bot2 tp @p

# Clear Bot1's assigned command
/fpp cmd Bot1 --clear
```

### Permissions

- `fpp.command.execute` - Required to execute commands

---

## fpp-groups Commands

Manage bot groups.

### Syntax

```
/fpp group create <name>             # Create a new group
/fpp group delete <name>             # Delete a group
/fpp group list                      # List all groups
/fpp group add <group> <bot>         # Add bot to group
/fpp group remove <group> <bot>      # Remove bot from group
/fpp group members <group>           # List group members
```

### Parameters

| Parameter | Description | Required |
|-----------|-------------|----------|
| `<name>` | Group name | Yes |
| `<group>` | Existing group name | Yes |
| `<bot>` | Bot name | Yes |

### Examples

```
# Create a new group called "guards"
/fpp group create guards

# Add Bot1 to the guards group
/fpp group add guards Bot1

# List all members of guards group
/fpp group members guards

# Remove Bot1 from guards
/fpp group remove guards Bot1

# Delete the guards group
/fpp group delete guards

# List all groups
/fpp group list
```

### Permissions

- `fpp.groups.create` - Create groups
- `fpp.groups.delete` - Delete groups
- `fpp.groups.modify` - Modify group membership
- `fpp.groups` - General groups access

---

## fpp-peaks Commands

View server performance statistics.

### Syntax

```
/fpp peaks              # Show all performance stats
/fpp peaks --tps        # Show only TPS
/fpp peaks --memory     # Show only memory usage
```

### Flags

| Flag | Description |
|------|-------------|
| `--tps` | Display only TPS information |
| `--memory` | Display only memory usage |

### Examples

```
# View all performance statistics
/fpp peaks

# View only server TPS
/fpp peaks --tps

# View only memory usage
/fpp peaks --memory
```

### Output

The command displays:
- Current TPS (ticks per second)
- Memory usage (used/allocated/max)
- Online player count
- Uptime

### Permissions

- `fpp.peaks` - View performance stats

---

## fpp-ping Commands

View and modify bot ping values.

### Single Bot Commands

```
/fpp ping <bot>                        # Show bot ping
/fpp ping <bot> --ping <ms>            # Set bot ping
/fpp ping <bot> --random               # Random ping (20-500ms)
/fpp ping <bot> --reset                # Reset to default
```

### Bulk Commands (All Bots)

```
/fpp ping --all                        # Show all bot pings
/fpp ping --all --ping <ms>            # Set ping for all bots
/fpp ping --all --random               # Random ping for all bots
/fpp ping --all --reset                # Reset all bot pings
```

### Count-Based Commands

```
/fpp ping --count <n>                  # Show n random bots
/fpp ping --count <n> --ping <ms>      # Set ping for n bots
```

### Parameters

| Parameter | Description | Required |
|-----------|-------------|----------|
| `<bot>` | Bot name | Yes (for single bot commands) |
| `<ms>` | Ping value in milliseconds | Yes (when using --ping) |
| `<n>` | Number of bots | Yes (for count commands) |

### Flags

| Flag | Description |
|------|-------------|
| `--ping <ms>` | Set ping to specific value |
| `--random` | Set random ping (20-500ms) |
| `--reset` | Reset to default ping |
| `--all` | Apply to all bots |
| `--count <n>` | Apply to n random bots |

### Examples

```
# View Bot1's current ping
/fpp ping Bot1

# Set Bot1's ping to 100ms
/fpp ping Bot1 --ping 100

# Set Bot1's ping to random value
/fpp ping Bot1 --random

# Reset Bot1's ping to default
/fpp ping Bot1 --reset

# View all bot pings
/fpp ping --all

# Set all bots to 50ms ping
/fpp ping --all --ping 50

# Set random ping for all bots
/fpp ping --all --random

# Reset all bot pings
/fpp ping --all --reset

# View ping for 5 random bots
/fpp ping --count 5

# Set 10 random bots to 75ms ping
/fpp ping --count 10 --ping 75
```

### Permissions

- `fpp.ping` - View ping values (default: true)
- `fpp.ping.set` - Set ping values (default: op)
- `fpp.ping.random` - Use random ping (default: op)
- `fpp.ping.bulk` - Use bulk operations (default: op)

---

## fpp-skin Commands

View and modify bot skins.

### Single Bot Commands

```
/fpp skin <bot>                        # Show bot skin info
/fpp skin <bot> --skin <url>           # Set custom skin
/fpp skin <bot> --random               # Random skin
/fpp skin <bot> --reset                # Reset to default
```

### Bulk Commands (All Bots)

```
/fpp skin --all                        # Show all bot skins
/fpp skin --all --skin <url>           # Set skin for all bots
/fpp skin --all --random               # Random skins for all bots
/fpp skin --all --reset                # Reset all bot skins
```

### Parameters

| Parameter | Description | Required |
|-----------|-------------|----------|
| `<bot>` | Bot name | Yes (for single bot commands) |
| `<url>` | Skin texture URL | Yes (when using --skin) |

### Flags

| Flag | Description |
|------|-------------|
| `--skin <url>` | Set skin from URL |
| `--random` | Set random skin |
| `--reset` | Reset to default skin |
| `--all` | Apply to all bots |

### Examples

```
# View Bot1's current skin
/fpp skin Bot1

# Set Bot1's skin from URL
/fpp skin Bot1 --skin https://example.com/skin.png

# Set Bot1's skin to random
/fpp skin Bot1 --random

# Reset Bot1's skin to default
/fpp skin Bot1 --reset

# View all bot skins
/fpp skin --all

# Set all bots to same skin
/fpp skin --all --skin https://example.com/skin.png

# Set random skins for all bots
/fpp skin --all --random

# Reset all bot skins
/fpp skin --all --reset
```

### Permissions

- `fpp.skin` - View skin information (default: true)
- `fpp.skin.set` - Set skin URLs (default: op)
- `fpp.skin.random` - Use random skins (default: op)
- `fpp.skin.bulk` - Use bulk operations (default: op)

### Notes

- Skin source (MCHead or NameMC) is configured in `fpp-skin/config.yml`
- Custom URLs must point to valid PNG skin files
- Random skins are fetched from the configured skin source

---

## fpp-waypoints Commands

Manage bot waypoints and patrol routes.

### Waypoint Management

```
/fpp waypoint create <name>            # Create waypoint at current location
/fpp waypoint delete <name>            # Delete waypoint
/fpp waypoint list                     # List all waypoints
/fpp waypoint goto <bot> <name>        # Send bot to waypoint
```

### Patrol Management

```
/fpp waypoint patrol <bot> add <name>      # Add waypoint to patrol
/fpp waypoint patrol <bot> remove <name>   # Remove waypoint from patrol
/fpp waypoint patrol <bot> clear           # Clear patrol route
/fpp waypoint patrol <bot> start           # Start patrol
/fpp waypoint patrol <bot> stop            # Stop patrol
/fpp waypoint patrol <bot> list            # List patrol waypoints
```

### Parameters

| Parameter | Description | Required |
|-----------|-------------|----------|
| `<name>` | Waypoint name | Yes |
| `<bot>` | Bot name | Yes |

### Examples

```
# Create a waypoint at your location
/fpp waypoint create spawn1

# List all waypoints
/fpp waypoint list

# Send Bot1 to spawn1 waypoint
/fpp waypoint goto Bot1 spawn1

# Delete spawn1 waypoint
/fpp waypoint delete spawn1

# Add spawn1 to Bot1's patrol route
/fpp waypoint patrol Bot1 add spawn1

# Add spawn2 to Bot1's patrol route
/fpp waypoint patrol Bot1 add spawn2

# List Bot1's patrol waypoints
/fpp waypoint patrol Bot1 list

# Start Bot1's patrol
/fpp waypoint patrol Bot1 start

# Stop Bot1's patrol
/fpp waypoint patrol Bot1 stop

# Clear Bot1's patrol route
/fpp waypoint patrol Bot1 clear
```

### Permissions

- `fpp.waypoints` - General waypoint access (default: op)
- `fpp.waypoints.create` - Create waypoints (default: op)
- `fpp.waypoints.delete` - Delete waypoints (default: op)
- `fpp.waypoints.patrol` - Manage patrols (default: op)

### Notes

- Waypoints are stored per-world
- Patrol routes loop by default (configurable)
- Bots will pathfind to waypoints using FPP's navigation system

---

## Command Aliases

All commands support the following aliases:

- `/fpp` can be replaced with `/fakeplayer`
- Extension commands may have additional aliases defined in their config

## Tab Completion

All commands support tab completion for:
- Bot names
- Waypoint names
- Group names
- Command flags

## Help Command

View available commands:

```
/fpp help              # Show general help
/fpp help <command>    # Show help for specific command
```
