package me.bill.fakePlayerPlugin.fakeplayer;

public final class SkinProfile {

    private final String value;
    private final String signature;

    private final String source;

    public SkinProfile(String value, String signature, String source) {
        this.value = value;
        this.signature = signature;
        this.source = source != null ? source : "unknown";
    }

    public String getValue() {
        return value;
    }

    @SuppressWarnings("unused")
    public String getSignature() {
        return signature;
    }

    public String getSource() {
        return source;
    }

    public boolean isValid() {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return "SkinProfile{source='" + source + "', signed=" + (signature != null) + '}';
    }
}
