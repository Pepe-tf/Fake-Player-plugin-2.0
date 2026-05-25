# fpp-chat - Chat Extension

Bot chat system with configurable messages, event-driven reactions, and bot-to-bot conversations.

## Overview

fpp-chat brings your bots to life with automated chat. Bots send contextually-aware messages based on in-game events — player joins, deaths, advancements, kills, and more. Bots can also chat among themselves, creating a living server atmosphere.

## How It Works

The extension uses a message pool system loaded from `bot-messages.yml`. When an event occurs (e.g., a player joins), BotChatAI selects an appropriate message from the pool, applies placeholder replacements, and broadcasts it as if the bot sent it.

## Message Pools

Messages are organized by category in `bot-messages.yml`:

| Pool | Trigger | Example |
|------|---------|---------|
| `greetings` | Player joins server | "Hey {player_name}, welcome back!" |
| `reactions` | Random interval | "Anyone else lagging?" |
| `questions` | Random interval | "Anyone want to trade?" |
| `gameplay-talk` | Random interval | "Found 12 diamonds in this cave" |
| `death-reactions` | Player dies | "RIP {player_name}" |
| `join-reactions` | Bot notices join | "Hey {player_name}!" |
| `leave-reactions` | Player leaves | "See ya, {player_name}" |
| `kill-reactions` | Player kills mob/player | "Nice kill, {player_name}!" |
| `advancement-reactions` | Player gets advancement | "GG on {advancement}!" |
| `first-join-reactions` | New player first join | "Welcome to the server!" |
| `player-chat-reactions` | Player chats nearby | "I know, right?" |
| `bot-to-bot-replies` | Another bot speaks | "Totally agree, {name}" |
| `keyword-reactions` | Keyword match | Custom responses |
| `burst-followups` | After initial message | Adds conversational depth |

### Supported Placeholders

Messages support these placeholders:

| Placeholder | Replaced With |
|-------------|---------------|
| `{name}` | Bot's own name |
| `{random_player}` | Random online player |
| `{player_name}` | Target player name |
| `{online}` | Online player count |
| `{world}` | Bot's current world |
| `{time}` | Current server time |
| `{biome}` | Bot's current biome |
| `{x}`, `{y}`, `{z}` | Bot's coordinates |
| `{server}` | Server name |
| `{date}` | Current date |
| `{day}` | In-game day |
| `{killer}` | Killer name (on death) |
| `{victim}` | Victim name (on kill) |
| `{advancement}` | Advancement name |
| `{level}` | Player experience level |

## Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-chat/config.yml`

```yaml
enabled: true

fake-chat:
  enabled: false
  debug: false
  require-player-online: true
  chance: 0.75

  interval:
    min: 5
    max: 10

  typing-delay: true
  activity-variation: true
  history-size: 5
  stagger-interval: 3
  burst-chance: 0.12
  burst-delay:
    min: 2
    max: 5

  bot-to-bot:
    enabled: true
    reply-chance: 0.35
    chain-chance: 0.40
    max-chain: 3
    cooldown: 8
    delay:
      min: 4
      max: 14

  reply-to-mentions: true
  mention-reply-chance: 0.65
  reply-delay:
    min: 2
    max: 8

  remote-format: "<yellow>{name}<dark_gray>: <white>{message}"

  event-triggers:
    enabled: true
    on-player-join:
      enabled: true
      chance: 0.40
      delay: { min: 2, max: 6 }
    on-death:
      enabled: true
      chance: 0.30
      delay: { min: 1, max: 4 }
    on-player-leave:
      enabled: true
      chance: 0.30
      delay: { min: 1, max: 4 }
    on-advancement:
      enabled: true
      chance: 0.45
      delay: { min: 1, max: 5 }
    on-first-join:
      enabled: true
      chance: 0.70
    on-kill:
      enabled: true
      chance: 0.35
      delay: { min: 1, max: 4 }
    on-high-level:
      enabled: true
      min-level: 30
      chance: 0.35
      delay: { min: 1, max: 5 }

  on-player-chat:
    enabled: false
    chance: 0.25
    max-bots: 1
    ignore-short: true
    ignore-commands: true
    mention-player: 0.50
    delay: { min: 2, max: 8 }

  keyword-reactions:
    enabled: false
    keywords: {}
```

## Commands

```
/fpp chat <bot> [on|off]        # Toggle chat for a bot
/fpp chat <bot> mute [duration] # Mute a bot for a duration
/fpp chat <bot> say <message>   # Force bot to say something
/fpp chat <bot> info            # Show bot chat status
/fpp chat all [on|off]          # Toggle chat for all bots
```

## Key Features

- **Event-Driven Chat**: Reacts to player joins, deaths, advancements, kills, level-ups, and more
- **Bot-to-Bot Conversations**: Bots can reply to and chain messages between themselves
- **Burst Messages**: Occasional multi-message conversations with staggered timing
- **Activity Variation**: Varies message timing to feel more natural
- **Typing Delay**: Simulates real typing speed
- **Mention Replies**: Responds when someone says the bot's name
- **Keyword Reactions**: Configurable keyword-triggered responses
- **Remote Chat Support**: Broadcasts to proxy (Velocity/BungeeCord) via remote format
- **Per-Bot Toggle**: Enable/disable chat per bot with optional timed mute
- **History Tracking**: Prevents repetitive messages

## Event Triggers

| Event | Default Chance | Description |
|-------|---------------|-------------|
| Player Join | 40% | Bot welcomes returning players |
| Player Death | 30% | Bot reacts to player deaths |
| Player Leave | 30% | Bot says goodbye |
| Advancement | 45% | Bot congratulates on advancement |
| First Join | 70% | Bot welcomes new players |
| Kill | 35% | Bot acknowledges player kills |
| High Level | 35% | Bot notices high-level players |
| Player Chat | 25% | Bot joins nearby conversations |

## Bot-to-Bot System

Bots can have full conversations with each other:

1. Bot A sends a message
2. Bot B (within range) has a 35% chance to reply
3. Bot A has a 40% chance to continue the chain
4. Maximum chain length of 3 messages
5. 8-second cooldown between bot-to-bot chains

## Permission Model

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.chat` | Use chat extension commands | op |

## Use Cases

- **Living World**: Bots create the illusion of an active, populated server
- **Welcome Committee**: New players are greeted by bots
- **Community Feel**: Bot conversations fill the chat with life
- **Roleplay Atmosphere**: Bots react to in-character events
- **Server Events**: Bots can hype up events and achievements

## Architecture

```
FppChatExtension (main)
├── BotMessageConfig — Loads and provides message pools
│   └── bot-messages.yml
└── BotChatAI — Core chat logic (implements Listener)
    ├── Event-driven scheduling
    ├── Bot-to-bot conversation management
    ├── Message placeholder resolution
    ├── Burst message system
    └── Remote (proxy) chat broadcasting
```

## Event Handling

| Event | Purpose |
|-------|---------|
| `PlayerJoinEvent` | Welcome messages |
| `PlayerDeathEvent` | Death reactions |
| `PlayerQuitEvent` | Farewell messages |
| `PlayerAdvancementDoneEvent` | Congratulatory messages |
| `AsyncPlayerChatEvent` | Nearby chat reactions |
| `EntityDeathEvent` | Kill reactions |
| `PlayerLevelChangeEvent` | Level-up notices |
| `FppBotSpawnEvent` | Initialize chat state |

## Troubleshooting

### Bots Not Chatting

- Check `fake-chat.enabled: true` in config
- Verify `enabled: true` at the top of config
- Ensure bots are online and active
- Check each event trigger's `chance` is above 0
- Verify `interval.min` and `interval.max` are reasonable

### Repetitive Messages

- Increase `history-size` (default: 5)
- Add more messages to the pools in `bot-messages.yml`
- Adjust `activity-variation` settings
- Check for conflicting bot-to-bot loops

### Performance Issues

- Reduce `chance` values for high-frequency events
- Increase `interval.min` and `interval.max`
- Disable unnecessary event triggers
- Reduce `bot-to-bot.max-chain`

## Technical Details

- **Priority**: 100 (default)
- **Messages File**: `bot-messages.yml` (~1030 lines of messages)
- **Chat Format**: Adventure Component API with MiniMessage
- **Proxy Format**: Configurable remote format for Velocity/BungeeCord
