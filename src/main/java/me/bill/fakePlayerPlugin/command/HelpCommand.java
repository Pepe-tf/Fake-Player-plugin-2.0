package me.bill.fakePlayerPlugin.command;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.gui.HelpGui;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.TextUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class HelpCommand implements FppCommand {

    private static final int PAGE_SIZE = 6;
    private static final TextColor ACCENT = TextColor.fromHexString("#0079FF");

    private final CommandManager manager;

    private volatile HelpGui helpGui = null;
    private volatile String lastLabel = "fpp";

    public HelpCommand(CommandManager manager) {
        this.manager = manager;
    }

    public void setHelpGui(HelpGui gui) {
        this.helpGui = gui;
    }

    public void setLastLabel(String label) {
        this.lastLabel = label;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getUsage() {
        return "[page]";
    }

    @Override
    public String getDescription() {
        return "Shows the command help menu.";
    }

    @Override
    public String getPermission() {
        return Perm.HELP;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {

        if (sender instanceof Player player && helpGui != null && "gui".equals(Config.helpMode())) {
            helpGui.open(player, lastLabel);
            return true;
        }

        List<FppCommand> visible =
                manager.getCommands().stream().filter(cmd -> cmd.canUse(sender)).toList();

        int totalPages = Math.max(1, (int) Math.ceil(visible.size() / (double) PAGE_SIZE));

        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }
        page = Math.min(Math.max(page, 1), totalPages);

        sender.sendMessage(Lang.get("help-header"));
        sender.sendMessage(Component.empty());

        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, visible.size());

        for (FppCommand cmd : visible.subList(from, to)) {
            String usage = cmd.getUsage().isEmpty() ? "" : " " + cmd.getUsage();
            String raw =
                    Lang.raw("help-entry", "cmd", "fpp " + cmd.getName(), "args", usage, "desc", cmd.getDescription());
            sender.sendMessage(TextUtil.colorize(raw));
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(buildPaginationBar(page, totalPages));
        sender.sendMessage(Lang.get("help-footer"));
        return true;
    }

    private Component buildPaginationBar(int page, int totalPages) {

        Component prev;
        if (page > 1) {
            prev = Component.text("« ᴘʀᴇᴠ")
                    .color(ACCENT)
                    .decoration(TextDecoration.BOLD, true)
                    .clickEvent(ClickEvent.runCommand("/fpp help " + (page - 1)))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Go to page " + (page - 1)).color(NamedTextColor.GRAY)));
        } else {
            prev = Component.text("« ᴘʀᴇᴠ").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, true);
        }

        Component pageInfo =
                Component.text("  " + page + " / " + totalPages + "  ").color(NamedTextColor.WHITE);

        Component next;
        if (page < totalPages) {
            next = Component.text("ɴᴇxᴛ »")
                    .color(ACCENT)
                    .decoration(TextDecoration.BOLD, true)
                    .clickEvent(ClickEvent.runCommand("/fpp help " + (page + 1)))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Go to page " + (page + 1)).color(NamedTextColor.GRAY)));
        } else {
            next = Component.text("ɴᴇxᴛ »").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, true);
        }

        return Component.text(" ").append(prev).append(pageInfo).append(next);
    }
}
