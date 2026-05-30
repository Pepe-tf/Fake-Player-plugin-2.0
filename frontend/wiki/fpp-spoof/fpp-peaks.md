# fpp-peaks - Peak Hours Manager

Time-based bot scheduling for optimal server population management.

## Overview

fpp-peaks manages your bot population based on time of day and day of week. Automatically adjust how many bots are awake vs sleeping to match your server's natural player activity patterns. More bots during peak hours, fewer during off-peak times.

## How It Works

The extension runs a schedule that defines what fraction of your total bot pool should be active at any given time. Throughout the day, bots are put to sleep or woken up in staggered intervals to reach the target fraction. The schedule can be customized for each day of the week.

## Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-peaks/config.yml`

```yaml
enabled: true

permissions:
  command: fpp.peaks

peak-hours:
  enabled: false
  timezone: "UTC"
  stagger-seconds: 30
  min-online: 0
  notify-transitions: false

  schedule:
    - { start: "06:00", end: "09:00", fraction: 0.30 }
    - { start: "09:00", end: "18:00", fraction: 0.75 }
    - { start: "18:00", end: "22:00", fraction: 1.00 }
    - { start: "22:00", end: "06:00", fraction: 0.05 }

  day-overrides:
    SATURDAY:
      - { start: "10:00", end: "23:00", fraction: 1.00 }
      - { start: "23:00", end: "10:00", fraction: 0.10 }
    SUNDAY:
      - { start: "10:00", end: "22:00", fraction: 0.90 }
      - { start: "22:00", end: "10:00", fraction: 0.10 }
```

## Commands

```
/fpp peaks on                           # Enable peak hours management
/fpp peaks off                          # Disable peak hours management
/fpp peaks status                       # Show current peak hours status
/fpp peaks next                         # Show next schedule transition
/fpp peaks force                        # Force recalculation now
/fpp peaks list                         # List full schedule
/fpp peaks wake <bot>                   # Wake a specific sleeping bot
/fpp peaks sleep <bot>                  # Put a specific bot to sleep
```

## Schedule Configuration

### Time Slots

Each schedule entry defines:
- **start**: Beginning of the time slot (24h format)
- **end**: End of the time slot (can cross midnight)
- **fraction**: Target fraction of bots to be awake (0.0 - 1.0)

### Default Schedule

| Time Slot | Fraction | Meaning |
|-----------|----------|---------|
| 06:00 - 09:00 | 0.30 | Morning — 30% of bots awake |
| 09:00 - 18:00 | 0.75 | Day — 75% of bots awake |
| 18:00 - 22:00 | 1.00 | Evening — all bots awake |
| 22:00 - 06:00 | 0.05 | Night — 5% of bots awake |

### Day Overrides

Weekend overrides replace the default schedule entirely:

**Saturday:**
| Time Slot | Fraction | Meaning |
|-----------|----------|---------|
| 10:00 - 23:00 | 1.00 | All bots awake during weekend |
| 23:00 - 10:00 | 0.10 | Minimal bots overnight |

**Sunday:**
| Time Slot | Fraction | Meaning |
|-----------|----------|---------|
| 10:00 - 22:00 | 0.90 | 90% awake |
| 22:00 - 10:00 | 0.10 | Minimal bots overnight |

## Key Features

- **Time-Based Scaling**: Automatically adjusts bot counts by time of day
- **Day-of-Week Overrides**: Different schedules for weekends/holidays
- **Staggered Transitions**: Bots sleep/wake gradually to avoid sudden changes
- **Crash Recovery**: Persists sleeping bot state to survive restarts
- **Force Check**: Manual recalculation at any time
- **Per-Bot Control**: Wake or sleep individual bots
- **Notifications**: Optional transition announcements
- **Minimum Online**: Configurable minimum bot count

## Permission Model

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.peaks` | Access peak hours commands | op |

## Architecture

```
FppPeaksExtension (main)
├── /fpp peaks — Command handler
└── PeakHoursManager — Core scheduling engine
    ├── Schedule computation (time → target fraction)
    ├── Bot sleeping queue management
    ├── Staggered sleep/wake with configurable delay
    ├── Database persistence (crash recovery)
    └── Day-of-week override resolution
```

## How Bots Sleep

When bots are "put to sleep" by fpp-peaks:
1. The bot is despawned from the world
2. Its state is saved to the database
3. On wake, the bot is respawned at its last location
4. The bot's metadata is restored

## Use Cases

- **Small Servers**: Keep the server looking active during quiet hours
- **Resource Management**: Reduce server load during off-peak times
- **Realistic Population**: Bot counts that match real player patterns
- **Event Scheduling**: Ramp up bots before scheduled events
- **Testing Environments**: Simulate different population levels

## Examples

### Family-Friendly Server

```yaml
peak-hours:
  timezone: "America/New_York"
  schedule:
    - { start: "07:00", end: "09:00", fraction: 0.20 }  # Before school
    - { start: "09:00", end: "15:00", fraction: 0.10 }  # School hours
    - { start: "15:00", end: "22:00", fraction: 1.00 }  # After school
    - { start: "22:00", end: "07:00", fraction: 0.05 }  # Bedtime
```

### 24/7 Active Server

```yaml
peak-hours:
  schedule:
    - { start: "00:00", end: "23:59", fraction: 1.00 }
```

### Work Hours Server

```yaml
peak-hours:
  timezone: "America/Chicago"
  schedule:
    - { start: "08:00", end: "17:00", fraction: 0.15 }  # Work hours
    - { start: "17:00", end: "23:00", fraction: 1.00 }  # Evening
    - { start: "23:00", end: "08:00", fraction: 0.05 }  # Night
  day-overrides:
    SATURDAY:
      - { start: "08:00", end: "23:59", fraction: 1.00 }
      - { start: "00:00", end: "08:00", fraction: 0.10 }
    SUNDAY:
      - { start: "08:00", end: "23:59", fraction: 1.00 }
      - { start: "00:00", end: "08:00", fraction: 0.10 }
```

## Troubleshooting

### Bot Not Sleeping/Waking

- Check `peak-hours.enabled: true`
- Verify the schedule is correctly configured
- Check `timezone` is set to your server's timezone
- Verify bots are owned by the server (not individual players)

### Wrong Bot Count

- Check current schedule fraction
- Run `/fpp peaks force` to recalculate
- Verify `min-online` is not overriding the schedule
- Check for recently spawned/despawned bots

### Transitions Not Smooth

- Decrease `stagger-seconds` for faster transitions
- Increase for slower, more gradual changes
- Check for performance issues with large bot counts

## Technical Details

- **Priority**: 90 (loads earlier than default)
- **Persistence**: Sleeping bot state saved to database
- **Crash Recovery**: On restart, recalculates target population and adjusts
- **Stagger**: Bots are processed one at a time with configurable delay between each
- **Time Zone**: Configurable, defaults to UTC
