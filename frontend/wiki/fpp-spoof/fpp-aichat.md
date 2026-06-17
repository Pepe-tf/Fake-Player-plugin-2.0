# fpp-aichat - AI Chat Extension

Provider-backed AI direct messages and public chat reactions for FPP bots.

## Overview

fpp-aichat enables bots to have AI-powered conversations with players. Bots can respond to direct messages and optionally react to public chat with context-aware AI replies.

**Note:** The personality/profile system previously documented under fpp-aichat has been moved to the separate [fpp-personality](fpp-personality) extension. Shared API classes (`BotProfile`, `Personality`, `ProfileService`) live in the core plugin at `me.bill.fakePlayerPlugin.api.personality`.

## Providers

Current source includes providers for OpenAI, Groq, Anthropic, Google Gemini, Ollama, Copilot, and custom OpenAI-compatible APIs.

## Configuration

File: `plugins/FakePlayerPlugin/extensions/fpp-aichat/config.yml`

```yaml
enabled: true
debug: false

direct-messages:
  enabled: true
  max-history: 10
  cooldown: 3

typing-delay:
  enabled: true
  base: 1.0
  per-char: 0.07
  max: 5.0

public-chat:
  enabled: false
  chance: 0.25
  max-bots: 1
  ignore-short: true
  ai-cooldown: 30
  delay:
    min: 2
    max: 8
```

Provider secrets are generated from extension resources under the extension data folder.

## Permissions

| Permission | Description |
|------------|-------------|
| `fpp.aichat` | Use AI chat features |

## Integration

- Reads `BotProfile` via `ProfileApi` to access personality traits, interests, and chat frequency settings.
- The `fpp-personality` extension must be loaded for profile-aware AI behavior.
