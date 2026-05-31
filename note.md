# FakePlayerPlugin — Development Notes

**Last Updated:** May 30, 2026  
**Current Version:** 1.6.6.12.2

---

## 🔴 Priority 1: Base Plugin Critical Fixes

### 1. Head AI During Actions ✅ FIXED
- [x] **Issue:** Bots still look at players during mine/use actions (not disabled properly)
- [x] **Root Cause:** `lockForAction(uuid, loc, false)` didn't add to `actionLockedBots` set
- [x] **Solution:** Created separate `actingBots` set that tracks all acting bots (regardless of position lock)
- [x] **Files Updated:** `FakePlayerManager.java` (line 416), added `actingBots` set
- [x] **Tested:** Head AI now fully disabled during `/fpp mine`, `/fpp use`, `/fpp place`
- [x] **Debug:** Enable `logging.debug.head-ai: true` to see "SKIPPED - bot is acting" messages

### 2. PacketEvents Injection Failure
- [ ] **Issue:** PacketEvents injection fails on some servers
- [ ] **TODO:**
  - Investigate injection timing (startup vs lazy)
  - Check compatibility with different Paper/Purpur versions
  - Add fallback mechanism if injection fails
  - Log detailed error with server version info

### 3. Pathfinding Improvements
- [ ] **Ongoing:** Continue improving A* pathfinding
- [ ] **TODO:**
  - Better door handling
  - Improved parkour detection
  - Smoother swimming navigation
  - Reduce path recalculation frequency
  - Add debug visualization for paths
- [ ] **Files:** `PathfindingService.java`, `BotPathfinder.java`

---

## 🟡 Priority 2: Base Plugin New Features

### 4. Body System Updates
- [x] **Removed:** Body disable toggle (bots always spawn with bodies)
- [x] **Removed:** Skin system toggle from GUI
- [x] **Kept:** Body settings (pushable, damageable, pickup items/xp, etc.)
- [x] **Files:** `SettingGui.java` (body() method cleaned up)

### 5. Mount Command
- [ ] **Feature:** `/fpp mount <bot> <entity>` - Bot mounts entities (horses, minecarts, boats)
- [ ] **TODO:**
  - Create `MountCommand.java`
  - Handle different entity types (horse, donkey, mule, minecart, boat, pig, strider)
  - Add saddle/armor support
  - Navigation to mount position
  - Dismount command
  - Persistence for mounted state

### 6. Multi-Action System
- [ ] **Feature:** Bots perform multiple actions in sequence/parallel
- [ ] **TODO:**
  - Design action queue system
  - Allow `/fpp action add <mine|use|place|attack>` 
  - Priority system for actions
  - Cancel/stop individual actions
  - Persistence for action queues
  - Example: Mine → Deposit in chest → Return to mining

---

## 🟢 Priority 3: Spoof Extension Pack

### 6. Convert to Gradle
- [ ] **Current:** Maven-based project
- [ ] **TODO:**
  - Create `build.gradle.kts`
  - Migrate dependencies
  - Setup shadowJar for fat JAR
  - Match base plugin build structure
  - Update CI/CD pipelines

### 7. `--all` Tag for Mass Config
- [ ] **Feature:** Easy apply config to all bots
- [ ] **TODO:**
  - Add `--all` flag to spoof commands
  - Apply settings to all online bots
  - Progress feedback in chat
  - Confirmation prompt for large bot counts

### 8. Custom Ping TabList Order
- [ ] **Issue:** Custom ping values don't sort correctly in tablist
- [ ] **TODO:**
  - Investigate tablist packet ordering
  - Force ping value in player info packet
  - Test with different tablist plugins (EssentialsX, TAB, etc.)

### 9. Auto Skin Apply on Spawn
- [ ] **Issue:** Skins don't auto-apply when Spoof Extension is installed
- [ ] **TODO:**
  - Hook into spawn event
  - Check for Spoof Extension API
  - Apply cached/premium skin automatically
  - Add config option to toggle auto-apply

---

## 🔵 Priority 4: New Extension Packs

### 10. AntiCheat Bypass Extension
- [ ] **Goal:** Bypass common anticheat plugins
- [ ] **TODO:**
  - Research Watchdog/Grim/Vulcan detection methods
  - Packet timing randomization
  - Movement smoothing
  - Combat delay simulation
  - Per-server profiles
  - **Target anticheats:**
    - [ ] GrimAC
    - [ ] Vulcan
    - [ ] Watchdog
    - [ ] Matrix
    - [ ] Spartan

### 11. Register & Login Plugin Support
- [ ] **Goal:** AuthMe, LoginSecurity compatibility
- [ ] **TODO:**
  - Auto-register bots on first join
  - Auto-login on spawn
  - Handle password config
  - Support premium (cracked) servers
  - **Target plugins:**
    - [ ] AuthMe
    - [ ] LoginSecurity
    - [ ] FastLogin

### 12. Bypasses Extension (General)
- [ ] **Goal:** Additional bypass utilities
- [ ] **TODO:**
  - WorldGuard region bypass
  - GriefPrevention claim bypass
  - PermissionsEx/LuckPerms virtual groups
  - Cooldown bypass
  - Join limit bypass

---

## 📝 Recent Changes (v1.6.6.12)

### Completed
- [x] Combined Place into Use command (`--place`, `--both` modes)
- [x] Flexible mining/using (bots use current look direction every tick)
- [x] Bots can be pushed/moved during actions (position not locked)
- [x] Start actions from player's look direction (not just bot's)
- [x] Added `logging.debug.commands` and `logging.debug.head-ai` config options
- [x] UseCommand now handles blocks AND entities
- [x] Persistence updated for new UseCommand structure
- [x] **Head AI fully disabled during actions** (mining/use/place) - uses `actingBots` set
- [x] Fixed UseCommand NPE when bot looks at nothing (null target check)
- [x] Fixed MineCommand - bots now actually break blocks (not just animation)

### Known Issues
- [ ] Some users report PacketEvents injection failures
- [ ] Area mining/place jobs need tick implementation

---

## 📁 Key Files

| File | Purpose |
|------|---------|
| `FakePlayerManager.java` | Main bot tick loop, head AI, action locks |
| `MineCommand.java` | Mining logic, flexible targeting |
| `UseCommand.java` | Use/Place combined, flexible targeting |
| `PathfindingService.java` | A* navigation, door/parkour/swim |
| `BotPathfinder.java` | Path calculation algorithm |
| `config.yml` | Main config (auto-migrates to v73) |

---

## 🧪 Testing Checklist

Before each release:
- [ ] Mine command (single block + area)
- [ ] Use command (blocks + entities)
- [ ] Place command (via Use --place)
- [ ] Head AI disabled during actions
- [ ] Bots can be pushed while acting
- [ ] Persistence saves/restores correctly
- [ ] No console errors on startup
- [ ] PacketEvents injection successful
- [ ] Compatible with Paper 1.21.11


---

## 💡 Ideas for Future Versions

- [ ] Bot-to-bot trading system
- [ ] Scheduled tasks (cron-like)
- [ ] Bot parties/groups
- [ ] Advanced combat AI (PvP)
- [ ] Voice chat integration
- [ ] Web dashboard for management
- [ ] Discord bot integration
- [ ] Economy integration (bots can earn/spend money)
- [ ] Quest system for bots
- [ ] Bot templates/loadouts
