# FAQ & Troubleshooting

## General

### Q: What server software is supported?
**A:** Paper/Purpur 1.21+ (up to 1.21.11 and the year-based 26.1.x–26.2.x releases) and Folia. FPP has full Folia support with region-threaded bot spawning.

### Q: Does it work on Spigot or CraftBukkit?
**A:** No. FPP uses Paper-specific APIs and NMS Mojang-mapped classes.

### Q: What Java version do I need?
**A:** JDK 21+ for both the server and for building from source.

### Q: Can I use this on a server with ViaVersion?
**A:** Yes, but the server itself must be Paper 1.21+. ViaVersion only affects client versions.

### Q: How do I see what a bot is doing?
**A:** The nametag above every bot shows its live activity (idle / moving / mining / fighting /
eating / searching / sneaking …), updated twice a second. The `/fpp list` GUI shows the same activity per
bot, and the pathfinding particle debug view (per-bot settings → 🧭 ᴘᴀᴛʜꜰɪɴᴅɪɴɢ → ꜱʜᴏᴡ ᴘᴀᴛʜ, or
globally via `/fpp settings` → 🐛 ᴅᴇʙᴜɢ → ꜱʜᴏᴡ ᴀʟʟ ᴘᴀᴛʜꜱ) renders each bot's route.

### Q: Can I see debug output in-game instead of the console?
**A:** Yes. Enable `debug-chat: true` in `debug.yml` (or via `/fpp settings` → Debug). All debug output will be sent to online players with `fpp.op` or `fpp.notify` as chat messages.

## Bots & Spawning

### Q: Why don't bots show in the tab list or server-list player count?
**A:** By design. Bots are unlisted in the tab, subtracted from the server-list ping count, and
removed from its hover sample. They also earn no advancements and send no join/leave/death chat
messages.

### Q: Bots appear but have no skin.
**A:** Skins come from the rarity pools in `plugins/FakePlayerPlugin/skins/` (`main_skin.txt` +
`1-<N>%.txt` files of NameMC URLs). A fresh skin needs one MineSkin signing round-trip (a few
seconds) the first time it's ever used; after that it's cached forever in `data/skin-cache.yml`.
Enable the `skin-pool` debug topic to trace the pipeline. To disable skins set
`skin.rare-pools: false`.

### Q: How do bot UUIDs work?
**A:** Deterministic and name-derived with a recognizable `fb07` prefix: `bot` →
`fb070000-0000-0000-0000-000000000001`, `bot2` → `…-000000000002`; custom names hash into the low
bits. They can never collide with real accounts.

### Q: Spawn cooldown is blocking players.
**A:** Set `spawn-cooldown: 0` in `config.yml` or grant `fpp.bypass.cooldown`.

### Q: "Max bots reached" but I have fewer than the limit.
**A:** The limit is both global (`limits.max-bots`) and personal (`fpp.spawn.limit.N`). Check both.

### Q: Can I spawn several bots at once?
**A:** No — `/fpp spawn` intentionally creates exactly one bot per command (auto-named, or
`--name <name>` for a custom name).

## Tasks, Combat & Pathfinding

### Q: How do I make a bot fight mobs?
**A:** Open its settings (shift+right-click the bot or click it in `/fpp list`) → `🗡 ᴘᴠᴇ` → set
**ꜱᴍᴀʀᴛ ᴀᴛᴛᴀᴄᴋ** to "on" (attack in reach) or "on with movement" (chase via pathfinding). Pick
target mob types, detect range, and priority there too. The **ᴘᴠᴇ ꜱᴛᴀᴛᴜꜱ** tile shows the live
state (off / scanning / fighting).

### Q: Can I make bots pathfind to coordinates or follow players?
**A:** Yes — `/fpp move <bot> --to <bot|player>` follows a target live, and
`/fpp move <bot> --coords <x> <y> <z> [world]` walks to a fixed point. Both are backed by the
core Pathetic A* engine.

### Q: Bot is stuck and won't move.
**A:** The pathfinder auto-recalculates when stuck, and abandons a target after a few fruitless
cycles (`pathfinding.max-stuck-cycles`). Enable the `pathfinding` debug topic for grep-friendly
`event=STUCK/PATH_REJECTED/…` console lines explaining exactly what happened. `/fpp stop <bot>`
cancels everything manually.

### Q: How does `/fpp find` work?
**A:** Search → path → mine loops: it auto-equips the best tool from the bot's inventory, gives up
on unreachable/unbreakable blocks instead of looping, and when inventory runs low it deposits into
the bot's nearest registered storage (`/fpp storage`) before resuming.

### Q: Can a bot do two things at once (mine and fight, walk and use)?
**A:** No — bots run **one task at a time** by design. Starting a new task (`move` / `find` /
`left-click` / `right-click` / `attack`) stops whatever the bot was doing first. If a bot has PVE
enabled, it fights only while it isn't running one of those manual tasks, and re-engages once the
task finishes. Interrupts like auto-eat are the exception: they *pause* the current task and resume
it afterward rather than replacing it.

### Q: How do I make a bot eat automatically?
**A:** Open its settings → `🍖 ᴀᴜᴛᴏ-ᴇᴀᴛ`. Toggle **ᴀᴜᴛᴏ-ᴇᴀᴛ** on, set the **ʜᴜɴɢᴇʀ ᴛʜʀᴇꜱʜᴏʟᴅ**
(0-19; the bot eats at or below it), and open **ᴀʟʟᴏᴡᴇᴅ ꜰᴏᴏᴅꜱ** to pick which foods it may eat
(none selected = any food). When hungry the bot prefers food in its off-hand, then hotbar, then
inventory; it pauses its current task, eats, and switches back to what it was holding. The global
default threshold is `automation.auto-eat-threshold` in `config.yml`.

### Q: My bot flips levers / presses buttons just by looking at the wall they're on.
**A:** Fixed — the bot now must aim at the button/lever's actual hit box to trigger it, exactly like
a real player. Aiming at the block it's mounted on (or a corner of that face) no longer activates
the switch.

### Q: How does the bot decide where to stand for a click?
**A:** If the target is already in reach it clicks from where it is. If not, it walks to a spot it
can reach the target from — preferring **your own standing location** (since you just aimed at the
target from there, it's a provably reachable vantage) before falling back to searching around the
target itself.

## Database

### Q: Can I use SQLite for a network setup?
**A:** No. SQLite is local-only. Use MySQL for multi-server setups.

### Q: Database connection fails on startup.
**A:** Verify credentials, firewall rules, and that the MySQL user has CREATE/ALTER permissions (schema migrations need them).

## Performance

### Q: Server lag with many bots.
**A:**
- Lower `chunk-loading.radius` or set `mass-disable-threshold` lower
- Reduce `head-ai.tick-rate`
- Increase `performance.position-sync-distance` (or set to `128`)
- Reduce bot count or spawn in batches
- Use `/fpp perf top` / `/fpp perf report` to measure

## Building

### Q: How do I build from source?
**A:** JDK 21+ and `./gradlew clean shadowJar`. The paperweight dev bundle downloads automatically.

### Q: `velocity-companion` or `bungee-companion` build fails.
**A:** These directories are `.gitignored` and may not exist. Only build them if you have the companion source.

## Fall Damage

### Q: Bots take fall damage even with `body.damageable: false`.
**A:** `body.damageable` only controls player/entity damage. Fall damage is governed by `combat.fall-damage.enabled` and is independent. Set `combat.fall-damage.enabled: false` to disable fall damage entirely.
