# Commands

All commands are prefixed with `/fpp` (aliases: `fakeplayer`, `fp`).

## Core Commands

| Command | Usage | Description | Permission |
|---------|-------|-------------|------------|
| **spawn** | `[--name <name>]` | Spawn **one** fake player bot per command at your own location — auto-named `bot`, `bot2`, … or custom-named via `--name`. In-game only (no console) | `fpp.spawn` (admin) / `fpp.spawn.user` (user) |
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
| **rent** | `buy <hours> \| extend <bot> <hours> \| info [bot] \| give <player> <bot\|--new> <hours> \| clear <bot>` | Rent a bot with real economy currency, billed per hour — see [Economy](Economy) | `fpp.rent` (+ `fpp.rent.info` / `fpp.rent.give`) |
| **auth** | `on\|off\|status [bot]\|reset <bot>\|setpassword <bot> <password>` | Manage bot auto-register/login against an installed login plugin — see [Configuration](Configuration) | `fpp.auth` |
| **perf** | `check\|top\|report\|report stop\|history\|spark` | Performance dashboard, history, benchmark reports, and Spark CPU profiler | `fpp.perf` |
| **help** | `[page]` | Shows the command help menu | `fpp.help` |

## Usage Examples

```
/fpp spawn                            # spawn one auto-named bot at your location
/fpp spawn --name Miner               # spawn one bot with a custom name
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
/fpp rent buy 4                       # spawn a new rented bot for 4 hours
/fpp rent extend bot1 4               # add 4 more hours to bot1
/fpp rent info                        # show remaining time on your rented bots
/fpp rent give Steve --new 4          # (console/admin) grant Steve a new 4h bot, no charge
/fpp auth on                          # enable bot auto-register/login
/fpp auth status bot1                 # check whether bot1 has a remembered password
/fpp auth reset bot1                  # forget bot1's password — it registers fresh next join
```

## Notes

- `--all` on task commands sends the command to every bot the sender can administer.
- `--once` performs a single action and then stops.
- `--stop` cancels the command's activity for the specified bot(s).
- `spawn` creates exactly **one** bot per command — the bulk `[amount]` form and the bot-type tag were removed.
- `spawn` is in-game only (a real player sender is required) and always spawns at your own location — there is no console spawning and no world/coordinate targeting.
- `--name` names are validated (1-16 chars, letters/numbers/underscores, badword filter) and rejected if a bot already uses them or a real player with that name is online. The mandatory "ʙᴏᴛ ʙʏ {owner}" nametag row still marks every bot as a bot regardless of its name.
- `/fpp move` is pathfinding-only: `--to <bot|player>` (follows the target live if it keeps moving) or `--coords <x> <y> <z> [world]`, backed by the core Pathetic-powered pathfinding engine. Directional raw-input movement was removed.
- `/fpp attack` is a basic swing/attack command. Rich PVE combat (mob targeting, range, priority, pathfinding-linked chasing) is configured per bot in its settings GUI under `🗡 ᴘᴠᴇ`.
- `left-click` and `right-click` are the core click automation commands; older mine/use/place-style commands were removed.
- **Precise aim & vantage:** click commands aim at the exact point you were looking at (not the block-face centre). If the target is out of reach, the bot walks to a spot it can reach it from — preferring your own standing location (a vantage the target is provably aim-able from) before searching around the target. `right-click` only activates a button/lever when the bot actually aims at the switch's hit box, not the block it's mounted on.
- **Configurable pacing:** the tick interval between actions during `--repeat`/`--hold` is set server-wide by `left-click.interval-ticks`/`right-click.interval-ticks` in `config.yml` (see [Configuration](Configuration)), and each bot can override its own value in the settings GUI (`⚙ ɢᴇɴᴇʀᴀʟ` → ʟᴇꜰᴛ/ʀɪɢʜᴛ-ᴄʟɪᴄᴋ ɪɴᴛᴇʀᴠᴀʟ, 1–40 ticks). Entity attacks during `left-click` are unaffected — those are still paced solely by the held weapon's real attack-speed cooldown.
- **Multitasking:** starting a new task no longer stops the bot's other running tasks — `move`, `find`, `left-click`, `right-click`, `attack`, and PVE auto-combat can all be active on the same bot at once. A bot still has only one body, so movement is arbitrated by priority (`move` > a hand-action's walk-to-reach > background movement like PVE's chase) rather than by cancellation; whichever loses out just declines gracefully instead of hijacking the bot mid-walk. Two hand-actions aimed at two different targets at once will still visibly alternate the bot's aim between them each tick — that's a real single-body limit, not a bug.
- **Auto-eat runs in parallel:** eating from the off-hand (the default/preferred source) no longer pauses anything at all — mining, moving, and combat keep going untouched. Only the main-hand fallback (no off-hand food available) still briefly pauses hand-actions, since it borrows the main hand itself for the eat animation.
- All core sub-commands use `--flag` style only (no bare-word duplicates like `list`/`enable`/`toggle` as an alternative spelling of a `--flag`).
- **Auth**: off by default (`auth.enabled: false` in `config.yml`, or `/fpp auth on`). A bot stands still and doesn't turn its head from join until its register/login outcome is known, exactly like a real not-yet-authenticated player. See [Configuration](Configuration) for the full config reference.
