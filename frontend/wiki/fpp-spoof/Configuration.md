# FPP First-Party Extension Configuration

Each extension creates its own config under:

```text
plugins/FakePlayerPlugin/extensions/<extension-name>/config.yml
```

Run `/fpp reload extensions` after editing extension configs, or restart the server.

## Config Highlights

| Extension | Key Sections |
|-----------|--------------|
| `fpp-aichat` | `direct-messages`, `typing-delay`, `public-chat`, AI provider settings and secrets |
| `fpp-chat` | `fake-chat`, event triggers, bot-to-bot replies, public chat reactions, keyword reactions |
| `fpp-luckperms` | `default-group`, `permissions.lpinfo`, `permissions.rank` |
| `fpp-peaks` | `peak-hours.enabled`, `timezone`, `stagger-seconds`, `min-online`, schedules, day overrides |
| `fpp-personality` | `default.personality`, `default.activity-level`, `default.chat-frequency`, `default.peak-participation` |
| `fpp-ping` | `random.min/max`, `ping.enabled`, variability, spike settings, permissions, message prefix |
| `fpp-skin` | `skin.mode`, `guaranteed-skin`, `overrides`, `pool`, skin folder, MineSkin URL upload settings |
| `fpp-swap` | `swap.enabled`, swapped-out limits, online minimum, greetings/farewells, retry, session/absence ranges |

## Build Config

`fpp-extensions/build.gradle.kts` builds these modules as a Gradle multi-project. Each module jar and the aggregate `fpp-spoof-1.2.1.jar` are copied to `fpp-extensions/builds/` by the build.

```powershell
cmd /c "..\\fake-player-plugin\\gradlew.bat -p . build"
```

The build expects `fake-player-plugin/build/libs/fake-player-plugin-1.6.6.12.8-all.jar` to exist because extension modules compile against the FPP API.
