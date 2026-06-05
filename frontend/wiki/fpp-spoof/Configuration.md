# FPP First-Party Extension Configuration

Each extension creates its own config under:

```text
plugins/FakePlayerPlugin/extensions/<extension-name>/config.yml
```

Run `/fpp reload extensions` after editing extension configs, or restart the server.

## Config Highlights

| Extension | Key Sections |
|-----------|--------------|
| `fpp-aichat` | `personality.default`, `personality.auto-assign-on-spawn`, `direct-messages`, `typing-delay`, `public-chat` |
| `fpp-chat` | `permissions.command`, `fake-chat`, event triggers, bot-to-bot replies, public chat reactions, keyword reactions |
| `fpp-command` | `permissions.command`, `permissions.legacy` |
| `fpp-groups` | `migration.import-core-groups`, `permissions.command`, message prefix |
| `fpp-list` | `bot-tab-list.enabled`, `bot-tab-list.sync-interval-ticks`, `server-player-list.count-bots`, `server-player-list.include-remote-bots` |
| `fpp-luckperms` | `default-group`, `permissions.lpinfo`, `permissions.rank` |
| `fpp-nametag` | `nametag.block-nick-conflicts`, `nametag.bot-isolation`, `nametag.sync-nick-as-rename`, `nametag.refresh-display-names` |
| `fpp-pathfinder` | `pathfinding.parkour`, block break/place toggles, arrival distances, recalc intervals, stuck thresholds, range/node limits |
| `fpp-peaks` | `peak-hours.enabled`, `timezone`, `stagger-seconds`, `min-online`, schedules, day overrides |
| `fpp-ping` | `random.min/max`, `ping.enabled`, variability, spike settings, permissions, message prefix |
| `fpp-skin` | `skin.mode`, `guaranteed-skin`, `overrides`, `pool`, skin folder, MineSkin URL upload settings |
| `fpp-swap` | `swap.enabled`, swapped-out limits, online minimum, greetings/farewells, retry, session/absence ranges |
| `fpp-waypoints` | `patrol.arrival-distance`, `patrol.random-reshuffle-each-cycle`, `migration.import-core-waypoints` |

## Build Config

`fpp-extensions/build.gradle.kts` builds these modules as a Gradle multi-project. Each module jar and the aggregate `fpp-extensions-bundle.jar` are copied to workspace `builds/` by the build.

```powershell
cmd /c "fake-player-plugin\\gradlew.bat -p fpp-extensions build"
```

The build expects `fake-player-plugin/build/fpp.jar` to exist because extension modules compile against the FPP API.
