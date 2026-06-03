# Commands

All commands are prefixed with `/fpp` (aliases: `fakeplayer`, `fp`).

## Core Commands

| Command | Usage | Description | Permission |
|---------|-------|-------------|------------|
| **spawn** | `[amount] [world [x y z]] [--name <name>] [--random-name] [--notp] [<bottype>]` | Spawn one or more fake player bots | `fpp.spawn` (admin) / `fpp.spawn.user` (user) |
| **despawn** | `<name> \| all \| --count <n> \| --random [--count <n>]` | Despawn a fake player bot by name | `fpp.despawn` |
| **list** | `[page]` | List all currently active bots | `fpp.list` |
| **tph** | `[botname\|all]` | Teleport your bot(s) to you | `fpp.tph` |
| **tp** | `[botname]` | Teleport you to a bot | `fpp.tp` |
| **xp** | `/fpp xp <bot>` | Collect XP from a bot | `fpp.xp` |
| **move** | `<bot\|all> --to <player>  \|  <bot\|all> --coords <x> <y> <z> [alias: --pos]  \|  <bot\|all> --roam [x,y,z] [radius \| infinite \| forever \| unbounded]  \|  <bot\|all> --stop` | Navigate a bot to a player or roam randomly | `fpp.move` |
| **mine** | `<bot> [--once\|--stop\|--pos1\|--pos2\|--start\|--wesel]  \|  --stop` | Walk a bot to mine blocks, continuously, or clear a selected area | `fpp.mine` |
| **place** | `<bot> [--once\|--stop\|--wesel]  \|  --stop` | Bot places blocks it is looking at, like mine but placing | `fpp.place` |
| **use** | `<bot> [--once\|--stop]  \|  --stop` | Walk a bot to your position then right-click what it's looking at | `fpp.use.cmd` |
| **attack** | `<bot\|all> [--mob [type]] [--range <n>] [--type <mob>] [--priority nearest\|lowest-health] [--move] [--stop]  \|  <bot\|all> --hunt [<mob>] [--range <n>] [--priority <mode>] [--stop]` | Attack nearby entities or hunt mobs | `fpp.attack` |
| **follow** | `<bot\|all> <player\|--start>  \|  <bot\|all> --stop` | Make a bot continuously follow a player | `fpp.follow` |
| **find** | `<bot> <block> [-r <n> / --radius <n>] [-c <n> / --count <n>] [--prefer-visible]  \|  <bot> --stop  \|  --stop` | Path to and mine nearby blocks of a chosen type | `fpp.find` |
| **sleep** | `<bot\|all> <x y z> <radius>  \|  <bot\|all> --stop` | Set a sleep-origin for a bot so it auto-sleeps at night | `fpp.sleep` |
| **stop** | `[<bot>\|all]` | Stop all active tasks for one bot or all bots | `fpp.stop` |
| **freeze** | `<bot\|all> [on\|off]` | Freeze or unfreeze a bot in place | `fpp.freeze` |
| **inventory** | `/fpp inventory <bot>` (alias: `inv`) | Open a bot's full inventory | `fpp.inventory` |
| **storage** | `<bot> [storage_name\|--list\|--remove <name>\|--clear]` | Set or manage storage targets for a bot | `fpp.storage` |
| **save** | — | Save all active bot data immediately | `fpp.save` |
| **setowner** | `<bot> <player>` | Set the owner of a bot | `fpp.setowner` |
| **rename** | `<oldname> <newname>` | Rename an active bot (preserves all data) | `fpp.rename` |
| **info** | `[bot\|spawner] <name>` | Query bot session history from the database | `fpp.info` (admin) / `fpp.info.user` (own bots) |
| **stats** | — | Display live plugin statistics | `fpp.stats` |
| **badword** | `<check\|update\|status>` | Scan and fix bot names flagged by the badword filter | `fpp.badword` |
| **migrate** | `<backup\|status\|config\|lang\|names\|db>` | Manages config/data migration and backups | `fpp.migrate` |
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
/fpp despawn all                      # remove all bots
/fpp despawn --random --count 3       # remove 3 random bots
/fpp move bot1 --to Notch             # navigate bot1 to Notch
/fpp move bot1 --roam 500,64,200 25   # roam within 25 blocks of center
/fpp mine bot1 diamond_ore --wesel    # mine using WorldEdit selection
/fpp place bot1 --once                # place one block
/fpp attack bot1 --hunt --range 16   # hunt mobs in 16-block range
/fpp follow bot1 Notch                # make bot1 follow Notch
/fpp find bot1 diamond_ore --radius 64 --count 20
/fpp sleep bot1 100 64 200 50         # sleep within 50 blocks of origin
/fpp stop bot1                        # stop all active tasks on bot1
/fpp freeze bot1 on                   # freeze bot1
/fpp inv bot1                         # open bot1 inventory
/fpp storage bot1 chest1              # register nearest container as "chest1"
/fpp rename bot1 builder_01           # rename bot1
/fpp info bot1                        # show session history for bot1
```

## Notes

- `--wesel` requires WorldEdit to be installed.
- `--all` on task commands sends the command to every bot the sender can administer.
- `--once` performs a single action and then stops.
- `--stop` cancels the command's activity for the specified bot(s).
- `--notp` spawns a bot at its last known persisted location instead of the sender's location.
- `spawn` accepts an optional `BotType` token (e.g., `afk`) as the first positional argument.
- `move --roam` accepts `infinite`/`forever`/`unbounded` as a radius keyword.
- `attack --move` makes the bot chase its target in mob attack mode.
- `find` short flags: `-r` for `--radius`, `-c` for `--count`.
