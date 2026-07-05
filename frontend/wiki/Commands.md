# Commands

All commands are prefixed with `/fpp` (aliases: `fakeplayer`, `fp`).

## Core Commands

| Command | Usage | Description | Permission |
|---------|-------|-------------|------------|
| **spawn** | `[--name <name>] [world [x y z]]` | Spawn **one** fake player bot per command — auto-named `bot`, `bot2`, … or custom-named via `--name` | `fpp.spawn` (admin) / `fpp.spawn.user` (user) |
| **despawn** | `<name> \| --all \| --own \| --count <n> \| --random [--count <n>]` | Despawn bots by name, owner, count, or random selection | `fpp.despawn` |
| **list** | `[page]` | List all currently active bots | `fpp.list` |
| **tph** | `[botname\|all]` | Teleport your bot(s) to you | `fpp.tph` |
| **tp** | `[botname]` | Teleport you to a bot | `fpp.tp` |
| **xp** | `/fpp xp <bot>` | Collect XP from a bot | `fpp.xp` |
| **move** | `<bot\|--all> --to <bot\|player>  \|  <bot\|--all> --coords <x> <y> <z> [world]  \|  <bot\|--all> --stop` | Pathfind to a bot/player (follows live) or to coordinates | `fpp.move` (+ `fpp.move.to` / `fpp.move.coords`) |
| **left-click** | `<bot> [--once\|--repeat\|--hold\|--stop]  \|  --stop` | Bot left-clicks: breaks the block / attacks the entity at the **exact point** you're aiming at; walks to a reachable vantage first if out of reach | `fpp.left-click` |
| **right-click** | `<bot> [--once\|--repeat\|--hold\|--stop]  \|  --stop` | Bot right-clicks: uses held items and interacts with the block/entity at the **exact point** you're aiming at; must aim at a button/lever's hit box to trigger it | `fpp.right-click` |
| **find** | `<bot> <block> [--radius\|-r <n>] [--count\|-c <n>] [--prefer-visible]  \|  <bot> --stop  \|  --stop` | Bot searches for and reports nearby blocks | `fpp.find` |
| **storage** | `<bot> [storage_name\|--list\|--remove <name>\|--clear\|--enable <name>\|--disable <name>\|--deposit [name]]` | Set or manage bot storage targets (chest/barrel/hopper/shulker) | `fpp.storage` |
| **attack** | `<bot\|all> [--once] [--stop]` | Basic swing/attack command | `fpp.attack` |
| **sneak** | `<bot> [on\|off\|toggle]` | Toggle or set the sneaking state for a live bot body | `fpp.sneak` |
| **stop** | `[<bot>\|all]` | Stop all active tasks for one bot or all bots | `fpp.stop` |
| **freeze** | `<bot\|all> [on\|off]` | Freeze or unfreeze a bot in place | `fpp.freeze` |
| **inventory** | `/fpp inventory <bot>` (alias: `inv`) | Open a bot's full inventory | `fpp.inventory` |
| **save** | — | Save all active bot data immediately | `fpp.save` |
| **setowner** | `<bot> <player>` | Set the owner of a bot | `fpp.setowner` |
| **rename** | `<bot> <new name>` | Change a bot's display name (name-tag, tab list, command output). Identity/UUID is unchanged and the "ʙᴏᴛ ʙʏ {owner}" row is kept. Supports colour codes; max 32 visible chars | `fpp.rename` |
| **info** | `[bot\|spawner] <name>` | Query bot session history from the database | `fpp.info` (admin) / `fpp.info.user` (own bots) |
| **check** | `[--deep\|--simulation\|--commands\|--listeners\|--nms\|--database\|--folia\|--world\|--config\|--extensions\|--memory\|--all]` | Run a system health check | `fpp.check` |
| **reload** | `[all\|config\|lang]` | Reloads the plugin configuration (optionally target a subsystem) | `fpp.reload` |
| **settings** | `[bot]` | Open the interactive settings GUI (global, per-bot, or **debug** category) | `fpp.settings` |
| **help** | `[page]` | Shows the command help menu | `fpp.help` |

## Usage Examples

```
/fpp spawn                            # spawn one auto-named bot at sender location
/fpp spawn --name Miner               # spawn one bot with a custom name
/fpp spawn world_nether 100 64 -200   # spawn in another world at coords
/fpp despawn --all                    # remove all bots
/fpp despawn --own                    # remove bots you spawned
/fpp despawn --random --count 3       # remove 3 random bots
/fpp move bot1 --to bot2                        # pathfind bot1 to bot2's location (follows live)
/fpp move bot1 --to Steve                       # pathfind bot1 to a real online player
/fpp move bot1 --coords 100 64 -200             # pathfind bot1 to coordinates
/fpp left-click bot1 --once           # break/attack the target once
/fpp right-click bot1 --repeat        # repeatedly use/interact with target
/fpp attack bot1 --once               # perform one basic attack swing
/fpp sneak bot1 toggle                # toggle sneak state
/fpp rename bot1 Miner                 # rename bot1's display name to "Miner"
/fpp stop bot1                        # stop all active tasks on bot1
/fpp freeze bot1 on                   # freeze bot1
/fpp inv bot1                         # open bot1 inventory
/fpp check --all                      # run all health checks
/fpp info bot1                        # show session history for bot1
```

## Notes

- `--all` on task commands sends the command to every bot the sender can administer.
- `--once` performs a single action and then stops.
- `--stop` cancels the command's activity for the specified bot(s).
- `spawn` creates exactly **one** bot per command — the bulk `[amount]` form and the bot-type tag were removed.
- `spawn` coordinates can be separate `x y z`, compact `x,y,z`, or relative values such as `~`, `~5`, and `~-3` (requires `fpp.spawn.coords`, now actually enforced).
- `--name` names are validated (1-16 chars, letters/numbers/underscores, badword filter) and rejected if a bot already uses them or a real player with that name is online. The mandatory "ʙᴏᴛ ʙʏ {owner}" nametag row still marks every bot as a bot regardless of its name.
- `/fpp move` is pathfinding-only: `--to <bot|player>` (follows the target live if it keeps moving) or `--coords <x> <y> <z> [world]`, backed by the core Pathetic-powered pathfinding engine. Directional raw-input movement was removed.
- `/fpp attack` is a basic swing/attack command. Rich PVE combat (mob targeting, range, priority, pathfinding-linked chasing) is configured per bot in its settings GUI under `🗡 ᴘᴠᴇ`.
- `left-click` and `right-click` are the core click automation commands; older mine/use/place-style commands were removed.
- **Precise aim & vantage:** click commands aim at the exact point you were looking at (not the block-face centre). If the target is out of reach, the bot walks to a spot it can reach it from — preferring your own standing location (a vantage the target is provably aim-able from) before searching around the target. `right-click` only activates a button/lever when the bot actually aims at the switch's hit box, not the block it's mounted on.
- **One action at a time:** starting any task (`move`, `find`, `left-click`, `right-click`, `attack`) stops the bot's other tasks first — bots don't multitask. Background PVE yields while a manual task runs and re-engages afterward.
- **Auto-eat interrupts:** when a bot gets hungry it pauses its current task, eats, then resumes exactly where it left off. Configure it per bot in the settings GUI under `🍖 ᴀᴜᴛᴏ-ᴇᴀᴛ` (toggle, hunger threshold, and an allowed-food picker).
- All core sub-commands use `--flag` style only (no bare-word duplicates like `list`/`enable`/`toggle` as an alternative spelling of a `--flag`).
