package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotType;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;

/**
 * Spawns exactly one bot per invocation — auto-named ({@code bot}, {@code bot2}, …) or custom-named
 * via {@code --name}. The old bulk form ({@code /fpp spawn <amount>}) and the bot-type tag were
 * removed deliberately: one command, one bot.
 */
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
        return "[--name <name>] [world [x y z]]";
    }

    @Override
    public String getDescription() {
        return "Spawns a fake player bot (auto-named bot, bot2, ... or a custom --name).";
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

        List<String> positional = new ArrayList<>(List.of(args));

        String customName = null;
        int nameFlag = positional.indexOf("--name");
        if (nameFlag >= 0) {
            if (nameFlag + 1 >= positional.size()) {
                sender.sendMessage(Lang.get("spawn-invalid-name", "name", ""));
                return true;
            }
            customName = positional.get(nameFlag + 1);
            positional.remove(nameFlag + 1);
            positional.remove(nameFlag);
        }

        String worldName = null;
        double coordX = 0, coordY = 0, coordZ = 0;
        boolean hasCoords = false;
        int idx = 0;

        if (idx < positional.size() && !isDouble(positional.get(idx))) {
            if (!Perm.has(sender, Perm.SPAWN_COORDS)) {
                sender.sendMessage(Lang.get("no-permission"));
                return true;
            }
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
                } catch (NumberFormatException e) {
                    sender.sendMessage(Lang.get("spawn-invalid-coords"));
                    return true;
                }
            }
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

        if (sender instanceof Player player
                && !Perm.has(sender, Perm.BYPASS_COOLDOWN)
                && manager.isOnCooldown(player.getUniqueId())) {
            long remaining = manager.getRemainingCooldown(player.getUniqueId());
            sender.sendMessage(Lang.get("spawn-cooldown", "seconds", String.valueOf(remaining)));
            return true;
        }

        if (isUser) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Lang.get("player-only"));
                return true;
            }

            int permLimit = Perm.resolveUserBotLimit(sender);
            int limit = permLimit >= 0 ? permLimit : Config.userBotLimit();
            int alreadyOwned = manager.getBotsOwnedBy(player.getUniqueId()).size();
            if (alreadyOwned >= limit) {
                sender.sendMessage(Lang.get("spawn-user-limit-reached", "limit", String.valueOf(limit)));
                return true;
            }

            int result = customName != null
                    ? manager.spawn(location, 1, player, customName, false, BotType.AFK)
                    : manager.spawnUserBot(location, 1, player, false, BotType.AFK);
            if (handleSpawnResult(sender, result, customName)) {
                manager.recordSpawnCooldown(player.getUniqueId());
                sender.sendMessage(
                        Lang.get("spawn-success", "count", "1", "total", String.valueOf(manager.getCount())));
            }
            return true;
        }

        boolean bypassMax = Perm.has(sender, Perm.BYPASS_MAX);
        Player spawner = (sender instanceof Player p) ? p : null;

        int result = manager.spawn(location, 1, spawner, customName, bypassMax, BotType.AFK);
        if (handleSpawnResult(sender, result, customName)) {
            if (sender instanceof Player p) {
                manager.recordSpawnCooldown(p.getUniqueId());
            }
            sender.sendMessage(Lang.get("spawn-success", "count", "1", "total", String.valueOf(manager.getCount())));
        }
        return true;
    }

    /** Sends the failure message for a spawn result code; returns true when the spawn succeeded. */
    private boolean handleSpawnResult(CommandSender sender, int result, String customName) {
        switch (result) {
            case -1 -> sender.sendMessage(Lang.get("spawn-max-reached", "max", String.valueOf(Config.maxBots())));
            case -2 -> sender.sendMessage(Lang.get("spawn-invalid-name", "name", String.valueOf(customName)));
            case -4 -> sender.sendMessage(Lang.get("spawn-name-online", "name", String.valueOf(customName)));
            case 0 -> sender.sendMessage(
                    customName != null
                            ? Lang.get("spawn-name-taken", "name", customName)
                            : Lang.get("spawn-no-names-left"));
            default -> {
                return result > 0;
            }
        }
        return false;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) return List.of();

        boolean isAdmin = Perm.has(sender, Perm.SPAWN);
        List<String> suggestions = new ArrayList<>();

        List<String> positional = new ArrayList<>(List.of(args));
        String typed = positional.isEmpty() ? "" : positional.getLast().toLowerCase();

        // Directly after "--name" the next token is free text.
        if (positional.size() >= 2 && "--name".equals(positional.get(positional.size() - 2))) {
            return List.of();
        }

        boolean nameUsed = positional.contains("--name");
        if (!nameUsed && "--name".startsWith(typed)) suggestions.add("--name");

        // Strip a completed "--name <value>" pair before reasoning about positional args.
        int nameFlag = positional.indexOf("--name");
        if (nameFlag >= 0 && nameFlag + 1 < positional.size()) {
            positional.remove(nameFlag + 1);
            positional.remove(nameFlag);
        }
        int completedTokens = Math.max(0, positional.size() - 1);

        if (completedTokens == 0) {
            if (isAdmin) {
                Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .filter(n -> n.toLowerCase().startsWith(typed))
                        .forEach(suggestions::add);
            }
        } else if (completedTokens == 1) {
            String prev = positional.get(completedTokens - 1);
            if (Bukkit.getWorld(prev) != null) {
                if (typed.isEmpty() || "~".startsWith(typed)) suggestions.add("~");
                if (typed.isEmpty()) suggestions.add("<x>");
            }
        } else {
            String prevPrev = positional.get(completedTokens - 2);
            String prev = positional.get(completedTokens - 1);
            boolean prevIsCoord = isTildeOrDouble(prev);
            boolean prevPrevIsCoord = isTildeOrDouble(prevPrev);
            if (prevIsCoord && !prevPrevIsCoord) {
                if (typed.isEmpty() || "~".startsWith(typed)) suggestions.add("~");
                if (typed.isEmpty()) suggestions.add("<y>");
            } else if (prevIsCoord && prevPrevIsCoord) {
                if (typed.isEmpty() || "~".startsWith(typed)) suggestions.add("~");
                if (typed.isEmpty()) suggestions.add("<z>");
            }
        }

        return suggestions;
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
