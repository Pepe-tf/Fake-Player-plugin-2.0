package me.bill.fakePlayerPlugin.api.personality;

public enum ChatFrequency {
    MUTE(0.0),
    RARE(0.3),
    NORMAL(1.0),
    CHATTY(1.8),
    SPAMMY(3.0);

    private final double multiplier;

    ChatFrequency(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public static ChatFrequency fromString(String value) {
        if (value == null) return NORMAL;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
