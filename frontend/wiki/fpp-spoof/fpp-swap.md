# fpp-swap - Bot Swap Extension

Session rotation system for FPP bots — bots periodically leave and rejoin the server to simulate real player behavior.

## Overview

fpp-swap makes bots behave like real players by giving them "sessions." Each bot stays online for a random duration, leaves with a farewell message, waits offline for a random period, then rejoins with a greeting. This creates the illusion of players coming and going naturally.

## How It Works

The extension assigns each bot a **personality** that influences session length. A timer tracks each bot's session, and when it expires:

1. Bot sends a farewell message (e.g., "gtg", "brb")
2. Bot waits a brief "leave delay" (simulating logging out)
3. Bot despawns and enters the "swapped out" pool
4. After a random absence period, bot attempts to rejoin
5. Bot respawns at its last location with inventory and XP restored
6. Bot sends a greeting message (e.g., "back", "hey")
7. A new session timer begins

### Personality System

Each bot gets a randomly assigned personality that affects session duration:

| Personality | Probability | Session Multiplier | Behavior |
|-------------|------------|-------------------|----------|
| **CASUAL** | 32% | 1.0x | Average sessions, normal play patterns |
| **GRINDER** | 15% | 1.6x | Long sessions, stays online for hours |
| **SOCIAL** | 15% | 0.65x | Short sessions, comes and goes frequently |
| **LURKER** | 14% | 2.2x | Very long sessions, stays online forever |
| **ACTIVE** | 14% | 0.45x | Very short sessions, pops in briefly |
| **SPORADIC** | 10% | 1.1x + random | Unpredictable, highly variable sessions |

Personality is assigned on first spawn and persists across swaps (same bot keeps its personality).

## Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-swap/config.yml`

```yaml
enabled: true

permissions:
  command: fpp.swap

swap:
  enabled: false
  debug: false
  max-swapped-out: 0
  min-online: 0
  same-name-on-rejoin: true
  farewell-chat: true
  greeting-chat: true
  retry-rejoin: true
  retry-delay: 60
  session:
    min: 60
    max: 300
  absence:
    min: 30
    max: 120
```

## Commands

```
/fpp swap                          # Toggle swap system on/off
/fpp swap on                       # Enable swap system
/fpp swap off                      # Disable swap system
/fpp swap status                   # Show current swap status
/fpp swap now <bot>                # Force a bot to swap now
/fpp swap list                     # List all scheduled bots
/fpp swap info <bot>               # Show swap info for a bot
```

**Aliases:** Available in the `/fpp settings` GUI under the "🔄 ꜱᴡᴀᴘ" tab.

## Command Examples

### Status Check

```
/fpp swap status
```

Shows:
- Active session count
- Number of swapped-out bots
- Time until next swap
- Minimum online setting

### Force Swap

```
/fpp swap now Bot1
```

Instantly triggers Bot1's leave sequence regardless of remaining session time.

### Bot Info

```
/fpp swap info Bot1
```

Shows:
- Personality type
- Total swap count
- Remaining session time
- Current offline/online status

## Key Features

- **Session Rotation**: Bots cycle online/offline like real players
- **6 Personalities**: Different session patterns for variety
- **Inventory Persistence**: Bot inventory and XP are saved and restored on rejoin
- **Farewell/Greeting Chat**: Bots say goodbye when leaving and hello when returning
- **Name Preservation**: Bots try to reclaim their original name on rejoin
- **Retry on Failure**: If rejoin fails (name taken, spawn issue), retries automatically
- **Minimum Online**: Configurable minimum bots to keep active
- **Max Offline Limit**: Prevents too many bots from being offline at once
- **Settings GUI**: Full configuration via `/fpp settings` inventory

## Settings GUI

Available in `/fpp settings` under the "🔄 ꜱᴡᴀᴘ" tab:

| Setting | Description | Values |
|---------|-------------|--------|
| Swap System | Toggle swap on/off | On/Off |
| Farewell Messages | Bots say goodbye before leaving | On/Off |
| Greeting Messages | Bots greet on return | On/Off |
| Keep Name on Rejoin | Try to reclaim original name | On/Off |
| Session - Min | Shortest session | 30-600s |
| Session - Max | Longest session | 60-1200s |
| Absence - Min | Shortest offline time | 15-120s |
| Absence - Max | Longest offline time | 30-300s |
| Max Offline | Max bots offline at once | 0-10 (0=unlimited) |
| Min Online | Minimum bots to keep online | 0-10 |
| Retry Rejoin | Retry if rejoin fails | On/Off |
| Retry Delay | Wait before retry | 30-600s |

## Permission Model

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.swap` | Use swap commands | op |

## Use Cases

### Realistic Server Population

Bots cycle naturally — some stay for hours (lurkers, grinders), others pop in briefly (active, social). The server always has a mix of "new" and "returning" bots.

### Load Management

```yaml
swap:
  max-swapped-out: 5
  min-online: 3
```

Ensures at least 3 bots are always online while no more than 5 are offline at once.

### Chat Activity

With farewell and greeting chat enabled:
```
[Bot1] gtg
[Bot1]   (disconnects)
... 2 minutes later ...
[Bot1] back
```

Creates natural chat patterns that make the server feel alive.

## Architecture

```
FppSwapExtension (main)
├── /fpp swap — Command handler
├── SwapSettingsTab — GUI settings tab
└── BotSwapAI — Core swap controller (implements BotSwapController)
    ├── Session scheduling (per-bot timers)
    ├── Personality assignment
    ├── Leave sequence (farewell → delay → despawn)
    ├── Absence tracking
    ├── Rejoin sequence (spawn → restore → greet → reschedule)
    ├── Inventory/XP snapshot and restore
    └── Retry logic on failed rejoin
```

## Session Lifecycle

```
[SCHEDULED] ──(session timer expires)──> [LEAVING]
    ▲                                          │
    │                                    farewell chat
    │                                    leave delay
    │                                          │
    │                                          ▼
    │                                    [DESPAWNED]
    │                                          │
    │                                    absence timer
    │                                          │
    │                                          ▼
    │                                    [REJOINING]
    │                                          │
    │                                    spawn bot
    │                                    restore inventory
    │                                    greeting chat
    │                                          │
    └───────────(reschedule)────────────────────┘
```

## Data Flow

1. **Timer starts**: Bot's session begins, personality determines duration
2. **Timer expires**: Leave sequence triggered
3. **Snapshot**: Bot's inventory (all 41 slots), XP total, level, and progress are serialized to Base64
4. **Despawn**: Bot entity is removed from world
5. **Wait**: Random absence period (configurable min/max)
6. **Rejoin**: New bot entity spawned at last location
7. **Restore**: Inventory and XP deserialized and applied to new bot
8. **Greet**: Greeting message sent
9. **Reschedule**: New session timer started

## Troubleshooting

### Bot Not Swapping

- Check `swap.enabled: true` (both at top level and under `swap:` section)
- Verify `enabled: true` at top of config
- Check `min-online` is not blocking swaps
- Check `max-swapped-out` limit

### Bot Not Rejoining

- Check `retry-rejoin: true` for automatic retries
- Verify `retry-delay` is reasonable
- Check server logs for spawn failures
- Ensure bot's last location world still exists

### Inventory Not Restoring

- Verify snapshot was taken (check debug logs)
- Inventory restore happens 10 ticks after spawn
- May fail if bot is in unloaded chunks

### Name Not Preserved

- Another bot or player may have taken the name
- Set `same-name-on-rejoin: true`
- Bot will get a new auto-generated name if original is taken

## Technical Details

- **Priority**: 80 (loads earlier than default)
- **Swap Messages**: 56 farewell variations, 55 greeting variations
- **Inventory Serialization**: Base64-encoded ItemStack bytes
- **Session Growth**: Session length increases up to 40% over first 5 swaps (8% per swap)
- **Timer Accuracy**: Session and rejoin timers check every second (20 ticks)
- **Cleanup**: All timers cancelled on disable, state cleared
- **Integration**: Implements `BotSwapController` interface in FPP core
