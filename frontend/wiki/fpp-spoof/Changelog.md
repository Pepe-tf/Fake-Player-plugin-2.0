# FPP First-Party Extensions Changelog

## v1.2.1

### Architecture: Shared API Classes Moved To Core

- Moved `BotProfile`, `Personality`, `ActivityLevel`, `ChatFrequency`, `SleepSchedule`, `ProfileApi`, and `ProfileService` into the core plugin at `me.bill.fakePlayerPlugin.api.personality` to fix `NoClassDefFoundError` caused by per-extension classloader isolation.
- `fpp-personality` extension retains only the implementation (`ProfileServiceImpl`, `ProfileStorage`, Bukkit events).
- All dependent extensions import from the core API package instead of the extension.

### Fixes

- **Build output**: Jars now land in `fpp-extensions/builds/` (was `../builds/`).
- **Load ordering**: `FPP-Personality` loads first (priority 0) so dependents (`FPP-AIChat`, `FPP-Chat`, `FPP-Peaks`, `FPP-Skin`, `FPP-Swap`) find it at registration time.
- **Build reliability**: Root `:build` now explicitly depends on all extension `:build` tasks so `copyExtension` finalizers always run.
- `cleanAll` no longer races with `copySpoof` across project boundaries.

### Dead Modules Removed From Source

The following modules are no longer built or shipped: `fpp-command`, `fpp-groups`, `fpp-list`, `fpp-nametag`, `fpp-waypoints`. Corresponding wiki pages have been removed.

## v1.6.6.12.8 (No Extension Changes)
- Bumped to match core `1.6.6.12.8`. No first-party extension changes in this release.

## v1.6.6.12.7 (No Extension Changes)

- Bumped to match core `1.6.6.12.7`. No first-party extension changes in this release.

---

## v1.6.6.12.6 (No Extension Changes)

- Bumped to match core `1.6.6.12.6`. No first-party extension changes in this release.

---

## v1.6.6.12.5 (Core Scope Reduction & Click API)

### Build And Packaging

- `fpp-extensions` is documented as the current Gradle multi-project build.
- Individual jars and `fpp-spoof.jar` are copied directly to workspace `builds/`.
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
