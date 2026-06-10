package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppSpawnLocationProvider;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotType;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BadwordFilter;

@SuppressWarnings("unused")
public class SpawnCommand implements FppCommand {

    private final FakePlayerManager manager;

    public SpawnCommand(FakePlayerManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "spawn";
    }

    @Override
    public String getUsage() {
        return "[amount] [world [x y z]] [--name <name>] [--random-name] [--notp]";
    }

    @Override
    public String getDescription() {
        return "Spawns one or more fake player bots.";
    }

    @Override
    public String getPermission() {
        return Perm.SPAWN;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.SPAWN) || Perm.has(sender, Perm.USER_SPAWN);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        boolean isAdmin = Perm.has(sender, Perm.SPAWN);
        boolean isUser = !isAdmin && Perm.has(sender, Perm.USER_SPAWN);

        if (!isAdmin && !isUser) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        int count = 1;
        String customName = null;
        String worldName = null;
        double coordX = 0, coordY = 0, coordZ = 0;
        boolean hasCoords = false;
        boolean isConsole = !(sender instanceof Player);
        boolean forceRandomName = false;
        boolean spawnAtLastLocation = false;

        List<String> positional = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--name")) {
                if (i + 1 < args.length) {
                    customName = args[++i];
                } else {
                    sender.sendMessage(Lang.get("spawn-invalid"));
                    return true;
                }
            } else if (args[i].equalsIgnoreCase("--random-name")) {
                forceRandomName = true;
            } else if (args[i].equalsIgnoreCase("--notp")) {
                spawnAtLastLocation = true;
            } else {
                positional.add(args[i]);
            }
        }

        BotType botType = BotType.AFK;
        if (!positional.isEmpty() && BotType.isValid(positional.get(0))) {
            botType = BotType.parse(positional.get(0));
            positional.remove(0);
        }

        int idx = 0;

        if (idx < positional.size() && isInteger(positional.get(idx))) {
            count = Math.max(1, Integer.parseInt(positional.get(idx++)));
        }

        if (idx < positional.size() && !isDouble(positional.get(idx))) {
            worldName = positional.get(idx++);
            World w = Bukkit.getWorld(worldName);
            if (w == null) {
                sender.sendMessage(Lang.get("spawn-world-not-found", "world", worldName));
                return true;
            }
        }

        if (worldName != null && idx < positional.size()) {
            String next = positional.get(idx);
            if (next.contains(",")) {

                String[] parts = next.split(",", -1);
                if (parts.length != 3) {
                    sender.sendMessage(Lang.get("spawn-invalid-coords"));
                    return true;
                }
                try {
                    coordX = Double.parseDouble(parts[0]);
                    coordY = Double.parseDouble(parts[1]);
                    coordZ = Double.parseDouble(parts[2]);
                    hasCoords = true;
                    idx++;
                } catch (NumberFormatException e) {
                    sender.sendMessage(Lang.get("spawn-invalid-coords"));
                    return true;
                }
            } else if (isTildeOrDouble(next)
                    && idx + 2 < positional.size()
                    && isTildeOrDouble(positional.get(idx + 1))
                    && isTildeOrDouble(positional.get(idx + 2))) {

                Location origin = (sender instanceof Player p) ? p.getLocation() : null;
                try {
                    coordX = resolveTilde(positional.get(idx), origin != null ? origin.getX() : 0);
                    coordY = resolveTilde(positional.get(idx + 1), origin != null ? origin.getY() : 0);
                    coordZ = resolveTilde(positional.get(idx + 2), origin != null ? origin.getZ() : 0);
                    hasCoords = true;
                    idx += 3;
                } catch (NumberFormatException e) {
                    sender.sendMessage(Lang.get("spawn-invalid-coords"));
                    return true;
                }
            }
        }

        if (idx < positional.size() && isInteger(positional.get(idx))) {
            int trailing = Integer.parseInt(positional.get(idx++));
            if (trailing > 0) count = trailing;
        }

        Location location;
        if (sender instanceof Player player) {
            if (worldName != null) {
                World w = Bukkit.getWorld(worldName);
                if (w == null) {
                    sender.sendMessage(Lang.get("spawn-world-not-found", "world", worldName));
                    return true;
                }

                location = hasCoords
                        ? new Location(
                                w,
                                coordX,
                                coordY,
                                coordZ,
                                player.getLocation().getYaw(),
                                player.getLocation().getPitch())
                        : new Location(
                                w,
                                player.getLocation().getX(),
                                player.getLocation().getY(),
                                player.getLocation().getZ(),
                                player.getLocation().getYaw(),
                                player.getLocation().getPitch());
            } else {
                location = player.getLocation().clone();
            }
        } else {

            if (worldName != null) {
                World w = Bukkit.getWorld(worldName);
                if (w == null) {
                    sender.sendMessage(Lang.get("spawn-world-not-found", "world", worldName));
                    return true;
                }
                location = hasCoords ? new Location(w, coordX, coordY, coordZ) : w.getSpawnLocation();
            } else {
                List<World> worlds = Bukkit.getWorlds();
                if (worlds.isEmpty()) {
                    sender.sendMessage(Lang.get("spawn-console-no-world"));
                    return true;
                }
                location = worlds.getFirst().getSpawnLocation();
            }
        }

        if (isUser) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Lang.get("player-only"));
                return true;
            }

            if (!Perm.has(sender, Perm.BYPASS_COOLDOWN) && manager.isOnCooldown(player.getUniqueId())) {
                long remaining = manager.getRemainingCooldown(player.getUniqueId());
                sender.sendMessage(Lang.get("spawn-cooldown", "seconds", String.valueOf(remaining)));
                return true;
            }

            int permLimit = Perm.resolveUserBotLimit(sender);
            int limit = permLimit >= 0 ? permLimit : Config.userBotLimit();
            int alreadyOwned = manager.getBotsOwnedBy(player.getUniqueId()).size();
            if (alreadyOwned >= limit) {
                sender.sendMessage(Lang.get("spawn-user-limit-reached", "limit", String.valueOf(limit)));
                return true;
            }
            count = Math.min(count, limit - alreadyOwned);

            int result = manager.spawnUserBot(location, count, player, false, botType);
            if (result == -1) {
                int max = Config.maxBots();
                sender.sendMessage(Lang.get("spawn-max-reached", "max", String.valueOf(max)));
                return true;
            }
            if (result <= 0) {
                sender.sendMessage(Lang.get("spawn-no-names-left"));
                return true;
            }

            manager.recordSpawnCooldown(player.getUniqueId());
            int total = manager.getCount();
            sender.sendMessage(
                    Lang.get("spawn-success", "count", String.valueOf(result), "total", String.valueOf(total)));
            return true;
        }

        if (sender instanceof Player player
                && !Perm.has(sender, Perm.BYPASS_COOLDOWN)
                && manager.isOnCooldown(player.getUniqueId())) {
            long remaining = manager.getRemainingCooldown(player.getUniqueId());
            sender.sendMessage(Lang.get("spawn-cooldown", "seconds", String.valueOf(remaining)));
            return true;
        }

        if (count > 1 && !Perm.has(sender, Perm.SPAWN_MULTIPLE)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        if (customName != null && !Perm.has(sender, Perm.SPAWN_CUSTOM_NAME)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        boolean bypassMax = Perm.has(sender, Perm.BYPASS_MAX);
        Player spawner = (sender instanceof Player p) ? p : null;

        String originalCustomName = customName;
        if (customName != null) {
            if (Config.isBadwordFilterEnabled() && BadwordFilter.getBadwordCount() == 0) {

                sender.sendMessage(Lang.get("badword-filter-empty-warning"));
            } else if (!BadwordFilter.isAllowed(customName)) {

                if (Config.isBadwordAutoRenameEnabled()) {

                    String sanitized = BadwordFilter.sanitize(customName);
                    if (sanitized != null) {
                        customName = sanitized;
                        sender.sendMessage(Lang.get(
                                "spawn-badword-auto-prefixed", "original", originalCustomName, "name", customName));
                    } else {
                        String badword = BadwordFilter.findBadword(originalCustomName);
                        sender.sendMessage(Lang.get(
                                "spawn-badword-rejected",
                                "name",
                                originalCustomName,
                                "badword",
                                badword != null ? badword : "???"));
                        return true;
                    }
                } else {

                    String badword = BadwordFilter.findBadword(originalCustomName);
                    sender.sendMessage(Lang.get(
                            "spawn-badword-rejected",
                            "name",
                            originalCustomName,
                            "badword",
                            badword != null ? badword : "???"));
                    return true;
                }
            }
        }

        if (customName != null) {
            Player onlinePlayer = Bukkit.getPlayerExact(customName);
            if (onlinePlayer != null && manager.getByName(customName) == null) {
                sender.sendMessage(Lang.get("spawn-name-taken-player", "name", customName));
                return true;
            }
        }

        if (spawnAtLastLocation && customName != null) {
            Location lastKnown = null;
            var plugin = FakePlayerPlugin.getInstance();
            var api = plugin != null ? plugin.getFppApi() : null;
            FppSpawnLocationProvider provider = api != null ? api.getService(FppSpawnLocationProvider.class) : null;
            if (provider != null) {
                lastKnown = provider.getSpawnLocation(customName, sender);
            }
            if (lastKnown == null || lastKnown.getWorld() == null) {
                lastKnown = manager.getLastKnownLocation(customName);
            }
            if (lastKnown != null && lastKnown.getWorld() != null) {
                location = lastKnown;
            }
        }

        int result = manager.spawn(location, count, spawner, customName, bypassMax, botType, forceRandomName);

        switch (result) {
            case -1 -> {
                int max = Config.maxBots();
                sender.sendMessage(Lang.get("spawn-max-reached", "max", String.valueOf(max)));
            }
            case -2 -> sender.sendMessage(Lang.get("spawn-invalid-name"));
            case -4 -> sender.sendMessage(
                    Lang.get("spawn-name-taken-player", "name", customName != null ? customName : "?"));
            case -5 -> sender.sendMessage(
                    Lang.get("spawn-name-taken-nick", "name", customName != null ? customName : "?"));
            case 0 -> {
                if (customName != null) {
                    sender.sendMessage(Lang.get("spawn-name-taken", "name", customName));
                } else {
                    sender.sendMessage(Lang.get("spawn-no-names-left"));
                }
            }
            default -> {
                if (sender instanceof Player p) {
                    manager.recordSpawnCooldown(p.getUniqueId());
                }
                int total = manager.getCount();
                sender.sendMessage(
                        Lang.get("spawn-success", "count", String.valueOf(result), "total", String.valueOf(total)));
            }
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) return List.of();

        boolean isAdmin = Perm.has(sender, Perm.SPAWN);
        boolean isConsole = !(sender instanceof Player);
        List<String> suggestions = new ArrayList<>();

        List<String> positional = new ArrayList<>();
        boolean skipNext = false;
        for (String a : args) {
            if (skipNext) {
                skipNext = false;
                continue;
            }
            if (a.equalsIgnoreCase("--name")) {
                skipNext = true;
                continue;
            }
            if (a.equalsIgnoreCase("--random-name")) {
                continue;
            }
            if (a.equalsIgnoreCase("--notp")) {
                continue;
            }
            positional.add(a);
        }

        String typed = positional.isEmpty() ? "" : positional.getLast().toLowerCase();

        boolean typeConsumed = positional.size() >= 2 && BotType.isValid(positional.get(0));

        List<String> eff = typeConsumed ? positional.subList(1, positional.size()) : positional;
        int completedTokens = Math.max(0, eff.size() - 1);

        if (!typeConsumed && positional.size() <= 1) {

            if ("afk".startsWith(typed)) suggestions.add("afk");

            if (isAdmin) {
                Config.spawnCountPresetsAdmin().stream()
                        .filter(s -> s.startsWith(typed))
                        .forEach(suggestions::add);
            } else {
                int permLimit = Perm.resolveUserBotLimit(sender);
                int limit = permLimit >= 0 ? permLimit : Config.userBotLimit();
                for (int i = 1; i <= Math.min(limit, 10); i++) {
                    String s = String.valueOf(i);
                    if (s.startsWith(typed)) suggestions.add(s);
                }
            }

            if (isAdmin) {
                Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .filter(n -> n.toLowerCase().startsWith(typed))
                        .forEach(suggestions::add);
                if ("--name".startsWith(typed)) suggestions.add("--name");
                if ("--random-name".startsWith(typed)) suggestions.add("--random-name");
                if ("--notp".startsWith(typed)) suggestions.add("--notp");
            }
            return suggestions;
        }

        if (completedTokens == 0) {
            if (isAdmin) {
                Config.spawnCountPresetsAdmin().stream()
                        .filter(s -> s.startsWith(typed))
                        .forEach(suggestions::add);
            } else {
                int permLimit = Perm.resolveUserBotLimit(sender);
                int limit = permLimit >= 0 ? permLimit : Config.userBotLimit();
                for (int i = 1; i <= Math.min(limit, 10); i++) {
                    String s = String.valueOf(i);
                    if (s.startsWith(typed)) suggestions.add(s);
                }
            }
            if (isAdmin) {
                Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .filter(n -> n.toLowerCase().startsWith(typed))
                        .forEach(suggestions::add);
                if ("--name".startsWith(typed)) suggestions.add("--name");
                if ("--random-name".startsWith(typed)) suggestions.add("--random-name");
            }
        } else if (completedTokens == 1) {
            String prev = eff.get(completedTokens - 1);
            if (isInteger(prev)) {
                Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .filter(n -> n.toLowerCase().startsWith(typed))
                        .forEach(suggestions::add);
                if (isAdmin && "--name".startsWith(typed)) suggestions.add("--name");
            } else if (Bukkit.getWorld(prev) != null) {
                if (typed.isEmpty() || "~".startsWith(typed)) suggestions.add("~");
                if (typed.isEmpty()) suggestions.add("<x>");
                if (isAdmin && "--name".startsWith(typed)) suggestions.add("--name");
            }
        } else if (completedTokens >= 2) {
            String prevPrev = eff.get(completedTokens - 2);
            String prev = eff.get(completedTokens - 1);
            boolean prevIsCoord = isTildeOrDouble(prev);
            boolean prevPrevIsCoord = isTildeOrDouble(prevPrev);
            if (prevIsCoord && !prevPrevIsCoord) {
                if (typed.isEmpty() || "~".startsWith(typed)) suggestions.add("~");
                if (typed.isEmpty()) suggestions.add("<y>");
            } else if (prevIsCoord && prevPrevIsCoord) {
                if (typed.isEmpty() || "~".startsWith(typed)) suggestions.add("~");
                if (typed.isEmpty()) suggestions.add("<z>");
            }
            if (isAdmin && "--name".startsWith(typed)) suggestions.add("--name");
        }

        return suggestions;
    }

    private static boolean isInteger(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            int v = Integer.parseInt(s);
            return v > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDouble(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isTildeOrDouble(String s) {
        if (s == null || s.isEmpty()) return false;
        if (s.equals("~")) return true;
        if (s.startsWith("~")) {
            try {
                Double.parseDouble(s.substring(1));
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return isDouble(s);
    }

    private static double resolveTilde(String s, double playerVal) {
        if (s.equals("~")) return playerVal;
        if (s.startsWith("~")) return playerVal + Double.parseDouble(s.substring(1));
        return Double.parseDouble(s);
    }
}
