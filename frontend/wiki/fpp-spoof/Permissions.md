# FPP Extensions Permissions

Complete permission reference for all FPP Extensions.

## Permission Format

All FPP Extensions permissions follow this format:

```
fpp.<extension>.<action>
```

Example: `fpp.ping.set`, `fpp.skin.random`

## Permission Tables

### fpp-aichat Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.aichat` | Use AI chat features | op |
| `fpp.aichat.configure` | Configure AI settings | op |
| `fpp.aichat.personality` | Change bot personalities | op |

### fpp-chat Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.chat` | Enable bot chat | op |
| `fpp.chat.configure` | Configure chat settings | op |

### fpp-command Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.command` | Use command extension | op |
| `fpp.command.execute` | Execute commands as bots | op |
| `fpp.command.configure` | Configure command settings | op |

### fpp-groups Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.groups` | Use group management | op |
| `fpp.groups.create` | Create groups | op |
| `fpp.groups.delete` | Delete groups | op |
| `fpp.groups.modify` | Modify group membership | op |
| `fpp.groups.view` | View group information | true |

### fpp-list Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.list` | Use tab list features | op |
| `fpp.list.configure` | Configure tab list settings | op |

### fpp-luckperms Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.luckperms` | Use LuckPerms integration | op |
| `fpp.luckperms.group` | Assign groups to bots | op |
| `fpp.luckperms.sync` | Sync permissions | op |

### fpp-nametag Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.nametag` | Use nametag features | op |
| `fpp.nametag.custom` | Set custom nametags | op |
| `fpp.nametag.configure` | Configure nametag settings | op |

### fpp-peaks Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.peaks` | View performance stats | op |
| `fpp.peaks.detailed` | View detailed statistics | op |

### fpp-ping Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.ping` | View ping values | true |
| `fpp.ping.set` | Set ping values | op |
| `fpp.ping.random` | Use random ping | op |
| `fpp.ping.bulk` | Use bulk operations | op |
| `fpp.ping.reset` | Reset ping to default | op |

### fpp-skin Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.skin` | View skin information | true |
| `fpp.skin.set` | Set skin URLs | op |
| `fpp.skin.random` | Use random skins | op |
| `fpp.skin.bulk` | Use bulk operations | op |
| `fpp.skin.reset` | Reset skin to default | op |
| `fpp.skin.custom-url` | Use custom skin URLs | op |

### fpp-swap Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.swap` | Use swap commands | op |

### fpp-waypoints Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `fpp.waypoints` | Use waypoint system | op |
| `fpp.waypoints.create` | Create waypoints | op |
| `fpp.waypoints.delete` | Delete waypoints | op |
| `fpp.waypoints.patrol` | Manage patrols | op |
| `fpp.waypoints.view` | View waypoints | true |

## Permission Defaults Explained

### Default: true

These permissions are granted to all players by default:

- `fpp.ping` - Anyone can view ping values
- `fpp.skin` - Anyone can view skin information
- `fpp.groups.view` - Anyone can view group information
- `fpp.waypoints.view` - Anyone can view waypoints

### Default: op

These permissions are granted only to operators by default:

- All `.set`, `.configure`, `.create`, `.delete`, `.modify` actions
- All bulk operations
- All administrative functions

## Setting Up Permissions

### With LuckPerms

```
# Grant ping view to all players
lp group default permission set fpp.ping true

# Grant ping set to admins only
lp group admin permission set fpp.ping.set true

# Grant skin management to moderators
lp group moderator permission set fpp.skin.set true
lp group moderator permission set fpp.skin.random true

# Grant full waypoint access to builders
lp group builder permission set fpp.waypoints.* true
```

### With PermissionsEx

```yaml
groups:
  default:
    permissions:
      - fpp.ping
      - fpp.skin
  moderator:
    permissions:
      - fpp.ping.*
      - fpp.skin.*
      - fpp.chat
  admin:
    permissions:
      - fpp.*
```

### With GroupManager

```yaml
groups:
  Default:
    permissions:
      - fpp.ping
      - fpp.skin
  Staff:
    permissions:
      - fpp.ping.*
      - fpp.skin.*
      - fpp.command.*
  Admin:
    permissions:
      - fpp.*
```

## Wildcard Permissions

### Extension-Specific Wildcards

Each extension supports wildcard permissions:

```
fpp.ping.*        # All ping permissions
fpp.skin.*        # All skin permissions
fpp.waypoints.*   # All waypoint permissions
```

### Global Wildcard

```
fpp.*             # All FPP Extensions permissions
```

**Note:** Use wildcards carefully. Grant only what's needed.

## Permission Nodes by Use Case

### Basic Player Permissions

For regular players who should be able to view information:

```
fpp.ping
fpp.skin
fpp.groups.view
fpp.waypoints.view
```

### Moderator Permissions

For moderators who need basic management:

```
fpp.ping.*
fpp.skin.*
fpp.chat
fpp.command.execute
fpp.groups.view
fpp.groups.modify
```

### Admin Permissions

For full administrative access:

```
fpp.*
```

### Builder Permissions

For builders who manage waypoints:

```
fpp.waypoints.*
fpp.ping
fpp.skin
```

## Troubleshooting Permissions

### Permission Not Working

1. **Check permission plugin** - Ensure LuckPerms/PEX is loaded
2. **Verify permission node** - Check for typos
3. **Check inheritance** - Parent groups may override
4. **Reload permissions** - Run `/lp reload` or equivalent
5. **Test with op** - If it works with op, it's a permission issue

### Check Player Permissions

**LuckPerms:**
```
lp user <player> permission check fpp.ping.set
lp user <player> permission info
```

**PermissionsEx:**
```
/pex user <player> info
```

### Debug Permission Issues

1. Enable permission logging in your permission plugin
2. Check server logs for permission denials
3. Use `/fpp permissions` command if available
4. Test with a fresh permission grant

## Best Practices

1. **Principle of Least Privilege** - Grant only necessary permissions
2. **Use Groups** - Assign permissions to groups, not individual players
3. **Regular Audits** - Review permissions periodically
4. **Document Changes** - Track permission changes
5. **Test Thoroughly** - Test permissions in a safe environment first

## Security Considerations

### High-Risk Permissions

These permissions should be restricted to trusted admins:

- `fpp.command.execute` - Can execute commands as bots
- `fpp.skin.custom-url` - Can set arbitrary URLs
- `fpp.aichat.configure` - Can modify AI settings
- `fpp.luckperms.group` - Can change bot permissions

### Recommended Restrictions

```yaml
# Don't give to regular staff
fpp.command.execute: admin-only
fpp.aichat.configure: admin-only
fpp.luckperms.*: admin-only

# OK for trusted moderators
fpp.ping.*: moderator+
fpp.skin.*: moderator+
fpp.groups.modify: moderator+
```

## Permission Inheritance

If using group inheritance:

```
default → vip → moderator → admin
```

Permissions flow upward. Admin gets all permissions from lower groups.

Example LuckPerms setup:
```
lp group vip parent set default
lp group moderator parent set vip
lp group admin parent set moderator
```

## Dynamic Permissions

Some permissions can be context-aware:

### World-Specific Permissions

```
lp group builder permission set fpp.waypoints.create true world:creative
```

### Server-Specific Permissions

```
lp group player permission set fpp.ping true server:minigames
```

## Quick Reference Card

### Most Common Permissions

```
fpp.ping              # View ping
fpp.ping.set          # Set ping
fpp.skin              # View skins
fpp.skin.set          # Set skins
fpp.command.execute   # Run commands
fpp.waypoints.create  # Create waypoints
fpp.groups.modify     # Manage groups
```

### Full Access

```
fpp.*                 # All extensions
```

### View Only

```
fpp.ping
fpp.skin
fpp.groups.view
fpp.waypoints.view
fpp.peaks
```
