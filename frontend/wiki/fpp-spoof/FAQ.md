# FPP Extensions FAQ

Frequently asked questions about FPP Extensions.

## Table of Contents

- [General Questions](#general-questions)
- [Installation & Setup](#installation--setup)
- [Extensions](#extensions)
- [Troubleshooting](#troubleshooting)
- [Performance](#performance)

---

## General Questions

### What are FPP Extensions?

FPP Extensions are modular add-ons that extend the functionality of the base Fake Player Plugin (FPP). They provide additional features like AI chat, skin management, ping spoofing, waypoint systems, and more.

### Are FPP Extensions official?

No, FPP Extensions are community-developed add-ons created by third-party developers. They use the official FPP Extension API but are not part of the core FPP plugin.

### Do I need FPP Extensions?

That depends on your needs. The base FPP plugin provides core bot functionality. Extensions add optional features like:
- AI-powered conversations
- Advanced skin management
- Ping spoofing
- Waypoint systems
- Group management

If you need these features, install the extensions. If not, the base plugin works fine on its own.

### Are extensions free?

Yes, FPP Extensions are free and open-source under the MIT License.

### What version of FPP do I need?

FPP Extensions 1.1.0 requires **Fake Player Plugin 1.6.6.12.1 or higher**.

### Do extensions work on Spigot?

Yes, but **Paper is recommended** for better performance and compatibility. Minimum requirement is Spigot/Paper 1.21+.

---

## Installation & Setup

### How do I install FPP Extensions?

1. Download `fpp-spoof-1.1.0-all.jar`
2. Place it in `plugins/FakePlayerPlugin/extensions/`
3. Restart your server
4. Run `/fpp reload` to verify installation

See the [Getting Started Guide](Getting-Started) for detailed instructions.

### Can I install individual extensions?

Yes! You can build and install extensions individually if you only need specific features.

Example:
```bash
./gradlew :fpp-ping:build --no-daemon
```

Then copy `fpp-ping-1.1.0.jar` to your extensions folder.

### Where are extension configs stored?

Configuration files are generated in:
```
plugins/FakePlayerPlugin/extensions/<extension-name>/config.yml
```

Each extension has its own config folder.

### How do I update extensions?

1. Stop your server
2. Replace the old JAR file with the new version
3. Start your server
4. Check configs for new settings (old configs are preserved)

### Do I need to delete old configs when updating?

No! FPP Extensions automatically merge new config keys with your existing configs. Your settings are preserved.

However, if you experience issues after updating, you can delete the old config and let it regenerate.

### Can I use extensions without LuckPerms?

Yes! LuckPerms is optional. Extensions that integrate with LuckPerms (like fpp-luckperms and fpp-groups) will still work, but LuckPerms-specific features won't be available.

---

## Extensions

### Which extension should I install first?

Recommended starting extensions:

1. **fpp-ping** - Easy to use, immediate visual feedback
2. **fpp-skin** - Customize bot appearances
3. **fpp-chat** - Makes bots feel more alive

Advanced extensions:
- **fpp-aichat** - Requires API key setup
- **fpp-waypoints** - More complex configuration
- **fpp-groups** - Best for large bot deployments

### How do I make bots talk with AI?

1. Install fpp-aichat extension
2. Get an API key from OpenAI or another provider
3. Edit `fpp-aichat/config.yml` with your API key
4. Set a personality for your bots
5. Bots will automatically respond to nearby chat

See the [Extensions Guide](Extensions#fpp-aichat) for detailed setup.

### Can bots have custom skins?

Yes! Use the **fpp-skin** extension:

```
/fpp skin <bot> --skin <url>
```

Or set random skins:
```
/fpp skin <bot> --random
```

Skins are fetched from MCHead or NameMC automatically.

### How do I make bots look like they have lag?

Use the **fpp-ping** extension to spoof ping values:

```
/fpp ping <bot> --ping 200
```

Or random ping:
```
/fpp ping <bot> --random
```

This only affects the tab list display, not actual bot performance.

### Can I make bots patrol an area?

Yes! Use the **fpp-waypoints** extension:

1. Create waypoints: `/fpp waypoint create point1`
2. Add to patrol: `/fpp waypoint patrol <bot> add point1`
3. Start patrol: `/fpp waypoint patrol <bot> start`

Bots will continuously walk the patrol route.

### Do extensions conflict with each other?

Generally no. Extensions are designed to work together. However:

- **fpp-list** and **LuckPerms** can conflict if both modify tab list
  - Solution: Set `bot-tab-list.enabled: false` in fpp-list config
- **fpp-nametag** and **LuckPerms** both manage prefixes/suffixes
  - Solution: Use LuckPerms integration in fpp-nametag config

### Can I use extensions on a proxy network?

Extensions work on individual servers. For BungeeCord/Velocity:

- Install FPP and extensions on each backend server
- Extensions don't sync across servers automatically
- Consider using a database for shared bot data

See the [Proxy Support](../Proxy-Support) guide for more details.

---

## Troubleshooting

### Extensions not loading

**Problem:** Extensions don't appear after installation

**Solutions:**
1. Verify FPP version is 1.6.6.12.1 or higher
2. Check server logs for error messages
3. Ensure JAR is in `plugins/FakePlayerPlugin/extensions/` (not `plugins/`)
4. Run `/fpp reload` and check for errors
5. Try a full server restart

### Commands not working

**Problem:** Extension commands return "unknown command"

**Solutions:**
1. Check you have the required permissions
2. Verify the extension is enabled in its config.yml
3. Run `/fpp reload` after config changes
4. Check for typos in command syntax
5. Use tab completion to see available commands

### Bots not chatting

**Problem:** Bots aren't sending chat messages

**Solutions:**
1. Enable chat in `fpp-chat/config.yml`: `enabled: true`
2. Check `chat-probability` is not 0
3. Ensure players are within `chat-radius`
4. Verify bots aren't on cooldown
5. Check console for chat-related errors

### API errors with AI chat

**Problem:** fpp-aichat shows API errors

**Solutions:**
1. Verify API key is correct in config
2. Check API key has available credits
3. Ensure `api-endpoint` URL is correct
4. Check server can reach the API (firewall rules)
5. Try a different model (e.g., `gpt-3.5-turbo` instead of `gpt-4`)

### Skins not loading

**Problem:** Bot skins appear as Steve/Alex

**Solutions:**
1. Check `fpp-skin/config.yml` - ensure `enabled: true`
2. Verify skin source (MCHead/NameMC) is accessible
3. Try a different skin URL
4. Check server has internet access
5. Clear skin cache: delete `fpp-skin/cache/` folder

### Config changes not applying

**Problem:** Edited configs don't take effect

**Solutions:**
1. Run `/fpp reload` after editing
2. Verify YAML syntax (indentation matters!)
3. Check you edited the correct config file
4. Restart server for full reload
5. Check server logs for config errors

### Permission issues

**Problem:** "You don't have permission" errors

**Solutions:**
1. Check permission nodes in your permission plugin
2. Use `/lp user <player> permission check fpp.ping` to verify
3. Ensure permission plugin is loaded before FPP
4. Try giving player op temporarily to test
5. Check for negated permissions (e.g., `-fpp.ping`)

---

## Performance

### Do extensions cause lag?

Minimal impact if configured properly:

- **Low impact:** fpp-ping, fpp-skin, fpp-peaks
- **Medium impact:** fpp-chat, fpp-waypoints, fpp-groups
- **Higher impact:** fpp-aichat (API calls), fpp-list (tab updates)

### How to reduce extension lag?

**General tips:**
1. Increase cooldowns and delays
2. Reduce update intervals
3. Disable unused extensions
4. Use individual extensions instead of full pack

**Specific optimizations:**

fpp-chat:
```yaml
chat-cooldown-ms: 5000  # Increase from 3000
chat-probability: 0.2   # Reduce from 0.5
```

fpp-waypoints:
```yaml
break-blocks: false     # Disable block breaking
place-blocks: false     # Disable block placing
```

fpp-aichat:
```yaml
response-delay-ms: 2000  # Add delay between responses
max-context-length: 5    # Reduce context size
```

### How many bots can I run with extensions?

Depends on your server:

- **Small server (2-4 GB RAM):** 10-20 bots with basic extensions
- **Medium server (4-8 GB RAM):** 20-50 bots with most extensions
- **Large server (8+ GB RAM):** 50+ bots with all extensions

Monitor performance and adjust accordingly.

### Memory usage?

Approximate memory per bot with extensions:
- Base FPP: ~50 MB per bot
- With extensions: ~60-80 MB per bot
- fpp-aichat adds ~10 MB overhead

### CPU usage?

Extensions add minimal CPU overhead:
- Tick handlers: <1% per bot
- Chat/AI: Spikes during activity
- Pathfinding: Varies with complexity

Use `/fpp peaks` to monitor server performance.

---

## Still Need Help?

### Getting Support

1. **Check the docs:** Most issues are covered in this wiki
2. **Search existing issues:** https://github.com/yourusername/fpp-extensions/issues
3. **Join Discord:** https://discord.gg/WRvfmV24Hh
4. **Create an issue:** Include server logs and FPP version

### What to Include in Bug Reports

- FPP Extensions version
- FPP version
- Server software (Paper/Spigot) and version
- List of installed extensions
- Steps to reproduce the issue
- Expected vs actual behavior
- Server logs (use pastebin.com or similar)

### Community Resources

- **Discord:** https://discord.gg/WRvfmV24Hh
- **GitHub:** https://github.com/yourusername/fpp-extensions
- **Wiki:** https://github.com/yourusername/fpp-extensions/wiki
- **Spigot Page:** (if applicable)

---

## Back to Top

- [Home](Home)
- [Getting Started](Getting-Started)
- [Extensions](Extensions)
- [Commands](Commands)
- [Configuration](Configuration)
- [Permissions](Permissions)
