package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.ArrayList;
import java.util.List;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;

/**
 * Computes a live, human-readable label for whatever a bot is currently doing, by directly
 * querying each task command's own "is this bot active in me" state — no separate tracked state
 * to fall out of sync, since it always reflects the current truth at render time.
 */
public final class BotActivity {

    private BotActivity() {}

    public static String currentLabel(FakePlayer fp) {
        FakePlayerPlugin plugin = FakePlayerPlugin.getInstance();
        if (plugin == null || fp == null) return "idle";

        java.util.UUID uuid = fp.getUuid();
        List<String> labels = new ArrayList<>();

        if (fp.isFrozen()) {
            labels.add("ꜰʀᴏᴢᴇɴ");
        }

        PathfindingService pathfinding = plugin.getPathfindingService();
        if (pathfinding != null && pathfinding.isNavigating(uuid)) {
            PathfindingService.Owner owner = pathfinding.getOwner(uuid);
            labels.add(navLabel(owner));
        }

        var autoEat = plugin.getAutoEatController();
        if (autoEat != null && autoEat.isEating(uuid)) labels.add("ᴇᴀᴛɪɴɢ");

        var pve = plugin.getPveController();
        if (pve != null && pve.isEngaged(uuid)) labels.add("ꜰɪɢʜᴛɪɴɢ");

        var leftClick = plugin.getLeftClickCommand();
        if (leftClick != null && leftClick.isClicking(uuid)) labels.add("ᴍɪɴɪɴɢ/ᴀᴛᴛᴀᴄᴋɪɴɢ");

        var rightClick = plugin.getRightClickCommand();
        if (rightClick != null && rightClick.isClicking(uuid)) labels.add("ᴜꜱɪɴɢ ɪᴛᴇᴍ");

        var attack = plugin.getAttackCommand();
        if (attack != null && attack.isAttacking(uuid)) labels.add("ᴀᴛᴛᴀᴄᴋɪɴɢ");

        var find = plugin.getFindCommand();
        if (find != null && find.isFinding(uuid)) labels.add("ꜱᴇᴀʀᴄʜɪɴɢ");

        var body = fp.getPlayer();
        if (body != null && body.isOnline() && body.isSneaking()) labels.add("ꜱɴᴇᴀᴋɪɴɢ");

        if (labels.isEmpty()) return "ɪᴅʟᴇ";
        return String.join(" · ", new java.util.LinkedHashSet<>(labels));
    }

    private static String navLabel(PathfindingService.Owner owner) {
        if (owner == null) return "ᴍᴏᴠɪɴɢ";
        return switch (owner) {
            case MOVE -> "ᴍᴏᴠɪɴɢ";
            case MINE -> "ᴍɪɴɪɴɢ";
            case PLACE -> "ʙᴜɪʟᴅɪɴɢ";
            case USE -> "ᴜꜱɪɴɢ ɪᴛᴇᴍ";
            case ATTACK -> "ᴄʜᴀꜱɪɴɢ ᴛᴀʀɢᴇᴛ";
            case SYSTEM -> "ᴍᴏᴠɪɴɢ";
        };
    }
}
