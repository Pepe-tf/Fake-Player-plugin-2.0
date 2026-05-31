# Getting Started with FPP Extensions

This guide will help you install and configure FPP Extensions on your Minecraft server.

## Prerequisites

Before installing FPP Extensions, ensure you have:

- ✅ Minecraft server running 1.21 or higher
- ✅ Paper or Spigot server software
- ✅ Java 21 installed
- ✅ Fake Player Plugin 1.6.6.12.2 or higher installed
- ✅ LuckPerms (optional, for group/nametag extensions)

## Installation

### Option 1: Full Pack (Recommended)

The full pack includes all extensions in a single JAR file.

1. **Build or download the extension pack:**
   
   If building from source:
   ```bash
   ./gradlew clean build --no-daemon
   ```
   
   Or download the pre-built JAR from the [releases page](https://github.com/yourusername/fpp-extensions/releases).

2. **Install the extension:**
   
   Copy `fpp-spoof-1.1.0-all.jar` to your server's extensions folder:
   ```
   plugins/FakePlayerPlugin/extensions/fpp-spoof-1.1.0-all.jar
   ```

3. **Restart your server:**
   
   ```bash
   # Stop your server
   # Start your server
   ```

4. **Verify installation:**
   
   Run the following command in-game or in the console:
   ```
   /fpp extensions
   ```
   
   You should see all installed extensions listed.

### Option 2: Individual Extensions

If you only need specific extensions, you can build and install them individually.

1. **Build a specific extension:**
   
   ```bash
   ./gradlew :fpp-ping:build --no-daemon
   ```

2. **Install the extension:**
   
   Copy the JAR file to your server's extensions folder:
   ```
   plugins/FakePlayerPlugin/extensions/fpp-ping-1.1.0.jar
   ```

3. **Restart your server**

## Post-Installation

### Configuration Files

On first startup, FPP Extensions will automatically generate configuration files for each extension in:

```
plugins/FakePlayerPlugin/extensions/
├── fpp-chat/
│   └── config.yml
├── fpp-list/
│   └── config.yml
├── fpp-ping/
│   └── config.yml
├── fpp-skin/
│   └── config.yml
└── ...
```

### Reloading Extensions

You can reload extensions without restarting the server:

```
/fpp reload
```

**Note:** Some extensions may require a full server restart to function properly after configuration changes.

## Verifying Extensions Work

### Test Ping Extension

```
/fpp ping <bot-name>
```

This should display the bot's current ping value.

### Test Skin Extension

```
/fpp skin <bot-name>
```

This should display the bot's current skin information.

### Test Chat Extension

Send a chat message near a bot. If configured correctly, the bot may respond based on its chat settings.

## Troubleshooting

### Extensions Not Loading

1. Check server logs for error messages
2. Verify FPP version is 1.6.6.12.2 or higher
3. Ensure the JAR file is in the correct directory
4. Check that the file name ends with `.jar`

### Commands Not Working

1. Verify you have the required permissions
2. Check that the extension is enabled in its config.yml
3. Try running `/fpp reload`

### Configuration Not Applying

1. Ensure you edited the correct config file
2. Run `/fpp reload` after making changes
3. Check for YAML syntax errors in your config

## Next Steps

- [Configure Extensions](Configuration) - Learn about configuration options
- [Commands Reference](Commands) - View all available commands
- [Permissions](Permissions) - Set up permission nodes
- [Extensions Guide](Extensions) - Detailed information about each extension
