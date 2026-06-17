package me.bill.fakePlayerPlugin.api.personality;

public enum ActivityLevel {
    VERY_LOW(0.25),
    LOW(0.5),
    MODERATE(1.0),
    HIGH(1.5),
    VERY_HIGH(2.0);

    private final double multiplier;

    ActivityLevel(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public static ActivityLevel fromString(String value) {
        if (value == null) return MODERATE;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MODERATE;
        }
    }
}
