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
 * Spawns exactly one bot per invocation - auto-named ({@code bot}, {@code bot2}, …) or custom-named
 * via {@code --name}. The old bulk form ({@code /fpp spawn <amount>}) and the bot-type tag were
 * removed deliberately: one command, one bot.
 *
 * <p>Bots spawn at the commanding player's own location by default, or wherever an admin (holding
 * {@link Perm#SPAWN}) targets with {@code --location <x> <y> <z> <world>}. Console and command-block
 * senders have no location of their own to fall back on, so {@code --location} is mandatory for them
 * - and, since they can't own bots personally, they can only use the admin tier, never
 * {@link Perm#USER_SPAWN}.
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
        return "[--name <name>] [--location <x> <y> <z> <world>]";
    }

    @Override
    public String getDescription() {
        return "Spawns a fake player bot at your location, or --location <x> <y> <z> <world> (admin"
                + " only; required from console/command blocks).";
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
        Player player = sender instanceof Player p ? p : null;

        boolean isAdmin = Perm.has(sender, Perm.SPAWN);
        boolean isUser = !isAdmin && Perm.has(sender, Perm.USER_SPAWN);

        if (!isAdmin && !isUser) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        // User-tier spawning tracks personal ownership, a bot limit, and a cooldown against a real
        // player's UUID - none of which exist for console/command-block senders, so they're admin-only.
        if (player == null && !isAdmin) {
            sender.sendMessage(Lang.get("player-only"));
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

        Location location = player != null ? player.getLocation().clone() : null;

        int locationFlag = positional.indexOf("--location");
        if (locationFlag >= 0) {
            if (!isAdmin) {
                sender.sendMessage(Lang.get("no-permission"));
                return true;
            }
            if (locationFlag + 4 >= positional.size()) {
                sender.sendMessage(Lang.get("spawn-invalid-location"));
                return true;
            }

            double x, y, z;
            try {
                x = Double.parseDouble(positional.get(locationFlag + 1));
                y = Double.parseDouble(positional.get(locationFlag + 2));
                z = Double.parseDouble(positional.get(locationFlag + 3));
            } catch (NumberFormatException e) {
                sender.sendMessage(Lang.get("spawn-invalid-location"));
                return true;
            }

            String worldName = positional.get(locationFlag + 4);
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                sender.sendMessage(Lang.get("spawn-world-not-found", "world", worldName));
                return true;
            }

            float yaw = player != null ? player.getLocation().getYaw() : 0f;
            float pitch = player != null ? player.getLocation().getPitch() : 0f;
            location = new Location(world, x, y, z, yaw, pitch);
        }

        if (location == null) {
            // Console/command-block sender that didn't pass --location - there's no player position to
            // fall back on.
            sender.sendMessage(Lang.get("spawn-invalid-location"));
            return true;
        }

        if (player != null && !Perm.has(sender, Perm.BYPASS_COOLDOWN) && manager.isOnCooldown(player.getUniqueId())) {
            long remaining = manager.getRemainingCooldown(player.getUniqueId());
            sender.sendMessage(Lang.get("spawn-cooldown", "seconds", String.valueOf(remaining)));
            return true;
        }

        if (isUser) {
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

        int result = manager.spawn(location, 1, player, customName, bypassMax, BotType.AFK);
        if (handleSpawnResult(sender, result, customName)) {
            if (player != null) manager.recordSpawnCooldown(player.getUniqueId());
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
        List<String> positional = new ArrayList<>(List.of(args));
        String typed = positional.isEmpty() ? "" : positional.getLast().toLowerCase();

        // Directly after "--name" the next token is free text.
        if (positional.size() >= 2 && "--name".equals(positional.get(positional.size() - 2))) {
            return List.of();
        }

        int locationFlag = positional.indexOf("--location");
        if (isAdmin && locationFlag >= 0 && positional.size() > locationFlag + 1) {
            int offset = positional.size() - 1 - locationFlag; // 1=x, 2=y, 3=z, 4=world
            return switch (offset) {
                case 1 -> typed.isEmpty() ? List.of("<x>") : List.of();
                case 2 -> typed.isEmpty() ? List.of("<y>") : List.of();
                case 3 -> typed.isEmpty() ? List.of("<z>") : List.of();
                case 4 -> Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .filter(n -> n.toLowerCase().startsWith(typed))
                        .toList();
                default -> List.of();
            };
        }

        List<String> suggestions = new ArrayList<>();
        boolean nameUsed = positional.contains("--name");
        if (!nameUsed && "--name".startsWith(typed)) suggestions.add("--name");
        if (isAdmin && locationFlag < 0 && "--location".startsWith(typed)) suggestions.add("--location");
        return suggestions;
    }
}
