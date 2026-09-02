package me.bill.fakePlayerPlugin.fakeplayer;

/**
 * Bots intentionally carry no skin identity. Returning an invalid {@link SkinProfile} makes every
 * call site fall back to the client's standard default Steve/Alex model (UUID-parity based) instead
 * of any specific, identifiable real player's actual signed skin - bots must never be able to visually
 * impersonate a real Minecraft account.
 */
public final class BotSkin {

    private BotSkin() {}

    private static final SkinProfile NONE = new SkinProfile(null, null, "vanilla-default");

    public static SkinProfile fixed() {
        return NONE;
    }
}
