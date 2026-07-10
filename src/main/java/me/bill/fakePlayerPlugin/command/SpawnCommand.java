package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
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
 *
 * <p>In-game only: bots always spawn at the commanding player's own location. There is no console
 * spawning and no world/coordinate targeting — both were removed deliberately.
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
        return "[--name <name>]";
    }

    @Override
    public String getDescription() {
        return "Spawns a fake player bot at your location (auto-named bot, bot2, ... or a custom --name).";
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
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("player-only"));
            return true;
        }

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

        Location location = player.getLocation().clone();

        if (!Perm.has(sender, Perm.BYPASS_COOLDOWN) && manager.isOnCooldown(player.getUniqueId())) {
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
            manager.recordSpawnCooldown(player.getUniqueId());
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

        List<String> positional = new ArrayList<>(List.of(args));
        String typed = positional.isEmpty() ? "" : positional.getLast().toLowerCase();

        // Directly after "--name" the next token is free text.
        if (positional.size() >= 2 && "--name".equals(positional.get(positional.size() - 2))) {
            return List.of();
        }

        boolean nameUsed = positional.contains("--name");
        if (!nameUsed && "--name".startsWith(typed)) return List.of("--name");
        return List.of();
    }
}
