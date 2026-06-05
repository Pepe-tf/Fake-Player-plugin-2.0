package me.bill.fakePlayerPlugin.fakeplayer;

public enum BotType {
    AFK,

    /**
     * @deprecated PvP bot type is no longer supported; behaves identically to AFK.
     */
    @Deprecated
    PVP;

    public static BotType parse(String s) {
        if (s == null) return AFK;
        return AFK;
    }

    public static boolean isValid(String s) {
        if (s == null) return false;
        return "afk".equalsIgnoreCase(s);
    }
}
