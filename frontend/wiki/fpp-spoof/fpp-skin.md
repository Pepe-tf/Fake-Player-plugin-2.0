# fpp-skin - Skin Extension

Advanced skin management for FPP bots — fetch, set, cache, and distribute bot skins.

## Overview

fpp-skin provides comprehensive skin management for your bots. Skins can be fetched from multiple sources (Mojang API, Minecraft Services, MineSkin), applied by player name or direct texture URL, assigned randomly from a pool, or loaded from local skin files. Perfect for creating visually diverse bot populations.

## How It Works

The extension provides a `SkinManager` and `SkinFetchService` to the FPP core. When a skin is requested:

1. The fetcher checks the local cache (`ConcurrentHashMap`)
2. If not cached, it queries the configured source (player name → Mojang API, URL → MineSkin)
3. Concurrent requests for the same skin are coalesced (only one API call)
4. The skin is cached and applied to the bot
5. Rate limiting prevents API abuse

## Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-skin/config.yml`

```yaml
enabled: true

permissions:
  command: fpp.skin

skin:
  mode: player
  guaranteed-skin: true
  clear-cache-on-reload: true
  overrides: {}
  pool: []
  use-skin-folder: true

  mineskin:
    url-upload-enabled: true
    api-key: ""
    visibility: public
```

## Commands

### Single Bot Operations

```
/fpp skin <bot>                        # Show bot's current skin info
/fpp skin <bot> --skin <value>         # Set skin (username, URL, or file)
/fpp skin <bot> --random               # Assign random skin from pool
/fpp skin <bot> --reset                # Reset to default skin
/fpp skin <bot> --clear                # Clear cached skin data
```

### Bulk Operations

```
/fpp skin --all                        # Show all bot skins
/fpp skin --all --skin <value>         # Set all bots to same skin
/fpp skin --all --random               # Random skins for all bots
/fpp skin --all --reset                # Reset all bot skins
```

## Skin Sources

### Player Names

```
/fpp skin Bot1 --skin Notch
/fpp skin Bot1 --skin Dream
/fpp skin Bot1 --skin Herobrine
```

Fetches the skin of the specified Minecraft username via:
1. **Mojang API** — Primary source for player profiles
2. **Minecraft Services** — Fallback for texture data

### Texture URLs

```
/fpp skin Bot1 --skin https://example.com/skin.png
```

Direct URLs to PNG skin files (64x32 or 64x64). Uses **MineSkin** API to convert URLs to Minecraft-compatible texture data.

### Skin Files

Skins placed in the `skins/` folder can be referenced by filename:

```
plugins/FakePlayerPlugin/skins/
├── my-custom-skin.png
├── knight-armor.png
└── wizard-robe.png
```

```
/fpp skin Bot1 --skin my-custom-skin.png
```

### Skin Pools

Define a pool of player names in config for random assignment:

```yaml
skin:
  pool:
    - "Notch"
    - "jeb_"
    - "Dinnerbone"
    - "Grumm"
```

```
/fpp skin Bot1 --random
```

Bots get random skins from the pool.

## Key Features

- **Multi-Source Fetching**: Mojang API, Minecraft Services, MineSkin, local files
- **Async Fetching**: Non-blocking skin downloads
- **Rate Limiting**: Prevents API abuse with configurable cooldown
- **Request Coalescing**: Duplicate requests are merged (same player name = one API call)
- **Local Caching**: `ConcurrentHashMap` cache with reload support
- **Skin Folder**: Place custom skins in `skins/` directory
- **Guaranteed Skin**: Falls back through sources to always get a valid skin
- **Pool Distribution**: Random skins from configurable pool
- **NameTag Sync**: Resolves conflicts with NameTag plugin skins
- **Clear Cache**: Reload wipes cache for fresh fetches

## Permission Model

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.skin` | View skin information | true |
| `fpp.skin.set` | Set skins (usernames/URLs) | op |
| `fpp.skin.random` | Use random skins | op |
| `fpp.skin.bulk` | Bulk skin operations | op |

## Skin Fetching Flow

When a skin is requested:

```
Request skin for "Notch"
  ├── Check cache → HIT → return cached skin
  └── Cache MISS
      ├── Check for ongoing request → coalesce
      └── New request
          ├── Mojang API → get UUID
          ├── Minecraft Services → get textures
          ├── Fallback: MineSkin (URL mode)
          ├── Local file fallback
          └── Cache result → return
```

## Use Cases

### Themed Bot Population

```yaml
skin:
  pool:
    - "Steve"
    - "Alex"
    - "Noor"
    - "Sunny"
    - "Ari"
    - "Zuri"
    - "Efe"
    - "Makena"
```

Random default skins for a varied look.

### Roleplay Characters

```
/fpp skin KingBot --skin monarch-skin.png
/fpp skin GuardBot1 --skin guard-1.png
/fpp skin GuardBot2 --skin guard-2.png
/fpp skin WizardBot --skin wizard.png
```

Each bot gets a custom skin matching their role.

### Skin Overrides

Force specific skins for certain bots:

```yaml
skin:
  overrides:
    KingBot: "monarch-skin.png"
    QueenBot: "queen-skin.png"
```

### Celebrity Bot Names

```
/fpp skin Bot1 --skin Technoblade
/fpp skin Bot2 --skin Dream
/fpp skin Bot3 --skin GeorgeNotFound
```

Bots that look like famous players.

## Skin Cache

The skin cache:
- Stores fetched textures in memory (`ConcurrentHashMap`)
- Cleared on `/fpp reload` (configurable with `clear-cache-on-reload`)
- Shares data between requests for the same player name (coalescing)
- Reduces API calls significantly for popular skins

## Troubleshooting

### Skin Not Applying

- Check internet connectivity (Mojang API access)
- Verify URL points to a valid PNG file
- Try a player name instead of URL
- Check `enabled: true` in config

### "Invalid Skin" Error

- URL must end in `.png`
- Image should be 64x32 or 64x64 pixels
- File must be a valid PNG format
- URL must be directly accessible (not a redirect page)

### Slow Skin Loading

- First fetch requires API calls (may take 1-3 seconds)
- Subsequent fetches use cache (instant)
- Rate limiting may delay frequent requests
- Check `mineskin.api-key` for faster URL processing

### Random Skin Not Working

- Verify `pool` has entries in config
- Pool must contain player names (not URLs)
- Run `/fpp reload` after changing pool

## Technical Details

- **Priority**: 100 (default)
- **Cache**: `ConcurrentHashMap<String, FppSkinData>`
- **Services**: Provides `SkinManager` and `SkinFetchService` to FPP core
- **Async**: All skin fetching is non-blocking
- **Rate Limiting**: Suppressed logging during cooldown to avoid console spam
- **MineSkin**: URL-based skin generation with configurable API key and visibility
