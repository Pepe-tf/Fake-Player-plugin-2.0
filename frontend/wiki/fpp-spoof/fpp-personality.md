# fpp-personality - Bot Personality Extension

Shared bot personality, profiles, and long-lived identity data for FPP extensions.

## Overview

fpp-personality provides the implementation for the core plugin's shared profile API. It manages `BotProfile` objects that track each bot's personality type, activity level, chat frequency, friendships, rivalries, swap groups, and peak participation settings.

## Architecture

The shared API surface lives in the core plugin to work around classloader isolation — each extension JAR gets its own `URLClassLoader`, so only classes from the core plugin (parent classloader) are visible to all extensions.

**Core plugin API classes** (`me.bill.fakePlayerPlugin.api.personality`):
- `BotProfile` — bot identity and traits
- `Personality` — personality type (friendly, toxic, quiet, helpful, builder, explorer, neutral)
- `ActivityLevel` — activity modifier (LOW, MODERATE, HIGH, VERY_HIGH)
- `ChatFrequency` — chat rate modifier (RARE, NORMAL, FREQUENT, VERY_FREQUENT)
- `SleepSchedule` — sleep/wake timing preferences
- `ProfileApi` — static accessor for the shared service
- `ProfileService` — interface for loading/saving/updating profiles

**This extension provides** (in `me.bill.fpppersonality`):
- `ProfileServiceImpl` — implements `ProfileService` from the core API
- `ProfileStorage` — persists profiles through the FPP API generic extension data methods
- Profile lifecycle events: `ProfileCreatedEvent`, `ProfileLoadedEvent`, `ProfileSavedEvent`, `ProfileUpdatedEvent`
- `/fpp personality` debug/management command

## Configuration

File: `plugins/FakePlayerPlugin/extensions/fpp-personality/config.yml`

```yaml
enabled: true

default:
  personality: neutral
  activity-level: MODERATE
  chat-frequency: NORMAL
  peak-participation: true
```

## Commands

```
/fpp personality debug           # Show cache size
/fpp personality reload          # Invalidate all cached profiles
/fpp personality list            # List all active profiles
/fpp personality show <bot>      # Show profile details for a bot
```

## Permissions

| Permission | Description |
|------------|-------------|
| `fpp.personality.admin` | Use `/fpp personality` commands |

## How Profiles Work

- Profiles are created automatically when a bot spawns (`FppBotSpawnEvent`).
- Profiles are saved automatically when a bot despawns (`FppBotDespawnEvent`).
- Profiles are persisted through the FPP API's generic extension data methods.
- Core DB schema v24 preserves selected first-party extension fields, so uninstall/reinstall does not erase saved profiles.
- Use `ProfileApi.getService()` to obtain `ProfileService` from any extension.
- Use `updateProfile(botUuid, builderConsumer)` for atomic, auto-saving modifications.

## Integration With Other Extensions

| Extension | What It Reads |
|-----------|---------------|
| `fpp-chat` | Personality traits influence talkativeness, toxicity, topic preferences |
| `fpp-aichat` | Personality, interests, and chat frequency for AI responses |
| `fpp-skin` | Skin preferences from profile |
| `fpp-swap` | Swap group assignment and peak participation flag |
| `fpp-peaks` | Peak participation flag for scheduling |

## Technical Details

- **Priority**: 0 (loads before all other extensions so dependents find it at registration time)
- **Hard dependencies**: `FPP-AIChat`, `FPP-Chat`, `FPP-Peaks`, `FPP-Skin`, `FPP-Swap` all declare a hard dependency on `FPP-Personality`
- **Storage**: Database via FPP API generic extension data methods
- **Default template**: Loaded from config on enable
