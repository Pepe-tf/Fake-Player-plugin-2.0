# FPP First-Party Extensions Changelog

## Current Wiki Sync - 2026-06-05

### Build And Packaging

- `fpp-extensions` is documented as the current Gradle multi-project build.
- Individual jars and `fpp-extensions-bundle.jar` are copied directly to workspace `builds/`.
- Removed stale combined-pack jar references from the main reference docs.

### Modules

- Current modules: `fpp-aichat`, `fpp-chat`, `fpp-command`, `fpp-groups`, `fpp-list`, `fpp-luckperms`, `fpp-nametag`, `fpp-pathfinder`, `fpp-peaks`, `fpp-ping`, `fpp-skin`, `fpp-swap`, and `fpp-waypoints`.
- Added `fpp-pathfinder` and `fpp-swap` to build/output references.
- Corrected `fpp-peaks` from performance monitoring to peak-hour bot scheduling.

### Command Corrections

- `fpp-ping`: removed stale `--all`; omitting bot/count targets all active bots.
- `fpp-skin`: documented current `<bot> <username|reset|--url <url>>` command and spawn `--skin` hook.
- `fpp-waypoints`: documented route entry CRUD plus `patrol <bot|all> <route> [--random]` and `stop <bot|all>`.
- `fpp-luckperms`: documented no-argument `/fpp lpinfo` and current `/fpp rank` forms.
- `fpp-groups`: removed non-existent `members` command and documented current aliases plus `--group` hooks.
- `fpp-aichat`: documented current `/fpp personality` subcommands and `fpp.aichat.personality` permission.

### Configuration Corrections

- Config references now point to each module's `plugins/FakePlayerPlugin/extensions/<extension-name>/config.yml`.
- Removed stale generic config examples that did not exist in source.

---

Older extension history may exist in repository commits, but this wiki page now tracks the current source-aligned documentation state.
