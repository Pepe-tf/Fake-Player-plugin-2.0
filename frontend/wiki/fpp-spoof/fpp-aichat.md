# fpp-aichat - AI Chat Extension

AI personalities and conversation support for FPP bots.

## Providers

Current source includes providers for OpenAI, Groq, Anthropic, Google Gemini, Ollama, Copilot, and custom OpenAI-compatible APIs.

## Configuration

File: `plugins/FakePlayerPlugin/extensions/fpp-aichat/config.yml`

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

Provider secrets are generated from extension resources under the extension data folder.

## Commands

`/fpp personality` aliases: `/fpp persona`, `/fpp aipersonality`

```text
/fpp personality list
/fpp personality reload
/fpp personality providers
/fpp personality <bot> set <name>
/fpp personality <bot> reset
/fpp personality <bot> show
```

## Permissions

| Permission | Description |
|------------|-------------|
| `fpp.aichat.personality` | Use `/fpp personality` |

## Notes

- Direct-message replies are controlled by `direct-messages.enabled`.
- Public chat reactions are disabled by default through `public-chat.enabled: false`.
- Personality prompts are loaded from extension resource personality files and can use `{bot_name}`.
