# fpp-command - Command Extension

Execute commands and bind right-click actions to your FPP bots.

## Overview

fpp-command allows you to make bots execute Minecraft commands, either directly or triggered by player interaction. This enables a wide range of functionality — from simple announcements to complex administrative tasks performed through bots.

## How It Works

When you execute `/fpp cmd <bot> <command>`, the extension uses the FPP API's `runAsBot()` method to execute the command with the bot as the executor. For right-click binding, one command is stored on the bot and executed when a player interacts with it.

## Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-command/config.yml`

```yaml
enabled: true
permissions:
  command: fpp.cmd.admin
  legacy: fpp.cmd
```

## Commands

### Execute Command

```
/fpp cmd <bot> <command>       # Execute a command as the bot
```

Makes the bot execute any command as if the bot typed it:

```
/fpp cmd Bot1 say Hello everyone!
/fpp cmd Bot2 give @p diamond 1
/fpp cmd BuilderBot setblock ~ ~ ~ stone
```

### Right-Click Commands

Bind commands to right-click interactions with the bot:

```
/fpp cmd <bot> --add <command>     # Set right-click command
/fpp cmd <bot> --clear             # Clear the right-click command
/fpp cmd <bot> --show              # Show assigned right-click command
```

When a player right-clicks a bot with a bound command, the command executes as the bot.

## Key Features

- **Direct Execution**: Run any command as a bot
- **Right-Click Binding**: Assign one command activated by player interaction
- **Permission-Based**: Access controlled via permission nodes
- **Legacy Support**: Backward-compatible permission nodes
- **Real-Time Execution**: Commands execute immediately with no delay

## Permission Model

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.cmd.admin` | Execute commands as bots | op |
| `fpp.cmd` | Legacy permission alias | op |

## Use Cases

- **Automated Announcements**: Bots that broadcast messages
- **Teleport Hubs**: Right-click to teleport or show menus
- **NPC Shops**: Bots that run trade commands on interaction
- **Server Utilities**: Bots that execute administrative commands
- **Welcome Bots**: Right-click for server info or rules
- **Command Chains**: Sequenced commands through multiple bots

## Examples

### Teleport Bot

```
/fpp cmd WarpBot tp @p 100 64 200
```

Creates a bot that teleports players to a destination.

### Info Bot

```
/fpp cmd InfoBot help
/fpp cmd InfoBot --add tell {player} Welcome to the server!
```

Bot that provides information when right-clicked.

### Shop Bot

```
/fpp cmd ShopBot --add give {player} diamond 1
```

Right-click to receive items from the bot.

### Broadcasting

```
/fpp cmd AnnouncerBot say Server maintenance in 10 minutes!
/fpp cmd AnnouncerBot say Please log out safely.
```

Bot announces server messages.

## Security Considerations

> **Important**: The command executes with the bot's permissions, not the player's. If a bot has operator status, any player who can use `/fpp cmd` can run operator commands. Restrict the `fpp.cmd.admin` permission to trusted admins only.

### Best Practices

- Use specific, limited bots for public-facing commands
- Avoid giving bots unnecessary permissions
- Monitor command execution logs
- Clear the right-click command when no longer needed

## Troubleshooting

### Command Not Executing

- Check bot is online and loaded
- Verify bot has permission to run the command
- Ensure `enabled: true` in config
- Check player has `fpp.cmd.admin` permission

### Right-Click Not Working

- Verify command was bound with `--add`
- Check for conflicting interaction handlers
- Ensure player has interaction permission

## Technical Details

- **API Method**: `FppApi.runAsBot(FppBot, String command)`
- **Priority**: 100 (default)
- **Dependencies**: Paper API 1.21+, FPP Core
