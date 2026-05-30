# fpp-ping - Ping Extension

Show and spoof ping values for FPP bots in the tab list.

## Overview

fpp-ping gives you full control over how bots appear in the tab list ping column. Set specific ping values, generate realistic random pings, or simulate network conditions like lag spikes and ping ramps. Makes bots look like real players with varying connection quality.

## How It Works

The extension hooks into FPP's tab list system to override the ping value displayed for each bot. You can set static pings, random pings within a range, or enable the dynamic ping simulator that creates realistic fluctuations.

## Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-ping/config.yml`

```yaml
enabled: true

random:
  min: 20
  max: 200

ping:
  enabled: false
  min: 20
  max: 200
  variability: 8
  update-interval: 40
  latency-effect: true
  behavior-effect: true
  max-behavior-skip-ticks: 8
  spike-chance: 0.04
  spike-min: 200
  spike-max: 600
  join-ramp-ticks: 60

permissions:
  base: fpp.ping
  set: fpp.ping.set
  random: fpp.ping.random
  bulk: fpp.ping.bulk

messages:
  prefix: "&8[&bFPP Ping&8]&r "
```

## Commands

### Single Bot Operations

```
/fpp ping <bot>                        # View bot's ping
/fpp ping <bot> --ping <ms>            # Set ping to specific value
/fpp ping <bot> --random               # Set random ping (20-500ms)
/fpp ping <bot> --reset                # Reset to default ping
```

### Bulk Operations

```
/fpp ping --all                        # View all bot pings
/fpp ping --all --ping <ms>            # Set all bots to same ping
/fpp ping --all --random               # Random ping for all bots
/fpp ping --all --reset                # Reset all bot pings
```

### Count-Based Operations

```
/fpp ping --count <n>                  # View n random bots
/fpp ping --count <n> --ping <ms>      # Set n random bots to ping
```

## Key Features

### Dynamic Ping Simulation

When `ping.enabled: true`, bots get realistic ping behavior:

| Feature | Description |
|---------|-------------|
| **Base Ping** | Configurable min/max range |
| **Variability** | Ping fluctuates naturally (default: ±8ms) |
| **Latency Effect** | Higher ping = slight delay in bot actions |
| **Behavior Effect** | High ping causes occasional action skips |
| **Lag Spikes** | 4% chance of spike (200-600ms) |
| **Join Ramp** | Ping starts high and drops over 60 ticks (3 seconds) |

### Static Ping

Set specific ping values:
```
/fpp ping Bot1 --ping 100
```

### Random Ping

Generate random pings within configurable range:
```
/fpp ping Bot1 --random
```

Random range defaults to 20-200ms (configurable via `random.min` and `random.max`).

### Bulk Management

Manage multiple bots at once:
```
/fpp ping --all --ping 50       # All bots at 50ms
/fpp ping --all --random        # Random for all
/fpp ping --count 5 --ping 100  # 5 random bots at 100ms
```

## Permission Model

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.ping` | View ping values | true |
| `fpp.ping.set` | Set ping values | op |
| `fpp.ping.random` | Use random ping | op |
| `fpp.ping.bulk` | Use bulk operations | op |

## Use Cases

### Realistic Bot Population

```yaml
random:
  min: 20
  max: 150
```

Bots get varied, realistic ping values that change dynamically.

### Latency Testing

```yaml
ping:
  enabled: true
  min: 200
  max: 500
  spike-chance: 0.10
  spike-min: 500
  spike-max: 1000
```

Simulate high-latency bots for testing lag-related features.

### Clean Tab List

All bots at 0ms for a clean, professional look:
```
/fpp ping --all --ping 0
```

Or with `ping.enabled: false`, bots use the server's default.

## Dynamic Ping Details

### Variability

Ping values fluctuate by ±`variability` ms each update interval:
```
Base: 100ms
Tick 1: 97ms
Tick 2: 104ms
Tick 3: 93ms
...
```

### Lag Spikes

With spike enabled, bots occasionally show sudden ping increases:
```
Normal: 45ms
Spike:  → 342ms
Recovery → 48ms
```

### Join Ramp

When a bot first appears or spawns, its ping starts high and gradually decreases:
```
Tick 1: 250ms
Tick 10: 180ms
Tick 30: 90ms
Tick 60: 45ms (stable)
```

## Examples

### Realistic Player Simulation

```yaml
random:
  min: 15
  max: 120

ping:
  enabled: true
  min: 15
  max: 80
  variability: 10
  spike-chance: 0.02
  spike-min: 150
  spike-max: 400
  join-ramp-ticks: 40
```

### High Ping Server Roleplay

```yaml
random:
  min: 100
  max: 300

ping:
  enabled: true
  min: 100
  max: 250
  variability: 25
  spike-chance: 0.08
```

### Low Ping (LAN-Style)

```yaml
random:
  min: 1
  max: 10

ping:
  enabled: false
```

All bots show near-zero ping.

## Troubleshooting

### Ping Not Changing in Tab List

- Verify `enabled: true` in config
- Check player has `fpp.ping.set` permission
- Ensure tab list updates (may take a few seconds)
- Verify no conflicting tab list plugins

### Random Ping Outside Range

- Check `random.min` and `random.max` config
- Verify `--random` flag was used (not `--ping`)
- With dynamic ping, values may exceed static range

### Dynamic Ping Not Working

- Ensure `ping.enabled: true`
- Check `update-interval` is reasonable (default: 40 ticks = 2 seconds)
- Bots must be in loaded chunks

## Technical Details

- **Priority**: 100 (default)
- **Update Method**: Tick-based ping recalculation
- **Default Ping**: 0ms (no spoofing when extension disabled)
- **API Used**: FPP tab list ping override method
