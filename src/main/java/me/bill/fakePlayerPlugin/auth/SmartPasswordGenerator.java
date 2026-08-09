package me.bill.fakePlayerPlugin.auth;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.bill.fakePlayerPlugin.config.Config;

/**
 * Generates a new bot's login password - "smart" in that it's built to actually pass a real auth
 * plugin's password-strength policy on the first try (nLogin, AuthMe, and friends commonly reject
 * all-lowercase or too-short passwords) rather than being flagged and needing a retry, while
 * staying safe to hand to {@link BotAuthManager}'s command-dispatch as a single argument: no
 * spaces, quotes, or backslashes, and no character that could be misread as ending the auth
 * plugin's own argument parsing.
 *
 * <p>Character pools skip visually-ambiguous glyphs (0/O, 1/l/I) - the password is never actually
 * read by a human, but there's no reason to make one harder to eyeball during troubleshooting
 * (e.g. copying it out of the database by hand).
 */
final class SmartPasswordGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String LOWER = "abcdefghjkmnpqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#%^&*-_=+";

    private SmartPasswordGenerator() {}

    static String generate() {
        int length = Config.authPasswordLength();

        List<String> pools = new ArrayList<>(4);
        if (Config.authPasswordLowercase()) pools.add(LOWER);
        if (Config.authPasswordUppercase()) pools.add(UPPER);
        if (Config.authPasswordDigits()) pools.add(DIGITS);
        if (Config.authPasswordSymbols()) pools.add(SYMBOLS);
        if (pools.isEmpty()) pools.add(LOWER + DIGITS); // config with every category off - still usable

        length = Math.max(length, pools.size()); // room for at least one char per enabled pool

        List<Character> chars = new ArrayList<>(length);
        // Guarantee at least one character from every enabled pool first, so a strength check that
        // requires "at least one uppercase/digit/symbol" can never fail purely on bad luck.
        for (String pool : pools) {
            chars.add(pool.charAt(RANDOM.nextInt(pool.length())));
        }
        String combined = String.join("", pools);
        while (chars.size() < length) {
            chars.add(combined.charAt(RANDOM.nextInt(combined.length())));
        }
        Collections.shuffle(chars, RANDOM);

        StringBuilder sb = new StringBuilder(chars.size());
        for (char c : chars) sb.append(c);
        return sb.toString();
    }
}
