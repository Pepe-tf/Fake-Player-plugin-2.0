# FPP Extensions Configuration

Complete configuration reference for all FPP Extensions.

## Configuration Location

All extension configurations are stored in:

```
plugins/FakePlayerPlugin/extensions/<extension-name>/config.yml
```

Configuration files are automatically generated on first server startup after installing the extensions.

## Global Configuration Tips

1. **Edit configs when server is offline** to prevent overwrites
2. **Run `/fpp reload`** after making changes
3. **Backup configs** before major changes
4. **Use YAML syntax** correctly (indentation matters!)
5. **Check server logs** for configuration errors

---

## fpp-chat Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-chat/config.yml`

```yaml
# Enable or disable the chat extension
enabled: true

# Cooldown between bot chat messages (milliseconds)
chat-cooldown-ms: 3000

# Additional random delay variation (milliseconds)
chat-random-delay-ms: 2000

# Chat radius - bots only chat within this distance
chat-radius: 50

# Prefix added to bot chat messages
chat-prefix: ""

# Allow bots to chat without AI enabled
allow-chat-without-ai: true

# Messages bots will never say
blocked-messages:
  - "spam message"
  - "another blocked message"

# Chat probability (0.0 - 1.0)
# Lower = less frequent chat
chat-probability: 0.3
```

### Key Settings

| Setting | Description | Recommended |
|---------|-------------|-------------|
| `chat-cooldown-ms` | Minimum time between messages | 3000-5000 |
| `chat-random-delay-ms` | Adds randomness to prevent synchronized chat | 1000-3000 |
| `chat-radius` | Distance for chat visibility | 30-100 |
| `chat-probability` | How often bots attempt to chat | 0.2-0.5 |

---

## fpp-list Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-list/config.yml`

```yaml
# Enable or disable the list extension
enabled: true

# Control bot visibility in tab list
# Set to false if using LuckPerms to avoid conflicts
bot-tab-list.enabled: false

# Custom tab list header (MiniMessage format)
custom-header: "&6&lMy Server"

# Custom tab list footer (MiniMessage format)
custom-footer: "&7Welcome! &a&lOnline: %online%"

# Sort mode for player list
# Options: "alphabetical", "group", "group_then_alphabetical", "none"
sort-mode: "group_then_alphabetical"

# Group priorities (lower = higher in list)
group-priorities:
  owner: 1
  admin: 2
  moderator: 3
  vip: 4
  default: 5
```

### Important Notes

- **LuckPerms Compatibility:** Keep `bot-tab-list.enabled: false` if using LuckPerms
- **Header/Footer:** Uses MiniMessage formatting (`&` color codes)
- **Sort Mode:** Affects how players and bots are ordered in tab list

---

## fpp-ping Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-ping/config.yml`

```yaml
# Enable or disable the ping extension
enabled: true

# Default ping value for bots (milliseconds)
default-ping: 0

# Minimum random ping value (used with --random flag)
min-random-ping: 20

# Maximum random ping value (used with --random flag)
max-random-ping: 500

# Show ping in tab list
show-in-tab: true

# Ping display format
ping-format: "{ping}ms"
```

### Key Settings

| Setting | Description | Recommended |
|---------|-------------|-------------|
| `default-ping` | Base ping for all bots | 0-50 |
| `min-random-ping` | Minimum for random ping | 20-50 |
| `max-random-ping` | Maximum for random ping | 200-500 |

### Usage Examples

Realistic ping simulation:
```yaml
default-ping: 30
min-random-ping: 20
max-random-ping: 150
```

High ping for testing:
```yaml
default-ping: 200
min-random-ping: 100
max-random-ping: 500
```

---

## fpp-skin Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-skin/config.yml`

```yaml
# Enable or disable the skin extension
enabled: true

# Skin source provider
# Options: "mchead", "namemc"
skin-source: "mchead"

# Default skin URL (optional)
# Leave empty to use bot's default skin
default-skin: ""

# Allow custom skin URLs in commands
allow-custom-urls: true

# Cache skins locally to reduce API calls
cache-skins: true

# Cache expiration time (minutes)
cache-expiration-minutes: 60
```

### Skin Sources

| Source | Description |
|--------|-------------|
| `mchead` | MCHead.net - Fast, reliable |
| `namemc` | NameMC.com - Larger database |

### Custom Skin URLs

Must point to a valid PNG skin file:
- 64x64 or 64x32 pixels
- PNG format
- Accessible via direct URL

Example:
```yaml
default-skin: "https://example.com/skins/custom.png"
```

---

## fpp-command Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-command/config.yml`

```yaml
# Enable or disable the command extension
enabled: true

# Default command assigned to new bots
default-command: ""

# Cooldown between command executions (milliseconds)
command-cooldown-ms: 5000

# Require permission node to execute commands
require-permission: true

# Allowed commands (leave empty for all)
# Use * for all commands (not recommended)
allowed-commands:
  - "say"
  - "me"
  - "tell"
  - "msg"

# Blocked commands (always blocked)
blocked-commands:
  - "op"
  - "deop"
  - "stop"
  - "restart"
```

### Security Notes

- Always block administrative commands
- Use `allowed-commands` for whitelisting
- Set `require-permission: true` for security

---

## fpp-groups Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-groups/config.yml`

```yaml
# Enable or disable the groups extension
enabled: true

# Maximum number of groups allowed
max-groups: 10

# Maximum members per group
max-members-per-group: 50

# Allow groups to contain other groups (inheritance)
allow-nested-groups: false

# Default group permissions
default-permissions:
  - "fpp.chat"
  - "fpp.ping"

# Group-specific settings
group-settings:
  guards:
    permissions:
      - "fpp.command"
    auto-assign: false
  workers:
    permissions:
      - "fpp.chat"
    auto-assign: true
```

---

## fpp-luckperms Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-luckperms/config.yml`

```yaml
# Enable or disable LuckPerms integration
enabled: true

# Auto-assign group to new bots
auto-assign-group: "default"

# Sync LuckPerms permissions to bots
sync-permissions: true

# Use LuckPerms contexts
use-contexts: false

# Context settings
contexts:
  world: "world"
  server: "main"
```

### Important Notes

- Requires LuckPerms plugin installed
- Bots inherit permissions from LuckPerms groups
- Set `sync-permissions: true` for full integration

---

## fpp-nametag Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-nametag/config.yml`

```yaml
# Enable or disable the nametag extension
enabled: true

# Nametag format (supports placeholders)
format: "{prefix}{name}{suffix}"

# Default prefix for bots
default-prefix: ""

# Default suffix for bots
default-suffix: ""

# Use LuckPerms for prefix/suffix
use-luckperms: true

# Show health in nametag
show-health: false

# Health display format
health-format: " &c{health}/{max_health}"
```

### Placeholders

- `{prefix}` - Group prefix
- `{name}` - Bot name
- `{suffix}` - Group suffix
- `{health}` - Current health
- `{max_health}` - Maximum health

---

## fpp-peaks Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-peaks/config.yml`

```yaml
# Enable or disable the peaks extension
enabled: true

# Update interval for statistics (seconds)
update-interval-seconds: 5

# Show statistics in action bar
show-in-action-bar: false

# Show statistics in chat when command is run
show-in-chat: true

# Display format
format:
  tps: "&aTPS: {tps}"
  memory: "&bMemory: {used}/{max} MB"
  players: "&ePlayers: {online}/{max}"
  uptime: "&6Uptime: {uptime}"
```

---

## fpp-swap Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-swap/config.yml`

```yaml
# Enable or disable the swap extension
enabled: true

# Permission node for swap commands
permissions:
  command: fpp.swap

# Swap system settings
swap:
  # Master toggle for swap rotation
  enabled: false

  # Enable debug logging
  debug: false

  # Max bots offline at once (0 = unlimited)
  max-swapped-out: 0

  # Minimum bots to keep online
  min-online: 0

  # Try to reclaim original name on rejoin
  same-name-on-rejoin: true

  # Bot says farewell before leaving
  farewell-chat: true

  # Bot greets on return
  greeting-chat: true

  # Retry rejoin if it fails
  retry-rejoin: true

  # Delay before retry (seconds)
  retry-delay: 60

  # Session duration settings
  session:
    min: 60   # Shortest session (seconds)
    max: 300  # Longest session (seconds)

  # Absence duration settings
  absence:
    min: 30   # Shortest offline time (seconds)
    max: 120  # Longest offline time (seconds)
```

### Key Settings

| Setting | Description | Recommended |
|---------|-------------|-------------|
| `swap.enabled` | Master toggle | `true` to enable |
| `session.min` | Shortest bot session | 60-120s |
| `session.max` | Longest bot session | 300-600s |
| `absence.min` | Shortest offline period | 30-60s |
| `absence.max` | Longest offline period | 120-300s |
| `max-swapped-out` | Max offline bots at once | 3-5 |
| `min-online` | Minimum bots always online | 1-3 |
| `retry-rejoin` | Retry on failed rejoin | `true` |

### Personality Effects

Session multipliers by bot personality:
- **CASUAL**: 1.0x (average)
- **GRINDER**: 1.6x (long sessions)
- **SOCIAL**: 0.65x (short frequent visits)
- **LURKER**: 2.2x (very long sessions)
- **ACTIVE**: 0.45x (brief pop-ins)
- **SPORADIC**: 1.1x + random (unpredictable)

### Session Growth

Session length increases up to 40% over the first 5 swaps (8% per swap), simulating bots that gradually stay online longer.

---

## fpp-waypoints Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-waypoints/config.yml`

```yaml
# Enable or disable the waypoints extension
enabled: true

# Maximum waypoints allowed
max-waypoints: 100

# Patrol looping (repeat patrol route)
patrol-loop: true

# Patrol movement speed multiplier
patrol-speed: 1.0

# Pathfinding settings
avoid-water: false
avoid-lava: false
break-blocks: false
place-blocks: false

# Arrival distance (how close to count as "arrived")
arrival-distance: 2.0

# Recalculation distance (when to recalculate path)
recalc-distance: 3.5
```

### Pathfinding Notes

- `break-blocks` and `place-blocks` can cause lag if overused
- `avoid-water` and `avoid-lava` make pathfinding more cautious
- Lower `arrival-distance` = more precise stopping

---

## fpp-aichat Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-aichat/config.yml`

```yaml
# Enable or disable AI chat
enabled: true

# API provider
# Options: "openai", "anthropic", "local"
api-provider: "openai"

# API key for LLM service
api-key: "your-api-key-here"

# Model to use
model: "gpt-3.5-turbo"

# Bot personality preset
# Options: "friendly", "neutral", "rude", "custom"
personality: "friendly"

# Response delay (milliseconds)
response-delay-ms: 1000

# Maximum conversation context length
max-context-length: 10

# Custom personality prompt (if personality is "custom")
custom-personality-prompt: "You are a helpful Minecraft bot."

# API endpoint (for local models)
api-endpoint: "https://api.openai.com/v1/chat/completions"

# Timeout for API requests (seconds)
timeout-seconds: 30
```

### Security Notes

- **Never commit your API key** to version control
- Use environment variables for production servers
- Monitor API usage to avoid unexpected costs

### Environment Variable Usage

Instead of hardcoding the API key:

```yaml
api-key: "${OPENAI_API_KEY}"
```

Then set the environment variable:
```bash
export OPENAI_API_KEY="your-key-here"
```

---

## Configuration Reload

After editing any config file, reload extensions:

```
/fpp reload
```

Or restart the server for a full reload.

## Troubleshooting

### Config Not Applying

1. Verify you edited the correct file
2. Check YAML syntax (use a YAML validator)
3. Run `/fpp reload`
4. Check server logs for errors

### Config Reset on Restart

- FPP only overwrites configs if they're missing required keys
- Add a `config-version` field to prevent auto-replacement
- Backup configs before updates

### Invalid Configuration

If a config value is invalid, the extension will:
1. Log an error to console
2. Use the default value
3. Continue functioning

Check server logs for configuration warnings.
