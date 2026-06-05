# fpp-waypoints - Waypoints Extension

Store named waypoint routes and send bots on looping patrols.

## Configuration

File: `plugins/FakePlayerPlugin/extensions/fpp-waypoints/config.yml`

```yaml
enabled: true

patrol:
  arrival-distance: 1.5
  random-reshuffle-each-cycle: true

migration:
  import-core-waypoints: true
```

## Commands

`/fpp waypoint` alias: `/fpp wp`

```text
/fpp wp add <route>
/fpp wp create <route>
/fpp wp remove <route> <index>
/fpp wp delete <route>
/fpp wp clear <route>
/fpp wp list [route]
/fpp wp patrol <bot|all> <route> [--random]
/fpp wp stop <bot|all>
```

## Permissions

| Permission | Description |
|------------|-------------|
| `fpp.waypoint` | Use waypoint route and patrol commands |

## Example

```text
/fpp wp create guard-route
/fpp wp add guard-route
/fpp wp add guard-route
/fpp wp list guard-route
/fpp wp patrol GuardBot guard-route --random
/fpp wp stop GuardBot
```

## Notes

- Waypoints are route entries, not standalone named destinations with a separate `goto` command.
- `patrol <bot|all> <route>` loops through the route until stopped.
- `--random` randomizes patrol order; `patrol.random-reshuffle-each-cycle` controls reshuffling between loops.
