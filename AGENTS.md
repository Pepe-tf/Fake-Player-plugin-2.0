# AGENTS.md — FakePlayerPlugin

## Project Overview
Minecraft Paper/Purpur 1.21+ plugin for spawning fake players with tab-list, physical bodies, pathfinding, automation, and multi-server proxy support.

**Current version:** 1.6.6.12.3 | **Config version:** 73 | **Java:** Toolchain 25, targets 21

---

## Build & Commands
```bash
./gradlew shadowJar           # Build distributable JAR (build/libs/fake-player-plugin-*.jar)
./gradlew test                # Minimal tests (2 JUnit tests, mostly no-op)
./gradlew runServer           # Run Paper 1.21.11 dev server
```

**Build quirk:** Use `shadowJar`, not `build`, to produce the runnable plugin JAR.

---

## Critical Gotchas (Read First)

### License Verification (BLOCKER)
- Plugin fetches credentials from `fpp.wtf` on startup and **disables itself** if unreachable
- **Internet required for development/testing**
- License check happens BEFORE most initialization (see `FakePlayerPlugin.java:168-203`)

### Folia Support (v1.6.6.12.2+)
- Plugin supports Folia via region scheduler for bot spawning
- `FakePlayerManager.spawn()` detects Folia and routes entire spawn chain to region thread via `FppScheduler.runAtLocation()`
- `NmsPlayerSpawner.isFoliaServer()` detects Folia at runtime
- `folia-supported: true` in `plugin.yml`

### Body System Changed (v1.6.6.12)
- Body disable toggle REMOVED — bots **always** spawn with physical bodies
- Tab-list only mode no longer available

### Command Registration Quirk
New commands must be registered in **BOTH**:
1. `CommandManager.java` (constructor)
2. `FakePlayerPlugin.onEnable()` (lines 342-414)

### Config Migration
- Auto-migrates on startup via `ConfigMigrator`
- **Do not manually edit `config-version`**

### Tight Coupling
Commands typically require references to: `FakePlayerManager`, `PathfindingService`, `StorageStore`. Many commands have circular dependencies (e.g., `StopCommand` needs all action commands).

---

## Architecture & Entrypoints

**Main class:** `FakePlayerPlugin.java:88` — standard Bukkit `JavaPlugin`

### Core Components
| Component | File | Purpose |
|-----------|------|---------|
| **Bot tick loop** | `FakePlayerManager.java` | Head AI, action locks (`actingBots` set), pathfinding coordination |
| **Pathfinding** | `PathfindingService.java` + `BotPathfinder.java` | A* with door/parkour/swim handling |
| **Click commands** | `LeftClickCommand`, `RightClickCommand` | Unified click actions (`--once`, `--repeat`, `--hold`, `--stop`) |
| **Persistence** | `BotPersistence.java` | Save/restore positions, tasks, inventories (DB or YAML) |
| **Extension API** | `api/FppExtension.java` | Drop `.jar` into `plugins/FakePlayerPlugin/extensions/` |

### Package Structure
```
src/main/java/me/bill/fakePlayerPlugin/
├── api/           # Extension interfaces
├── command/       # 28+ command handlers
├── config/        # Config accessors (Config, BotNameConfig)
├── database/      # SQLite/MySQL abstraction
├── extension/     # Extension loader
├── fakeplayer/    # Core bot logic (15+ classes)
├── gui/           # Settings/help inventories
├── listener/      # 10+ Bukkit event handlers
├── messaging/     # Velocity/BungeeCord channels
├── network/       # Cross-server heartbeat
├── sync/          # Config sync manager
└── util/          # 30+ utility classes
```

---

## Config & Data

**Main config:** `plugins/FakePlayerPlugin/config.yml` (auto-migrated)

**Database:**
- SQLite (local): `plugins/FakePlayerPlugin/data/fpp.db`
- MySQL (NETWORK mode): Shared across proxy backends, requires unique `server-id` per server

**Persistence:** Bot positions, tasks, inventories survive restarts (database primary, YAML fallback)

**Languages:** `plugins/FakePlayerPlugin/lang/`

---

## Known Issues / TODOs

See `note.md` for authoritative list. Current priorities:

- [ ] **PacketEvents injection** can fail on some Paper/Purpur versions
- [ ] **Pathfinding:** door handling, parkour, swimming need work
- [ ] **Area mining/place:** tick implementation improvements needed

---

## CI/CD

**Build:** `.github/workflows/build.yml`
- Java 21 (Temurin), `shadowJar`
- Runs on: pull_request, push

**Qodana:** `qodana.yaml`
- Linter: `jetbrains/qodana-jvm-community:2026.1`
- Project JDK: 25
- Profile: `qodana.starter`

---

## Dependencies

**Hard:** PaperMC 1.21.11 dev bundle, FastStats metrics

**Soft (runtime reflection):**
- PlaceholderAPI (80+ placeholders)
- LuckPerms (prefix/suffix, bot groups)
- WorldGuard (bot PvP region protection)
- WorldEdit (`--wesel` flag for area mining/placing)

**compileOnly:** LuckPerms API, WorldGuard

---

## Testing

**Automated:** 2 JUnit tests (`FoliaCompatibilityTest`) — mostly no-op

**Manual checklist** (from `note.md`):
- Mine/Use/Place commands (single block + area)
- Head AI disabled during actions (`actingBots` set)
- Bots pushable while acting
- Persistence save/restore
- PacketEvents injection on target server versions
- No console errors on startup

---

## Important Directories

| Directory | Purpose |
|-----------|---------|
| `build/libs/` | Output JAR (after `shadowJar`) |
| `plugins/FakePlayerPlugin/extensions/` | Extension JAR drop location |
| `plugins/FakePlayerPlugin/data/` | SQLite DB, persistence YAML |
| `src/main/resources/` | Plugin resources (config.yml, plugin.yml) |

---

## References

- **Development notes:** `note.md` (authoritative TODO list, recent changes, testing checklist)
- **Wiki:** https://fpp.wtf
- **Modrinth:** https://modrinth.com/plugin/fake-player-plugin-(fpp)
