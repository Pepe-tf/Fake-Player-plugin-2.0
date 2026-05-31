package me.bill.fakePlayerPlugin.util;

import org.bukkit.attribute.Attribute;

/**
 * Version-safe attribute lookup helper.
 *
 * <p>Paper 1.21.3+ introduced shorthand enum constants without the {@code GENERIC_} prefix
 * (e.g. {@code MAX_HEALTH}). Older servers (1.21.1 and below) only have the legacy names
 * (e.g. {@code GENERIC_MAX_HEALTH}). Referencing the new constant directly in bytecode causes
 * a {@link NoSuchFieldError} at runtime on servers that don't have it yet.
 *
 * <p>This class resolves the correct constant once at class-load time via reflection so the
 * rest of the codebase can call {@link #maxHealth()} without any version branching.
 */
public final class AttributeCompat {

    /**
     * Resolved at class-load; never null unless the server is very broken.
     */
    public static final Attribute MAX_HEALTH = resolve("MAX_HEALTH", "GENERIC_MAX_HEALTH");

    private AttributeCompat() {}

    /**
     * Returns the max-health {@link Attribute} constant that exists on the current server,
     * or {@code null} if neither name could be found (should never happen on any supported version).
     */
    public static Attribute maxHealth() {
        return MAX_HEALTH;
    }

    // ── internal ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static <T extends Attribute> T resolve(String... names) {
        for (String name : names) {
            try {
                return (T) Attribute.class.getField(name).get(null);
            } catch (NoSuchFieldException ignored) {
                // try next candidate
            } catch (Exception e) {
                FppLogger.warn("AttributeCompat: unexpected error resolving '" + name + "': " + e.getMessage());
            }
        }
        FppLogger.warn("AttributeCompat: could not resolve any of: " + String.join(", ", names));
        return null;
    }
}
