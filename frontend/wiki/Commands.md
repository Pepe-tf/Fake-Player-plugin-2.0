# Commands

All commands are prefixed with `/fpp` (aliases: `fakeplayer`, `fp`).

## Core Commands

| Command | Usage | Description | Permission |
|---------|-------|-------------|------------|
| **spawn** | `[amount] [world [x y z]] [--name <name>] [--random-name] [--notp] [<bottype>]` | Spawn one or more fake player bots | `fpp.spawn` (admin) / `fpp.spawn.user` (user) |
| **despawn** | `<name> \| --all \| --own \| --count <n> \| --random [--count <n>]` | Despawn bots by name, owner, count, or random selection | `fpp.despawn` |
| **list** | `[page]` | List all currently active bots | `fpp.list` |
| **tph** | `[botname\|all]` | Teleport your bot(s) to you | `fpp.tph` |
| **tp** | `[botname]` | Teleport you to a bot | `fpp.tp` |
| **xp** | `/fpp xp <bot>` | Collect XP from a bot | `fpp.xp` |
| **move** | `<bot\|all> --direction <forward\|backward\|left\|right> [--seconds <n> \| --ticks <n>]  \|  <bot\|all> --stop` | Directional movement input for a bot | `fpp.move` |
| **left-click** | `<bot> [--once\|--repeat\|--hold\|--stop]  \|  --stop` | Bot left-clicks: breaks targeted blocks or attacks targeted entities | `fpp.left-click` |
| **right-click** | `<bot> [--once\|--repeat\|--hold\|--stop]  \|  --stop` | Bot right-clicks: uses held items and interacts with blocks/entities | `fpp.right-click` |
| **attack** | `<bot\|all> [--once] [--stop]` | Basic swing/attack command | `fpp.attack` |
| **sneak** | `<bot> [on\|off\|toggle]` | Toggle or set the sneaking state for a live bot body | `fpp.sneak` |
| **stop** | `[<bot>\|all]` | Stop all active tasks for one bot or all bots | `fpp.stop` |
| **freeze** | `<bot\|all> [on\|off]` | Freeze or unfreeze a bot in place | `fpp.freeze` |
| **inventory** | `/fpp inventory <bot>` (alias: `inv`) | Open a bot's full inventory | `fpp.inventory` |
| **save** | — | Save all active bot data immediately | `fpp.save` |
| **setowner** | `<bot> <player>` | Set the owner of a bot | `fpp.setowner` |
| **rename** | `<oldname> <newname>` | Rename an active bot (preserves all data) | `fpp.rename` |
| **info** | `[bot\|spawner] <name>` | Query bot session history from the database | `fpp.info` (admin) / `fpp.info.user` (own bots) |
| **stats** | — | Display live plugin statistics | `fpp.stats` |
| **badword** | `<check\|update\|status>` | Scan and fix bot names flagged by the badword filter | `fpp.badword` |
| **migrate** | `<backup\|status\|config\|lang\|names\|db>` | Manages config/data migration and backups | `fpp.migrate` |
| **check** | `[--deep\|--simulation\|--commands\|--listeners\|--nms\|--database\|--folia\|--world\|--config\|--extensions\|--memory\|--all]` | Run a system health check | `fpp.check` |
| **reload** | `[all\|config\|lang\|extensions]` | Reloads the plugin configuration (optionally target a subsystem) | `fpp.reload` |
| **settings** | `[bot]` | Open the interactive settings GUI (global, per-bot, or **debug** category) | `fpp.settings` |
| **extension** | (bare) `\| --list` | Open marketplace link or list loaded extensions | (implied admin) |
| **help** | `[page]` | Shows the command help menu | `fpp.help` |

## Usage Examples

```
/fpp spawn 5                          # spawn 5 bots at sender location
/fpp spawn --name Steve               # spawn a bot named "Steve"
/fpp spawn --notp                     # spawn at bot's last known location (if persisted)
/fpp spawn world_nether 100 64 -200   # spawn in another world at coords
/fpp spawn 3 afk                      # spawn 3 bots with "afk" bot-type preset
/fpp despawn --all                    # remove all bots
/fpp despawn --own                    # remove bots you spawned
/fpp despawn --random --count 3       # remove 3 random bots
/fpp move bot1 --direction forward --seconds 3  # move bot1 forward for 3 seconds
/fpp left-click bot1 --once           # break/attack the target once
/fpp right-click bot1 --repeat        # repeatedly use/interact with target
/fpp attack bot1 --once               # perform one basic attack swing
/fpp sneak bot1 toggle                # toggle sneak state
/fpp stop bot1                        # stop all active tasks on bot1
/fpp freeze bot1 on                   # freeze bot1
/fpp inv bot1                         # open bot1 inventory
/fpp rename bot1 builder_01           # rename bot1
/fpp check --all                      # run all health checks
/fpp info bot1                        # show session history for bot1
```

## Notes

- `--all` on task commands sends the command to every bot the sender can administer.
- `--once` performs a single action and then stops.
- `--stop` cancels the command's activity for the specified bot(s).
- `--notp` spawns a bot at its last known persisted location instead of the sender's location.
- `spawn` accepts an optional `BotType` token such as `afk` as the first positional argument.
- `spawn` coordinates can be separate `x y z`, compact `x,y,z`, or relative values such as `~`, `~5`, and `~-3`.
- Core `/fpp move` is **directional input only** (`--direction forward|backward|left|right`). Pathfinding movement, follow, and roam behavior are provided by extensions.
- Core `/fpp attack` is a **basic swing/attack** command only. Rich combat and hunting behavior are provided by extensions.
- `left-click` and `right-click` are the current core click automation commands. Older mine/use/place-style automation has been moved out of the registered core command set.
