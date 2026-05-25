# FPP Extensions Reference

Complete reference for all FPP Extensions included in the fpp-spoof pack.

## Table of Contents

- [fpp-aichat](#fpp-aichat) - AI-powered chat for bots
- [fpp-chat](#fpp-chat) - Bot chat with cooldowns
- [fpp-command](#fpp-command) - Execute commands as bots
- [fpp-groups](#fpp-groups) - Bot group management
- [fpp-list](#fpp-list) - Advanced tab list control
- [fpp-luckperms](#fpp-luckperms) - LuckPerms integration
- [fpp-nametag](#fpp-nametag) - Custom nametags
- [fpp-peaks](#fpp-peaks) - Server performance monitoring
- [fpp-ping](#fpp-ping) - Ping spoofing
- [fpp-skin](#fpp-skin) - Skin management
- [fpp-waypoints](#fpp-waypoints) - Waypoint system

---

## fpp-aichat

AI-powered chat for bots using LLM APIs.

### Features

- Connect to LLM APIs (OpenAI, Anthropic, etc.)
- Custom AI personalities for bots
- Context-aware conversations
- Configurable response behavior

### Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-aichat/config.yml`

```yaml
enabled: true
api-provider: "openai"  # or "anthropic", "local"
api-key: "your-api-key"
model: "gpt-3.5-turbo"
personality: "friendly"
response-delay-ms: 1000
max-context-length: 10
```

### Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.aichat` | Use AI chat features | op |
| `fpp.aichat.configure` | Configure AI settings | op |

### Usage

AI chat is automatic once configured. Bots will respond to nearby player chat based on their personality settings.

---

## fpp-chat

Bot chat system with cooldowns and anti-spam features.

### Features

- Configurable chat cooldowns
- Random delay variation
- Anti-spam protection
- Chat radius control
- Custom chat prefixes

### Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-chat/config.yml`

```yaml
enabled: true
chat-cooldown-ms: 3000
chat-random-delay-ms: 2000
chat-radius: 50
chat-prefix: ""
allow-chat-without-ai: true
```

### Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.chat` | Enable bot chat | op |

### Usage

Chat behavior is automatic. Configure cooldowns and delays in the config file to control chat frequency.

---

## fpp-command

Execute commands as bots.

### Features

- Run any command as a bot
- Right-click command assignment
- Command cooldowns
- Permission-based execution

### Commands

```
/fpp cmd <bot> <command>     # Execute command as bot
/fpp cmd <bot> --clear       # Clear assigned command
```

### Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-command/config.yml`

```yaml
enabled: true
default-command: ""
command-cooldown-ms: 5000
require-permission: true
```

### Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.command` | Use command extension | op |
| `fpp.command.execute` | Execute commands as bots | op |

### Usage Examples

```
# Execute a command as a bot
/fpp cmd Bot1 say Hello everyone!

# Clear assigned command
/fpp cmd Bot1 --clear
```

---

## fpp-groups

Bot group management system.

### Features

- Create and manage bot groups
- Group-based permissions
- Bulk operations on groups
- Group inheritance

### Commands

```
/fpp group create <name>              # Create a new group
/fpp group delete <name>              # Delete a group
/fpp group list                       # List all groups
/fpp group add <group> <bot>          # Add bot to group
/fpp group remove <group> <bot>       # Remove bot from group
/fpp group members <group>            # List group members
```

### Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-groups/config.yml`

```yaml
enabled: true
max-groups: 10
max-members-per-group: 50
allow-nested-groups: false
```

### Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.groups` | Use group management | op |
| `fpp.groups.create` | Create groups | op |
| `fpp.groups.delete` | Delete groups | op |
| `fpp.groups.modify` | Modify group membership | op |

---

## fpp-list

Advanced player list (tab list) control for bots.

### Features

- Custom tab list entries
- Header/footer customization
- Player list ordering
- Priority-based display

### Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-list/config.yml`

```yaml
enabled: true
bot-tab-list.enabled: false  # Keep false for LuckPerms compatibility
custom-header: "&6My Server"
custom-footer: "&7Welcome!"
sort-mode: "group_then_alphabetical"  # or "alphabetical", "group", "none"
```

### Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.list` | Use tab list features | op |

### Important Notes

- Set `bot-tab-list.enabled: false` if using LuckPerms to avoid conflicts with group ordering
- Custom headers/footers apply to all players on the server

---

## fpp-luckperms

LuckPerms integration for bots.

### Features

- Assign LuckPerms groups to bots
- Bot permission inheritance
- Group-based bot behavior
- Context support

### Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-luckperms/config.yml`

```yaml
enabled: true
auto-assign-group: "default"
sync-permissions: true
use-contexts: false
contexts:
  world: "world"
```

### Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.luckperms` | Use LuckPerms integration | op |
| `fpp.luckperms.group` | Assign groups to bots | op |

### Usage

Bots automatically inherit permissions from their assigned LuckPerms group. Use the groups extension or LuckPerms commands to manage bot groups.

---

## fpp-nametag

Custom nametag display for bots.

### Features

- Custom nametag formatting
- Prefix/suffix support
- Dynamic nametags
- Visibility control

### Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-nametag/config.yml`

```yaml
enabled: true
format: "{prefix}{name}{suffix}"
default-prefix: ""
default-suffix: ""
use-luckperms: true
```

### Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.nametag` | Use nametag features | op |
| `fpp.nametag.custom` | Set custom nametags | op |

### Usage

Nametags are applied automatically based on configuration. Use LuckPerms integration for dynamic prefix/suffix management.

---

## fpp-peaks

Server performance monitoring and display.

### Features

- TPS (ticks per second) monitoring
- Memory usage display
- Player count tracking
- Performance statistics

### Commands

```
/fpp peaks              # Show server performance stats
/fpp peaks --tps        # Show only TPS
/fpp peaks --memory     # Show only memory usage
```

### Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-peaks/config.yml`

```yaml
enabled: true
update-interval-seconds: 5
show-in-action-bar: false
show-in-chat: true
```

### Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.peaks` | View performance stats | op |

### Usage Examples

```
# View all performance stats
/fpp peaks

# View only TPS
/fpp peaks --tps
```

---

## fpp-ping

Show or spoof bot ping values.

### Features

- View bot ping values
- Set custom ping values
- Random ping spoofing
- Bulk operations
- Ping reset functionality

### Commands

```
# Single bot operations
/fpp ping <bot>                    # Show bot ping
/fpp ping <bot> --ping <ms>        # Set bot ping
/fpp ping <bot> --random           # Random ping (20-500ms)
/fpp ping <bot> --reset            # Reset to default

# Bulk operations
/fpp ping --all                    # Show all bot pings
/fpp ping --all --ping <ms>        # Set ping for all bots
/fpp ping --all --random           # Random ping for all bots
/fpp ping --all --reset            # Reset all bot pings

# Count-based operations
/fpp ping --count <n>              # Show n random bots
/fpp ping --count <n> --ping <ms>  # Set ping for n bots
```

### Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-ping/config.yml`

```yaml
enabled: true
default-ping: 0
min-random-ping: 20
max-random-ping: 500
```

### Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.ping` | View ping values | true |
| `fpp.ping.set` | Set ping values | op |
| `fpp.ping.random` | Use random ping | op |
| `fpp.ping.bulk` | Use bulk operations | op |

### Usage Examples

```
# View a bot's ping
/fpp ping Bot1

# Set ping to 100ms
/fpp ping Bot1 --ping 100

# Set random ping
/fpp ping Bot1 --random

# Reset to default
/fpp ping Bot1 --reset

# Set all bots to 50ms
/fpp ping --all --ping 50

# Random ping for all bots
/fpp ping --all --random
```

---

## fpp-skin

Manage bot skins from MCHead or NameMC.

### Features

- Fetch skins from MCHead or NameMC
- Custom skin URLs
- Random skin assignment
- Bulk skin operations
- Skin reset functionality

### Commands

```
# Single bot operations
/fpp skin <bot>                    # Show bot skin info
/fpp skin <bot> --skin <url>       # Set custom skin
/fpp skin <bot> --random           # Random skin
/fpp skin <bot> --reset            # Reset to default

# Bulk operations
/fpp skin --all                    # Show all bot skins
/fpp skin --all --skin <url>       # Set skin for all bots
/fpp skin --all --random           # Random skins for all bots
/fpp skin --all --reset            # Reset all bot skins
```

### Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-skin/config.yml`

```yaml
enabled: true
skin-source: "mchead"  # or "namemc"
default-skin: ""
allow-custom-urls: true
```

### Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.skin` | View skin information | true |
| `fpp.skin.set` | Set skin URLs | op |
| `fpp.skin.random` | Use random skins | op |
| `fpp.skin.bulk` | Use bulk operations | op |

### Usage Examples

```
# View bot's skin
/fpp skin Bot1

# Set custom skin from URL
/fpp skin Bot1 --skin https://example.com/skin.png

# Set random skin
/fpp skin Bot1 --random

# Reset to default
/fpp skin Bot1 --reset

# Set all bots to same skin
/fpp skin --all --skin https://example.com/skin.png

# Random skins for all bots
/fpp skin --all --random
```

---

## fpp-waypoints

Bot waypoint and pathfinding system.

### Features

- Create and manage waypoints
- Custom patrol routes
- Pathfinding configuration
- Waypoint behaviors

### Commands

```
/fpp waypoint create <name>        # Create waypoint at current location
/fpp waypoint delete <name>        # Delete waypoint
/fpp waypoint list                 # List all waypoints
/fpp waypoint goto <bot> <name>    # Send bot to waypoint
/fpp waypoint patrol <bot> add <name>   # Add waypoint to patrol
/fpp waypoint patrol <bot> clear   # Clear patrol route
/fpp waypoint patrol <bot> start   # Start patrol
/fpp waypoint patrol <bot> stop    # Stop patrol
```

### Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-waypoints/config.yml`

```yaml
enabled: true
max-waypoints: 100
patrol-loop: true
patrol-speed: 1.0
avoid-water: false
avoid-lava: false
break-blocks: false
place-blocks: false
```

### Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.waypoints` | Use waypoint system | op |
| `fpp.waypoints.create` | Create waypoints | op |
| `fpp.waypoints.delete` | Delete waypoints | op |
| `fpp.waypoints.patrol` | Manage patrols | op |

### Usage Examples

```
# Create a waypoint
/fpp waypoint spawn1

# Send bot to waypoint
/fpp waypoint goto Bot1 spawn1

# Create patrol route
/fpp waypoint patrol Bot1 add spawn1
/fpp waypoint patrol Bot1 add spawn2
/fpp waypoint patrol Bot1 start

# Stop patrol
/fpp waypoint patrol Bot1 stop
```

---

## Excluded Extensions

The following extensions are **not** included in fpp-spoof:

| Extension | Reason |
|-----------|--------|
| fpp-pathfinder | Functionality moved to base FPP plugin |
| fpp-swap | Incompatible with FPP 1.6.6.12.1 API |
