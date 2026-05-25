# fpp-waypoints - Waypoints Extension

Named waypoint route storage and patrol system for FPP bots.

## Overview

fpp-waypoints lets you create named waypoints and patrol routes for your bots. Define specific locations, organize them into routes, and have bots navigate between them automatically. Supports sequential patrols, random reshuffling, and persistent route storage.

## How It Works

Waypoints are named locations stored per-world in `waypoints.yml`. Routes are ordered lists of waypoints. When a bot starts a patrol, the `PatrolManager` navigates the bot through the route using FPP's navigation system. On arrival at each waypoint, the bot proceeds to the next one, looping when the route ends.

## Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-waypoints/config.yml`

```yaml
enabled: true

patrol:
  arrival-distance: 1.5
  random-reshuffle-each-cycle: true

migration:
  import-core-waypoints: true
```

## Commands

### Waypoint Management

```
/fpp waypoint create <name>              # Create waypoint at your location
/fpp waypoint delete <name>              # Delete a waypoint
/fpp waypoint list                       # List all waypoints
/fpp waypoint info <name>                # Show waypoint details
```

### Route Management

```
/fpp waypoint add <name>                 # Add waypoint to current route
/fpp waypoint remove <name>              # Remove waypoint from route
/fpp waypoint clear                      # Clear all routes
/fpp waypoint route <name1> <name2> ...  # Create ordered route
```

### Patrol Management

```
/fpp waypoint goto <bot> <name>          # Send bot to single waypoint
/fpp waypoint patrol <bot> [name]        # Start bot on patrol route
/fpp waypoint patrol <bot> stop          # Stop bot's patrol
/fpp waypoint patrol all [name]          # All bots patrol
/fpp waypoint patrol all stop            # Stop all patrols
/fpp waypoint reload                     # Reload waypoints from file
```

**Alias:** `wp` can be used instead of `waypoint`

## Key Features

- **Named Waypoints**: Descriptive names for locations
- **Persistent Routes**: Saved in `waypoints.yml` across restarts
- **Sequential Patrols**: Bots navigate waypoints in order
- **Random Reshuffle**: Optionally randomize waypoint order each cycle
- **Arrival Detection**: Configurable distance threshold for "arrived" status
- **Per-World Storage**: Waypoints are specific to each world
- **Core Migration**: Imports existing core waypoints on first load
- **Multi-Bot Patrol**: Start patrol for all bots at once
- **Case-Insensitive**: Waypoint names are case-insensitive

## Permission Model

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.waypoints` | Access waypoint system | op |
| `fpp.waypoints.create` | Create waypoints | op |
| `fpp.waypoints.delete` | Delete waypoints | op |
| `fpp.waypoints.patrol` | Manage patrols | op |

## Use Cases

### Guard Patrol

Create a patrol route for security bots:

```
/fpp wp create entrance
/fpp wp create throne-room
/fpp wp create armory
/fpp wp create dungeon

# Create route by adding in order
/fpp wp route entrance throne-room armory dungeon entrance

# Start patrol
/fpp wp patrol GuardBot1
```

### NPC Tour Guide

Create a tour route for guide bots:

```
/fpp wp create spawn
/fpp wp create shop
/fpp wp create pvp-arena
/fpp wp create parkour
/fpp wp create farm

# Start tour patrol
/fpp wp patrol TourBot spawn
```

### Delivery Bots

Bots that patrol between resource locations:

```
/fpp wp create mine-entrance
/fpp wp create storage-room
/fpp wp create furnace-room
/fpp wp create crafting-area

/fpp wp patrol DeliveryBot1
```

### Zoo / Display Bots

Bots that patrol animal exhibit areas:

```
/fpp wp create penguin-exhibit
/fpp wp create lion-den
/fpp wp create aviary
/fpp wp create aquarium

/fpp wp patrol ZooBot1
```

## Architecture

```
FppWaypointsExtension (main)
├── /fpp waypoint — Command handler
├── WaypointStore — Data persistence
│   ├── waypoints.yml
│   ├── Route CRUD (create, read, update, delete)
│   └── Core data migration
└── PatrolManager — Active patrol control
    ├── Navigation callback system
    ├── Sequential waypoint advancement
    ├── Random reshuffle support
    └── Cleanup on bot despawn
```

## Data Storage

Waypoints are stored in:

```
plugins/FakePlayerPlugin/extensions/fpp-waypoints/waypoints.yml
```

Format:
```yaml
routes:
  world_name:
    guard-route:
      - "entrance"
      - "throne-room"
      - "armory"
      - "dungeon"
waypoints:
  world_name:
    entrance:
      x: 100.0
      y: 64.0
      z: 200.0
      yaw: 0.0
      pitch: 0.0
    throne-room:
      x: 150.0
      y: 64.0
      z: 180.0
      yaw: 90.0
      pitch: 0.0
```

## Patrol Behavior

### Sequential Patrol

Bots navigate waypoints in order:
```
entrance → throne-room → armory → dungeon → entrance → ...
```

### Random Reshuffle (`random-reshuffle-each-cycle: true`)

Each complete cycle, the route order is randomized:
```
Cycle 1: entrance → dungeon → throne-room → armory → entrance
Cycle 2: armory → entrance → dungeon → throne-room → armory
```

### Arrival Detection

A bot is considered "arrived" when within `arrival-distance` blocks of the waypoint (default: 1.5 blocks).

### Loop

Patrols loop indefinitely until stopped with `/fpp wp patrol <bot> stop`.

## Migration

On first load, the extension checks for existing core waypoint data and imports it automatically (configurable via `migration.import-core-waypoints`).

## Troubleshooting

### Bot Not Moving to Waypoint

- Verify bot is online and in loaded chunks
- Check waypoint location is valid (same world)
- Ensure no obstacles blocking the bot
- Verify FPP navigation is functional

### Patrol Not Looping

- Route must be a valid ordered list
- Patrol continues until stopped with `/fpp wp patrol stop`
- Check bot hasn't despawned

### Waypoints Not Found on Reload

- Waypoints are per-world — you must be in the correct world
- Check `waypoints.yml` file exists
- Verify file has correct YAML format
- Run `/fpp wp reload` to reload from disk

### Wrong Arrival Detection

- Adjust `patrol.arrival-distance` in config
- Default 1.5 blocks — increase for generous arrival
- Decrease for precise stopping

## Technical Details

- **Priority**: 100 (default)
- **Data File**: `waypoints.yml`
- **Navigation API**: Uses FPP's `navigateTo()` with arrival callbacks
- **Patrol Cleanup**: Automatically stops patrol when bot despawns
- **Route Naming**: Case-insensitive
- **Core Migration**: Imports waypoints from core data files
