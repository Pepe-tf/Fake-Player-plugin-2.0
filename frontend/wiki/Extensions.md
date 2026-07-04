# Extensions

> **The extension system has been removed.** As of v2.0.0, FakePlayerPlugin does not load external
> extensions: `plugins/FakePlayerPlugin/extensions/` is never scanned or created, dropping a JAR
> there does nothing, and the `/fpp extension` command no longer exists. All previously
> extension-owned features that survived are now built into core.

## What happened to extension features?

| Former extension feature | Where it lives now |
|--------------------------|--------------------|
| Pathfinding engine | **Core** — Pathetic-backed A* engine drives `/fpp move`, `/fpp find`, storage trips, and PVE chasing |
| Skins | **Core** — rarity-based skin pools (`plugins/FakePlayerPlugin/skins/`), MineSkin signing, slim/classic detection |
| PVE / smart attack | **Core** — `PveController`, configured per bot in the `🗡 ᴘᴠᴇ` settings category |
| Tool auto-equip (find) | **Core** — `/fpp find` picks the best tool from the bot's inventory |
| Chat / AI chat / personality | **Removed** — bots are fully silent in chat by design |
| LuckPerms integration | **Removed** |
| Dynamic name pools / renaming | **Removed** — bots are sequentially named (`bot`, `bot2`, …) or custom-named via `/fpp spawn --name` |
| Player spoofing (`fpp-spoof`) | **Removed** — impersonation capability was deliberately deleted |

## For developers

The internal API (`FppApi`, `FppBot`, the `api.event.*` classes) still exists because **core itself
uses it** — events like `FppBotAttackEvent`, `FppBotSettingChangeEvent`, and
`FppBotBlockBreakEvent` fire normally and can be listened to from any ordinary Bukkit plugin.
There is no supported way to load code *into* FPP; interact with bots from your own plugin via
these Bukkit events instead.

`/fpp check` reports "0 extension(s) loaded" — that is expected, not an error.
