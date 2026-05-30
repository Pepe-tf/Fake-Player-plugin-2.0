# fpp-nametag - NameTag Integration

NameTag plugin integration for FPP bots — nicknames, skins, and display names.

## Overview

fpp-nametag integrates FPP bots with the NameTag plugin, providing seamless nickname, skin, and display name management. It prevents conflicts between bot names and real players, syncs NameTag nicknames as bot renames, and applies cached skins from NameTag.

## Dependency

- **Required:** FPP Core
- **Soft Dependency:** NameTag plugin (optional — enhances functionality when present)

## How It Works

When the NameTag plugin is present, fpp-nametag:

1. **Isolates Bots**: Clears bot names from NameTag's cache on spawn, preventing collisions
2. **Blocks Conflicts**: Prevents real players from having the same nick as a bot
3. **Syncs Nicks**: Optionally applies NameTag nicknames as FPP bot renames
4. **Applies Skins**: Reads skin data from NameTag cache for bot skins
5. **Refreshes Display Names**: Keeps bot display names in sync

## Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-nametag/config.yml`

```yaml
enabled: true

nametag:
  block-nick-conflicts: true
  bot-isolation: true
  sync-nick-as-rename: false
  refresh-display-names: true
```

## Key Features

### Bot Isolation (bot-isolation: true)

When a bot spawns, the extension removes it from NameTag's cache. This prevents:
- NameTag from trying to manage the bot's display
- Conflicts with real player nicknames
- Duplicate entries in NameTag's database

### Nick Conflict Blocking (block-nick-conflicts: true)

Prevents real players from taking a nick that matches a bot name. If a player tries to nick themselves to "Bot1" and Bot1 exists, NameTag will reject it.

### Nick Sync (sync-nick-as-rename: false)

When enabled, if NameTag assigns a nickname to a bot, fpp-nametag will apply it as an FPP rename. This keeps both systems in sync.

### Display Name Refresh

Periodically refreshes bot display names to ensure they match the NameTag format.

## Permission Model

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.nametag` | Access nametag features | op |

## Use Cases

- **Cosmetic Servers**: Bots with custom nicknames matching the server theme
- **Roleplay Servers**: Bots with character-appropriate display names
- **Staff Bots**: Colored names matching staff naming conventions
- **Event Bots**: Temporary bots with event-specific names

## Integration Details

### NameTag Service

The extension registers `FppNameTagService` via FPP's service registry. Other extensions can access it:

```java
FppNameTagService service = api.getService(FppNameTagService.class);
if (service != null) {
    service.applyNickname(bot, "NewName");
}
```

### Reflection API

fpp-nametag uses reflection to interact with NameTag's internal API. This means:
- No compile-time dependency on NameTag
- Graceful fallback if NameTag is not installed
- Compatible with multiple NameTag versions

## Behavior Matrix

| Setting | true | false |
|---------|------|-------|
| `bot-isolation` | Bots removed from NameTag cache | Bots visible in NameTag management |
| `block-nick-conflicts` | Players can't nick as bot names | Players can duplicate bot names |
| `sync-nick-as-rename` | NameTag nick → FPP rename | Independent naming systems |
| `refresh-display-names` | Continuous display name sync | Names set once on spawn |

## Examples

### With NameTag Installed

1. A staff member uses NameTag to set a bot's prefix: `/nick Bot1 &6[VIP] &eBot1`
2. fpp-nametag ensures the bot's name doesn't conflict
3. When `sync-nick-as-rename: true`, the FPP bot is renamed to match

### Without NameTag

The extension loads but performs no operations. All features are gracefully disabled.

## Troubleshooting

### NameTag Features Not Working

- Verify NameTag plugin is installed and loaded
- Check `enabled: true` in config
- Ensure FPP loaded before NameTag
- Check server logs for reflection errors

### Bot Names Not Showing

- Verify `nametag.refresh-display-names: true`
- Check for conflicts with other scoreboard plugins
- Run `/fpp reload` after NameTag changes

### Nick Conflict Errors

- If players can't nick, check `block-nick-conflicts` setting
- Verify no bot has the same name as the desired nick

## Technical Details

- **Priority**: 100 (default)
- **Soft Dependency**: NameTag plugin (reflection-based integration)
- **Service**: Registers `FppNameTagService` in FPP service registry
- **Sync Method**: Uses reflection to call internal NameTag API methods
