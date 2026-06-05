package me.bill.fakePlayerPlugin.command;

import java.util.List;

import org.bukkit.command.CommandSender;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppAddon;
import me.bill.fakePlayerPlugin.extension.ExtensionLoader;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.TextUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class ExtensionCommand implements FppCommand {

    private static final TextColor ACCENT = TextColor.fromHexString("#0079FF");
    private static final TextColor MUTED = NamedTextColor.DARK_GRAY;
    private static final TextColor LABEL = NamedTextColor.GRAY;
    private static final TextColor WHITE = NamedTextColor.WHITE;
    private static final TextColor OK = NamedTextColor.GREEN;

    private final FakePlayerPlugin plugin;

    public ExtensionCommand(FakePlayerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "extension";
    }

    @Override
    public List<String> getAliases() {
        return List.of("extensions");
    }

    @Override
    public String getUsage() {
        return "[--list]";
    }

    @Override
    public String getDescription() {
        return "Browse the extension marketplace or list loaded extensions.";
    }

    @Override
    public String getPermission() {
        return Perm.STATS;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        ExtensionLoader loader = plugin.getExtensionLoader();

        if (args.length > 0 && args[0].equalsIgnoreCase("--list")) {
            sendExtensionList(sender, loader);
            return true;
        }

        sendMarketplaceLink(sender);
        return true;
    }

    private void sendMarketplaceLink(CommandSender sender) {
        sender.sendMessage(TextUtil.colorize("<dark_gray><st>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</st>"));

        sender.sendMessage(Component.empty()
                .append(Component.text("  ").color(MUTED))
                .append(Component.text("ᴇxᴘʟᴏʀᴇ ").color(LABEL))
                .append(Component.text("ꜰᴘᴘ ᴇxᴛᴇɴꜱɪᴏɴꜱ").color(ACCENT).decorate(TextDecoration.BOLD))
                .append(Component.text(" ᴀᴛ ᴛʜᴇ ᴏꜰꜰɪᴄɪᴀʟ ᴍᴀʀᴋᴇᴛᴘʟᴀᴄᴇ").color(LABEL)));

        sender.sendMessage(Component.empty());

        sender.sendMessage(Component.empty()
                .append(Component.text("  ").color(MUTED))
                .append(Component.text("→ ").color(MUTED))
                .append(Component.text("ᴍᴀʀᴋᴇᴛᴘʟᴀᴄᴇ")
                        .color(ACCENT)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl("https://mp.fpp.wtf/resources"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to open the FPP Extension Marketplace")
                                .color(LABEL)))));

        sender.sendMessage(Component.empty());

        sender.sendMessage(Component.empty()
                .append(Component.text("  ").color(MUTED))
                .append(Component.text("ᴛʏᴘᴇ ").color(LABEL))
                .append(Component.text("/fpp extension --list")
                        .color(ACCENT)
                        .clickEvent(ClickEvent.runCommand("/fpp extension --list"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to list loaded extensions")
                                .color(LABEL))))
                .append(Component.text(" ꜰᴏʀ ɪɴꜱᴛᴀʟʟᴇᴅ ᴇxᴛᴇɴꜱɪᴏɴꜱ.").color(LABEL)));

        sender.sendMessage(TextUtil.colorize("<dark_gray><st>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</st>"));
    }

    private void sendExtensionList(CommandSender sender, ExtensionLoader loader) {
        List<FppAddon> extensions = loader != null ? loader.getLoadedExtensions() : List.of();

        sender.sendMessage(TextUtil.colorize("<dark_gray><st>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</st>"));

        sender.sendMessage(Component.empty()
                .append(Component.text("  ").color(MUTED))
                .append(Component.text("ʟᴏᴀᴅᴇᴅ ᴇxᴛᴇɴꜱɪᴏɴꜱ ").color(LABEL))
                .append(Component.text("(" + extensions.size() + ")").color(extensions.isEmpty() ? MUTED : OK)));

        if (extensions.isEmpty()) {
            sender.sendMessage(Component.empty());
            sender.sendMessage(Component.empty()
                    .append(Component.text("  ").color(MUTED))
                    .append(Component.text("ɴᴏ ᴇxᴛᴇɴꜱɪᴏɴꜱ ᴄᴜʀʀᴇɴᴛʟʏ ʟᴏᴀᴅᴇᴅ.").color(MUTED)));
            sender.sendMessage(Component.empty()
                    .append(Component.text("  ").color(MUTED))
                    .append(Component.text("ᴘʟᴀᴄᴇ .ᴊᴀʀ ꜰɪʟᴇꜱ ɪɴ ").color(LABEL))
                    .append(Component.text("plugins/FakePlayerPlugin/extensions/")
                            .color(WHITE))
                    .append(Component.text(" ᴀɴᴅ ʀᴇꜱᴛᴀʀᴛ.").color(LABEL)));
        } else {
            for (FppAddon ext : extensions) {
                sender.sendMessage(Component.empty());
                sender.sendMessage(Component.empty()
                        .append(Component.text("  ").color(MUTED))
                        .append(Component.text("● ").color(OK))
                        .append(Component.text(ext.getName()).color(ACCENT).decorate(TextDecoration.BOLD))
                        .append(Component.text("  v" + ext.getVersion()).color(LABEL)));

                String desc = ext.getDescription();
                if (desc != null && !desc.isBlank()) {
                    sender.sendMessage(Component.empty()
                            .append(Component.text("      ").color(MUTED))
                            .append(Component.text(desc).color(LABEL)));
                }

                List<String> authors = ext.getAuthors();
                if (authors != null && !authors.isEmpty()) {
                    sender.sendMessage(Component.empty()
                            .append(Component.text("      ").color(MUTED))
                            .append(Component.text("ʙʏ ").color(MUTED))
                            .append(Component.text(String.join(", ", authors)).color(WHITE)));
                }
            }
        }

        sender.sendMessage(TextUtil.colorize("<dark_gray><st>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</st>"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("--list");
        }
        return List.of();
    }
}
