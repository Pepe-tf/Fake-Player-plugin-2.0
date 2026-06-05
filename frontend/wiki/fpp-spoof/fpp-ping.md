# fpp-ping - Ping Extension

View and override bot ping values, assign random pings, or enable dynamic ping simulation.

## Configuration

File: `plugins/FakePlayerPlugin/extensions/fpp-ping/config.yml`

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
```

## Commands

```text
/fpp ping <bot>
/fpp ping <bot> --ping <ms>
/fpp ping <bot> --random
/fpp ping <bot> --reset
/fpp ping --count <n>
/fpp ping --count <n> --ping <ms>
/fpp ping --count <n> --random
/fpp ping --count <n> --reset
/fpp ping
/fpp ping --ping <ms>
/fpp ping --random
/fpp ping --reset
```

Omitting `<bot>` and `--count` targets all active bots. There is no `--all` flag in the current source.

## Permissions

| Permission | Description |
|------------|-------------|
| `fpp.ping` | Base command access |
| `fpp.ping.set` | Set explicit ping values |
| `fpp.ping.random` | Apply random ping values |
| `fpp.ping.bulk` | Use count/all-bot operations |

## Notes

- Random pings use `random.min` and `random.max`.
- Dynamic simulation only runs when `ping.enabled: true`.
- `latency-effect` and `behavior-effect` can make simulated high ping affect bot timing.
