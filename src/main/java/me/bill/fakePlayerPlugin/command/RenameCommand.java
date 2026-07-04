package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotAccess;
import me.bill.fakePlayerPlugin.util.TextUtil;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Renames a bot's <em>display name</em> — the name shown above its head, in the tab list and in
 * command output. The login name and fb07 UUID (identity) are never changed, and the mandatory
 * "ʙᴏᴛ ʙʏ {owner}" disclosure row on the name-tag is preserved.
 */
public final class RenameCommand implements FppCommand {

    /** Longest allowed display name (visible, colour codes stripped) — keeps the name-tag readable. */
    private static final int MAX_NAME_LENGTH = 32;

    private final FakePlayerManager manager;

    public RenameCommand(FakePlayerManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "rename";
    }

    @Override
    public String getUsage() {
        return "<bot> <new name>";
    }

    @Override
    public String getDescription() {
        return "Rename a bot's display name (identity is unchanged).";
    }

    @Override
    public String getPermission() {
        return Perm.RENAME;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Lang.get("rename-usage"));
            return true;
        }

        FakePlayer fp = manager.getByName(args[0]);
        if (fp == null) {
            sender.sendMessage(Lang.get("rename-not-found", "name", args[0]));
            return true;
        }

        if (sender instanceof Player player
                && !Perm.hasOrOp(sender, Perm.ADMIN)
                && !BotAccess.canAdminister(player, fp)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        String newName =
                String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(TextUtil.colorize(newName))
                .trim();
        if (plain.isEmpty()) {
            sender.sendMessage(Lang.get("rename-invalid"));
            return true;
        }
        if (plain.length() > MAX_NAME_LENGTH) {
            sender.sendMessage(Lang.get("rename-too-long", "max", String.valueOf(MAX_NAME_LENGTH)));
            return true;
        }

        String oldName = fp.getName();
        manager.renameBot(fp, newName);
        sender.sendMessage(Lang.get("rename-success", "name", oldName, "display", plain));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return manager.getActiveNames().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return List.of();
    }
}
