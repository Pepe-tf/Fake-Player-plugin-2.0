package me.bill.fakePlayerPlugin.fakeplayer;

/**
 * Display-name resolution helper for fake players.
 *
 * <p>Historically this class also produced bot chat / join / leave / kill broadcasts. The chat
 * system has been removed; only display-name resolution (used by despawn/death naming) remains.
 */
public final class BotBroadcast {

    private BotBroadcast() {}

    public static String resolveDisplayName(FakePlayer fp) {
        if (fp.getNameTagNick() != null && !fp.getNameTagNick().isEmpty()) {
            return fp.getNameTagNick();
        }
        return fp.getRawDisplayName() != null ? fp.getRawDisplayName() : fp.getDisplayName();
    }
}
