# fpp-aichat - AI Chat Extension

AI-powered chat for FPP bots using Large Language Model APIs.

## Overview

fpp-aichat gives your bots intelligent conversations powered by AI. Bots can respond to direct messages, react to public chat, and maintain unique personalities — all through configurable LLM providers.

## How It Works

When a player sends a message to a bot (via `/msg` or public chat), the extension:

1. Captures the message via event listeners
2. Looks up the bot's assigned personality
3. Sends the conversation history to the configured AI provider
4. Receives a generated response
5. Has the bot "type" with simulated delay
6. Sends the response as the bot

## AI Providers

| Provider | API Required | Model Support | Notes |
|----------|-------------|---------------|-------|
| **OpenAI** | API key | GPT-3.5, GPT-4, GPT-4o | Most popular, good quality |
| **Groq** | API key | Mixtral, Llama, Gemma | Fast inference, free tier |
| **Anthropic** | API key | Claude 3 Opus/Sonnet/Haiku | High quality, safety-focused |
| **Google Gemini** | API key | Gemini 1.5 Pro/Flash | Competitive pricing |
| **Ollama** | None (local) | Llama, Mistral, Phi, etc. | Self-hosted, free, requires server |
| **Copilot** | API key | Azure OpenAI models | Microsoft Azure integration |
| **Custom OpenAI** | API key | Any OpenAI-compatible API | Self-hosted or third-party |

## Bot Personalities

Bots can have distinct personalities loaded from text files:

| Personality | Description |
|-------------|-------------|
| **default** | Casual player, short lowercase replies with typos |
| **friendly** | Warm, upbeat, supportive with casual emotes |
| **noob** | Confused beginner with spelling mistakes |
| **miner** | Tunnel-vision miner obsessed with ores and caves |
| **silent** | Quiet type, only replies when necessary |
| **grumpy** | Impatient veteran, extremely terse |
| **explorer** | Adventurous, talks about biomes and ruins |
| **builder** | Practical, discusses materials and layouts |
| **farmer** | Relaxed, mentions crops and animals |

Personalities are stored as `.txt` files in `extension-resources/personalities/` and use `{bot_name}` as a placeholder.

## Configuration

**File:** `plugins/FakePlayerPlugin/extensions/fpp-aichat/config.yml`

```yaml
enabled: true
debug: false

personality:
  default: "default"
  auto-assign-on-spawn: true

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

### Secrets

**File:** `plugins/FakePlayerPlugin/extensions/fpp-aichat/secrets.yml`

```yaml
openai:
  api-key: ""
  model: "gpt-3.5-turbo"

groq:
  api-key: ""
  model: "mixtral-8x7b-32768"

anthropic:
  api-key: ""
  model: "claude-3-haiku-20240307"

google:
  api-key: ""
  model: "gemini-1.5-flash"

ollama:
  enabled: false
  endpoint: "http://localhost:11434"
  model: "llama3"

custom:
  enabled: false
  api-key: ""
  model: ""
  endpoint: ""
```

## Commands

### Personality Command

```
/fpp personality <bot> [personality]    # View or set bot personality
/fpp personality <bot> --list           # List available personalities
/fpp personality <bot> --reset          # Reset to default personality
```

## Key Features

- **Multi-Provider**: 7 different AI providers with automatic fallback
- **Personality System**: 9 preset personalities with custom prompt support
- **Direct Messages**: Intercepts `/msg`, `/tell`, `/w` to bots
- **Public Chat**: Bots can react to nearby player conversations
- **Typing Simulation**: Configurable typing delay per character
- **Conversation Memory**: Maintains per-bot chat history
- **Auto-Assign**: Automatically assigns personality on bot spawn
- **Cooldowns**: Prevents spam with configurable rate limits

## Permission Model

**No separate permission nodes.** Access is controlled by the base permission system. Bots will only respond to players who have permission to message them.

## Use Cases

- **NPC Companions**: Bots that feel like real players with distinct personalities
- **Roleplay Servers**: Characters with backstories and consistent behavior
- **Support Bots**: Informational bots that answer player questions
- **Auto-Moderators**: Bots that gently remind players of rules
- **Quest Givers**: Interactive NPCs that provide guidance

## Architecture

```
FppAiChatExtension (main)
├── AIProviderRegistry — Loads and manages AI providers
│   └── AIProvider (interface)
│       ├── OpenAIProvider
│       ├── GroqProvider
│       ├── AnthropicProvider
│       ├── GoogleGeminiProvider
│       ├── OllamaProvider
│       ├── CopilotProvider
│       └── CustomOpenAIProvider
├── BotConversationManager — Per-bot chat state
│   ├── Rate limiting
│   ├── History tracking
│   └── Typing simulation
└── PersonalityRepository — Loading personality prompts
    └── personalities/*.txt
```

## Event Handling

| Event | Purpose |
|-------|---------|
| `AsyncPlayerChatEvent` | Public chat reactions |
| `PlayerCommandPreprocessEvent` | Intercept `/msg`, `/tell`, `/w` commands |
| `FppBotSpawnEvent` | Auto-assign personality |
| `FppBotChatEvent` | Bot-originated chat |

## Troubleshooting

### Bot Not Responding

- Check `secrets.yml` API key is entered correctly
- Verify the AI provider is available (internet connection)
- Check `direct-messages.enabled: true` in config
- Ensure bot has a personality assigned
- Check for cooldown restrictions

### API Errors

- Verify API key has available credits/quota
- Check API endpoint URLs are correct
- Ensure firewall allows outbound connections
- Try a different AI provider

### Personality Not Loading

- Verify personality file exists in `personalities/` folder
- Check file is valid UTF-8 text
- Ensure `{bot_name}` placeholder is present if needed
- Run `/fpp reload` after adding personalities

## Technical Details

- **Priority**: 100 (default)
- **Soft Dependencies**: None
- **API Dependencies**: Paper API 1.21+, FPP Core
- **Chat Messages**: Uses Adventure Component API
- **Async Safety**: AI requests are made async, responses scheduled on main thread
