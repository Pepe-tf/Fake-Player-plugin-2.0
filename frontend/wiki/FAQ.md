# FAQ & Troubleshooting

## General

### Q: What server software is supported?
**A:** Paper/Purpur 1.21+ (up to 1.21.11) and Folia 1.21+. FPP has full Folia support with region-threaded bot spawning.

### Q: Does it work on Spigot or CraftBukkit?
**A:** No. FPP uses Paper-specific APIs and NMS Mojang-mapped classes.

### Q: What Java version do I need?
**A:** JDK 21+ for both the server and for building from source.

### Q: Can I use this on a server with ViaVersion?
**A:** Yes, but the server itself must be Paper 1.21+. ViaVersion only affects client versions.

### Q: Why is there so much debug spam on startup?
**A:** In v1.6.6.12.2, license verification runs silently by default. If you see debug messages, check `plugins/FakePlayerPlugin/debug.yml` and ensure all categories are set to `false`, or use `/fpp settings` → **🐛 ᴅᴇʙᴜɢ** to toggle them interactively. Run `/fpp reload` after manual edits.

### Q: Can I see debug output in-game instead of the console?
**A:** Yes. Enable `debug-chat: true` in `debug.yml` (or via `/fpp settings` → Debug). All debug output will be sent to online players with `fpp.op` or `fpp.notify` as chat messages.

### Q: License verification fails on startup.
**A:** The plugin requires internet access to verify the license from `app.lukittu.com`. If your server is offline, the plugin will run in limited mode. Check firewall rules and ensure outbound HTTPS (port 443) is allowed.

## Bots & Spawning

### Q: Bots are not showing in the tab list.
**A:** Verify no other plugin is overriding tab list packets. The `fpp-list` extension handles tab-list team management if installed.

### Q: Bots appear but have no skin.
**A:** Check `config.yml` skin settings. If `skin.mode` is `none`, skins are disabled. Set to `player` or `random`. The user-facing `/fpp skin` command is provided by the `fpp-skin` extension. The Mojang API can also rate-limit; try again later.

### Q: Spawn cooldown is blocking players.
**A:** Set `spawn-cooldown: 0` in `config.yml` or grant `fpp.bypass.cooldown`.

### Q: "Max bots reached" but I have fewer than the limit.
**A:** The limit is both global (`limits.max-bots`) and personal (`fpp.spawn.limit.N`). Check both.

## Tasks & Pathfinding

### Q: What's the difference between core `/fpp attack` and extension combat?
**A:** Core `/fpp attack` is a basic swing/attack command only (`--once`, `--stop`). Rich PvE/PvP combat with hunting, mob targeting, priority, range, and movement is extension-owned (e.g., `fpp-spoof` or other combat extensions).

### Q: Can I make bots pathfind to coordinates or follow players?
**A:** Core `/fpp move` is directional input only (`--direction forward|backward|left|right`). Pathfinding to coordinates, roaming, and following players are extension-owned behaviors.

### Q: Bot is stuck and won't move.
**A:** Try `/fpp stop <bot>` then re-issue the task. Bots may also get stuck in unloaded chunks; chunk-loading helps but is not guaranteed.

## Database

### Q: Can I use SQLite for a network setup?
**A:** No. SQLite is local-only. Use MySQL for multi-server setups.

### Q: Database connection fails on startup.
**A:** Verify credentials, firewall rules, and that the MySQL user has CREATE/ALTER permissions (schema migrations need them).

## Extensions

### Q: Where do I put extension JARs?
**A:** `plugins/FakePlayerPlugin/extensions/`. Create the folder if it doesn't exist, then `/fpp reload`.

### Q: Where are the old spoof/chat/ping/skin features?
**A:** They are first-party extensions now. Install the relevant individual jar or `fpp-spoof.jar` from the first-party `fpp-extensions` build.

### Q: Why do some config keys (fake-chat, swap, peak-hours, ping) not do anything?
**A:** Those systems are owned by first-party extensions such as `fpp-chat`, `fpp-swap`, `fpp-peaks`, and `fpp-ping`. Check the extension's own config under `plugins/FakePlayerPlugin/extensions/<extension-name>/config.yml`.

## Performance

### Q: Server lag with many bots.
**A:**
- Lower `chunk-loading.radius` or set `mass-disable-threshold` lower
- Reduce `head-ai.tick-rate`
- Increase `performance.position-sync-distance` (or set to `128`)
- Reduce bot count or spawn in batches

## Building

### Q: Build fails with "cannot find symbol" for NMS classes.
**A:** Ensure `libs/paper-1.21.11-mojang-mapped.jar` exists. This is a system-scoped dependency; the build cannot proceed without it.

### Q: `velocity-companion` or `bungee-companion` build fails.
**A:** These directories are `.gitignored` and may not exist. Only build them if you have the companion source.

## Fall Damage

### Q: Bots take fall damage even with `body.damageable: false`.
**A:** `body.damageable` only controls player/entity damage. Fall damage is governed by `combat.fall-damage.enabled` and is independent. Set `combat.fall-damage.enabled: false` to disable fall damage entirely.
