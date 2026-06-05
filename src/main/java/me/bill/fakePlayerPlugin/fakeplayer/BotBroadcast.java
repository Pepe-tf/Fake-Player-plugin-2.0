package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppBotDisplayService;
import me.bill.fakePlayerPlugin.api.event.FppBotChatEvent;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.util.TextUtil;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class BotBroadcast {

    private BotBroadcast() {}

    private static void send(Component msg) {
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    private static Component parseDisplayName(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        return TextUtil.colorize(raw);
    }

    private static Component buildMessage(String langKey, String displayName, String... extraArgs) {
        String template = Lang.raw(langKey, extraArgs);
        Component nameComponent = parseDisplayName(displayName);

        String withTag = template.replace("{name}", "<fpp_name>");
        String converted = TextUtil.legacyToMiniMessage(withTag);

        TagResolver nameResolver = TagResolver.resolver("fpp_name", Tag.inserting(nameComponent));
        return MiniMessage.miniMessage().deserialize(converted, nameResolver);
    }

    public static String resolveDisplayName(FakePlayer fp) {
        String raw;
        if (fp.getNameTagNick() != null && !fp.getNameTagNick().isEmpty()) {
            raw = fp.getNameTagNick();
        } else {
            raw = fp.getRawDisplayName() != null ? fp.getRawDisplayName() : fp.getDisplayName();
        }
        return decorateDisplayName(fp, raw);
    }

    private static String decorateDisplayName(FakePlayer fp, String displayName) {
        FakePlayerPlugin plugin = FakePlayerPlugin.getInstance();
        if (plugin == null || plugin.getFppApi() == null) return displayName;
        FppBotDisplayService service = plugin.getFppApi().getService(FppBotDisplayService.class);
        if (service == null) return displayName;
        try {
            String decorated = service.decorateDisplayName(new FppBotImpl(fp), displayName);
            return decorated != null && !decorated.isBlank() ? decorated : displayName;
        } catch (Throwable ignored) {
            return displayName;
        }
    }

    public static Component joinComponent(FakePlayer fp) {
        return buildMessage("bot-join", resolveDisplayName(fp));
    }

    public static Component joinComponent(String displayName) {
        return buildMessage("bot-join", displayName);
    }

    public static Component leaveComponent(FakePlayer fp) {
        return buildMessage("bot-leave", resolveDisplayName(fp));
    }

    public static Component leaveComponent(String displayName) {
        return buildMessage("bot-leave", displayName);
    }

    public static void broadcastJoin(FakePlayer fp) {
        if (!Config.joinMessage()) return;
        send(buildMessage("bot-join", resolveDisplayName(fp)));
    }

    public static void broadcastJoinByDisplayName(String displayName) {
        if (!Config.joinMessage()) return;
        send(buildMessage("bot-join", displayName));
    }

    public static void broadcastLeave(FakePlayer fp) {
        if (!Config.leaveMessage()) return;
        send(buildMessage("bot-leave", resolveDisplayName(fp)));
    }

    public static void broadcastLeaveByDisplayName(String displayName) {
        if (!Config.leaveMessage()) return;
        send(buildMessage("bot-leave", displayName));
    }

    public static void broadcastKill(String killerName, String botDisplayName) {
        if (!Config.killMessage()) return;
        send(buildMessage("bot-kill", botDisplayName, "killer", killerName));
    }

    public static void dispatchChat(Player player, String rawMessage) {
        FakePlayerPlugin pluginInst = FakePlayerPlugin.getInstance();
        if (pluginInst != null) {
            var fppApi = pluginInst.getFppApi();
            if (fppApi != null) {
                FakePlayer fp = pluginInst.getFakePlayerManager() != null
                        ? pluginInst.getFakePlayerManager().getByUuid(player.getUniqueId())
                        : null;
                if (fp != null) {
                    FppBotChatEvent chatEvt = new FppBotChatEvent(new FppBotImpl(fp), rawMessage);
                    Bukkit.getPluginManager().callEvent(chatEvt);
                    if (chatEvt.isCancelled()) return;
                    rawMessage = chatEvt.getMessage();
                }
            }
        }
        try {
            player.chat(rawMessage);
        } catch (Throwable chatError) {
            Config.debugChat("dispatchChat fallback for "
                    + player.getName()
                    + " ("
                    + chatError.getClass().getSimpleName()
                    + ": "
                    + chatError.getMessage()
                    + ")");
            dispatchLegacyChat(player, rawMessage);
        }
    }

    @SuppressWarnings("deprecation")
    private static void dispatchLegacyChat(Player player, String rawMessage) {
        Component message = Component.text(rawMessage);
        Component displayName = player.displayName();
        Set<Audience> viewers = new HashSet<>(Bukkit.getOnlinePlayers());
        viewers.add(Bukkit.getConsoleSender());
        SignedMessage signed = SignedMessage.system(rawMessage, message);
        ChatRenderer renderer = ChatRenderer.viewerUnaware((src, dn, msg) ->
                Component.empty().append(dn).append(Component.text(": ")).append(msg));
        AsyncChatEvent event = new AsyncChatEvent(false, player, viewers, renderer, message, message, signed);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        for (Audience viewer : event.viewers()) {
            viewer.sendMessage(event.renderer().render(player, displayName, event.message(), viewer));
        }
    }

    public static void broadcastRemote(
            String botName, String botDisplayName, String message, String prefix, String suffix) {
        if (!Config.fakeChatEnabled()) {
            Config.debugChat("Remote message dropped (bot chat disabled).");
            return;
        }
        String decoratedName = botDisplayName != null ? botDisplayName : botName;
        if ((prefix != null && !prefix.isBlank()) || (suffix != null && !suffix.isBlank())) {
            decoratedName = (prefix != null ? prefix : "") + decoratedName + (suffix != null ? suffix : "");
        }
        broadcastFormatted(decoratedName, message);
        Config.debugChat("Broadcast remote message from bot '" + botName + "'.");
    }

    public static void broadcastFormatted(String displayName, String message) {
        try {
            String format =
                    Config.fakeChatRemoteFormat().replace("{name}", displayName).replace("{message}", message);
            Bukkit.getServer().broadcast(MiniMessage.miniMessage().deserialize(format));
        } catch (Throwable t) {
            Bukkit.getServer()
                    .broadcast(Component.text("<")
                            .append(TextUtil.colorize(displayName))
                            .append(Component.text("> "))
                            .append(Component.text(message)));
        }
    }
}
