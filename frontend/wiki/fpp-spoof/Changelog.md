# FPP Extensions Changelog

All notable changes to FPP Extensions will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.1.0] - 2026-05-23

### Added

- **fpp-ping**
  - Added `--all` flag for bulk ping operations on all bots
  - Added `--count <n>` flag for operations on n random bots
  - Enhanced tab completion for bulk operations
  
- **fpp-skin**
  - Added `--all` flag for bulk skin operations on all bots
  - Bulk command support for `--skin`, `--random`, and `--reset` options

- **General**
  - Improved error messages for invalid command arguments
  - Better null safety across all extensions

### Changed

- **fpp-ping**
  - Migrated to Adventure Component API (reduces deprecation warnings)
  
- **fpp-chat**
  - Config defaults now save properly on first run
  
- **fpp-list**
  - Changed `bot-tab-list.enabled` default from `true` to `false`
  - Improves compatibility with LuckPerms group ordering
  
- **Project**
  - Renamed extension pack from `fpp-pack` to `fpp-spoof`
  - Updated all module versions from `1.0.0` to `1.1.0`
  - Output jar now includes version: `fpp-spoof-1.1.0-all.jar`

### Fixed

- **fpp-chat**
  - Messages not sending due to config not loading defaults
  
- **fpp-list**
  - Tab list interfering with LuckPerms group ordering
  
- **fpp-ping**
  - ChatColor deprecation warnings (partial migration to Adventure)
  
- **General**
  - Null safety improvements across all extensions
  - Error handling for invalid command arguments
  - Better error messages for missing dependencies

### Technical

- Converted all modules from Maven (pom.xml) to Gradle (build.gradle.kts)
- Removed all pom.xml files from project
- Added proper dependency management in Gradle
- Improved build configuration and task organization
- Enhanced CI/CD pipeline for automated builds

### Migration Notes

**From 1.0.0 to 1.1.0:**

1. **Config Update Required:** If you modified `fpp-list/config.yml`, manually set `bot-tab-list.enabled: false` to maintain LuckPerms compatibility

2. **JAR File Name Changed:** The output file is now `fpp-spoof-1.1.0-all.jar` instead of `fpp-pack-1.0.0-all.jar`

3. **No Breaking Changes:** All commands and permissions remain compatible

---

## [1.0.0] - 2026-05-22

### Added

Initial release with 11 extensions:

- **fpp-aichat**
  - AI-powered bot chat using LLM APIs
  - Support for OpenAI, Anthropic, and local models
  - Custom personality system
  - Context-aware conversations
  
- **fpp-chat**
  - Bot chat with cooldowns and anti-spam
  - Configurable chat radius
  - Random delay variation
  - Custom chat prefixes
  
- **fpp-command**
  - Execute commands as bots
  - Command cooldowns
  - Permission-based execution
  - Right-click command assignment
  
- **fpp-groups**
  - Bot group management
  - Group-based permissions
  - Bulk operations on groups
  - Group inheritance support
  
- **fpp-list**
  - Advanced player list/tab control
  - Custom header/footer
  - Sort mode configuration
  - Group-based priorities
  
- **fpp-luckperms**
  - LuckPerms integration for bots
  - Auto-assign groups
  - Permission synchronization
  - Context support
  
- **fpp-nametag**
  - Custom nametags for bots
  - Prefix/suffix support
  - Dynamic nametags via LuckPerms
  - Health display option
  
- **fpp-peaks**
  - Display server TPS and performance
  - Memory usage monitoring
  - Player count tracking
  - Uptime statistics
  
- **fpp-ping**
  - Show or spoof bot ping values
  - Custom ping setting
  - Random ping generation
  - Ping reset functionality
  
- **fpp-skin**
  - Manage bot skins from MCHead/NameMC
  - Custom skin URL support
  - Random skin assignment
  - Skin caching system
  
- **fpp-waypoints**
  - Bot waypoint/pathfinding system
  - Patrol route creation
  - Custom pathfinding settings
  - Waypoint behaviors

### Features

- Combined extension pack (`fpp-spoof-all.jar`)
- Individual extension builds available
- Basic command permissions
- Configuration files for each extension
- Automatic config generation
- Extension reload support via `/fpp reload`
- Tab completion for all commands
- Comprehensive permission system

### Excluded

The following extensions are **not** included:

- **fpp-pathfinder** - Functionality moved to base FPP plugin
- **fpp-swap** - Incompatible with FPP 1.6.6.12.2 API

### Requirements

- Minecraft 1.21+
- Paper/Spigot 1.21+
- Java 21
- Fake Player Plugin 1.6.6.12.2+
- LuckPerms (optional, for group/nametag extensions)

---

## Version History

| Version | Date | Notes |
|---------|------|-------|
| [1.1.0](#110---2026-05-23) | 2026-05-23 | Bulk operations, config fixes, Gradle migration |
| [1.0.0](#100---2026-05-22) | 2026-05-22 | Initial release |

---

## Upcoming Features (Planned)

### 1.2.0 (TBD)

- **fpp-economy** - Economy integration for bots
- **fpp-quests** - Bot quest system
- **fpp-trades** - Bot trading system
- Enhanced AI chat with memory
- Web dashboard for configuration
- Discord integration

### Future Considerations

- MiniGames extension
- Bot recording/playback
- Advanced patrol behaviors
- Custom bot skills/abilities
- Bot inventory management
- Bot crafting system

---

## Reporting Issues

Found a bug or have a feature request?

- **GitHub Issues:** https://github.com/yourusername/fpp-extensions/issues
- **Discord Support:** https://discord.gg/WRvfmV24Hh

When reporting issues, please include:

1. FPP Extensions version
2. FPP version
3. Server software and version
4. Steps to reproduce
5. Expected vs actual behavior
6. Server logs (if applicable)

---

## Contributing

We welcome contributions! Please see our [Contributing Guide](https://github.com/yourusername/fpp-extensions/blob/main/CONTRIBUTING.md) for details.

### How to Contribute

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

### Code Standards

- Follow existing code style
- Add comments for complex logic
- Include tests for new features
- Update documentation
- Test on latest FPP version

---

## Credits

**Developers:**
- Lead Developer: Your Name
- Contributors: See GitHub contributors list

**Special Thanks:**
- Fake Player Plugin team for the excellent API
- Community testers and beta contributors
- Discord community for feedback and support

**Libraries Used:**
- Adventure API - Modern Minecraft text API
- LuckPerms API - Permission management
- Paper API - High-performance server implementation

---

## License

MIT License - See [LICENSE](https://github.com/yourusername/fpp-extensions/blob/main/LICENSE) for details
