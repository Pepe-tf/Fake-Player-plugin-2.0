# Permissions

FPP uses a two-tier permission system with granular sub-nodes.

## Wildcard Tiers

| Node | Default | Description |
|------|---------|-------------|
| `fpp.admin` | `op` | Full access (identical to `fpp.op`) |
| `fpp.op` | `op` | Full admin wildcard — all commands |
| `fpp.use` | `true` | User-tier access — basic commands for all players |

## Core Permissions

### Command Visibility
- `fpp.command` — makes `/fpp` visible and usable (default: `true`)
- `fpp.plugininfo` — show full info panel on bare `/fpp` (default: `op`)

### Spawn
- `fpp.spawn` — admin spawn (ignores personal limits)
  - `fpp.spawn.multiple` — spawn more than one bot at a time
  - `fpp.spawn.mass` — alias for `fpp.spawn.multiple`
  - `fpp.spawn.name` — use `--name` for custom names
  - `fpp.spawn.coords` — spawn at explicit world/coordinates
- `fpp.spawn.user` — user spawn (limited by personal bot cap)
  - `fpp.spawn.limit.1` through `fpp.spawn.limit.100` — personal bot limit

### Despawn
- `fpp.despawn` — despawn bots (grants `fpp.delete` and `fpp.despawn.bulk`)
- `fpp.despawn.bulk` — despawn multiple bots (`--count`, `--random`)
- `fpp.despawn.own` — despawn only bots the sender spawned
- `fpp.delete` — legacy alias for `fpp.despawn`
- `fpp.delete.all` — legacy alias for bulk despawn

### Info / Teleport
- `fpp.list` — list all bots
- `fpp.stats` — view plugin statistics
- `fpp.info` — full admin session query
- `fpp.info.user` — user info (own bots only)
- `fpp.tp` — teleport to a bot
- `fpp.tph` — teleport bot(s) to sender
- `fpp.tph.all` — teleport all accessible bots
- `fpp.xp` — collect XP from own bots

### Movement
- `fpp.move` — navigate bots
  - `fpp.move.to` — `--to <player>`
  - `fpp.move.stop` — `--stop`

### Automation
- `fpp.mine` — bot mining
  - `fpp.mine.start`, `fpp.mine.once`, `fpp.mine.stop`, `fpp.mine.area`
  - `fpp.mine.wesel` — WorldEdit selection as mining area
- `fpp.place` — block placement
  - `fpp.place.start`, `fpp.place.once`, `fpp.place.stop`
  - `fpp.place.wesel` — WorldEdit selection as placement area
- `fpp.use.cmd` (alias: `fpp.useitem`) — right-click automation
  - `fpp.useitem.start`, `fpp.useitem.once`, `fpp.useitem.stop`
- `fpp.attack` — PvE attack
  - `fpp.mine.wesel` — use WorldEdit selection for mining area
- `fpp.place.wesel` — use WorldEdit selection for placement area
- `fpp.attack.hunt` — `--hunt` roaming mob hunt
- `fpp.find` — find-and-mine nearby blocks
- `fpp.follow` — make bot follow a player
- `fpp.sleep` — auto-sleep at night
- `fpp.stop` — cancel all active tasks

### Management
- `fpp.freeze` — freeze/unfreeze bots
- `fpp.rename` — rename any bot
  - `fpp.rename.own` — rename only own bots
- `fpp.inventory` — open bot inventory GUI
  - `fpp.inventory.cmd` — via command
  - `fpp.inventory.rightclick` — via right-click entity
- `fpp.storage` — manage supply containers
- `fpp.setowner` — transfer bot ownership
- `fpp.save` — force-save all active bots
- `fpp.settings` — open settings GUI

### System
- `fpp.reload` — hot-reload config
- `fpp.migrate` — backup/migrate/convert data
- `fpp.badword` — manage badword filter

### Bypass
- `fpp.bypass.max` — bypass global bot cap
- `fpp.bypass.cooldown` — skip spawn cooldown

### Notify
- `fpp.notify` — receive update notifications on join **and debug chat broadcasts** (when `debug-chat` is enabled)

## Quick Setup Examples

```
# Full admin
/lp group admin permission set fpp.admin true

# User access
/lp group member permission set fpp.use true

# Personal bot limit (5)
/lp user Alice permission set fpp.spawn.limit.5 true

# Bypass cooldown for VIPs
/lp group vip permission set fpp.bypass.cooldown true

# Hide /fpp from guests
/lp group guest permission set fpp.command false
```

## Legacy Nodes

These still work and map to their modern equivalents:
- `fpp.op` → identical to `fpp.admin`
- `fpp.delete` → identical to `fpp.despawn`
- `fpp.delete.all` → identical to `fpp.despawn.bulk`
- `fpp.useitem` → identical to `fpp.use.cmd`
