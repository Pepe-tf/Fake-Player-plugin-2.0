package me.bill.fakePlayerPlugin.util;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates realistic Minecraft-style usernames using syllable combinations,
 * common gaming prefixes/suffixes, and occasional numbers.
 *
 * <p>The generator produces names that look like real player names rather than
 * artificial "Bot1234" patterns. It supports several name styles:</p>
 *
 * <ul>
 *   <li>Syllable-based names (e.g., Glarion, Vexon)</li>
 *   <li>Prefix + base (e.g., ProGamer, TheViper)</li>
 *   <li>Base + suffix number (e.g., Notch_2011)</li>
 *   <li>Gaming tags (e.g., xXShadowXx)</li>
 *   <li>Compound words (e.g., DarkKnight)</li>
 * </ul>
 */
public final class RandomNameGenerator {

    private RandomNameGenerator() {}

    private static final List<String> CONSONANTS = List.of(
            "b", "c", "d", "f", "g", "h", "j", "k", "l", "m", "n", "p", "q", "r", "s", "t", "v", "w", "x", "y", "z");

    private static final List<String> VOWELS = List.of("a", "e", "i", "o", "u");

    private static final List<String> SYLLABLE_ENDS = List.of("r", "n", "s", "l", "t", "k", "m", "x", "z", "p", "", "");

    private static final List<String> PREFIXES = List.of(
            "Pro", "The", "Mr", "Its", "i", "xX", "Im", "Dark", "Light", "Super", "Mega", "Ultra", "Hyper", "Shadow",
            "Night", "Fire", "Ice", "Storm", "Thunder", "Golden", "Silver", "Iron", "Diamond");

    private static final List<String> SUFFIXES = List.of("YT", "TV", "HD", "XD", "OP", "GG", "PvP", "MC", "_");

    private static final List<String> FIRST_NAMES = List.of(
            "Alex", "Jordan", "Taylor", "Morgan", "Casey", "Riley", "Jamie", "Quinn", "Avery", "Skyler", "Dakota",
            "Reese", "Rowan", "Sage", "Kai", "Zion", "Nova", "Orion", "Jasper", "Felix", "Silas", "Atlas", "Cedar",
            "Flint", "Hawk", "Wolf", "Bear", "Fox", "Drake", "Blaze", "Cobra", "Falcon", "Raven", "Viper", "Phantom",
            "Ghost", "Spectre", "Wraith", "Reaper", "Hunter", "Ranger", "Scout", "Knight", "Paladin", "Rogue", "Mage",
            "Wizard", "Archer", "Sniper");

    private static final List<String> ADJECTIVES = List.of(
            "Swift",
            "Silent",
            "Deadly",
            "Brave",
            "Bold",
            "Wild",
            "Free",
            "Lucky",
            "Crazy",
            "Mad",
            "Epic",
            "Pro",
            "Elite",
            "Grand",
            "Royal",
            "True",
            "Real",
            "Raw",
            "Pure",
            "Hard",
            "Solid",
            "Fast",
            "Quick",
            "Slick",
            "Sneaky",
            "Stealthy",
            "Fierce",
            "Cruel");

    private static final List<String> NOUNS = List.of(
            "Wolf", "Bear", "Eagle", "Hawk", "Falcon", "Raven", "Crow", "Snake", "Viper", "Cobra", "Dragon", "Tiger",
            "Lion", "Panther", "Shark", "Blaze", "Storm", "Thunder", "Shadow", "Spirit", "Soul", "Heart", "Mind",
            "Edge", "Blade", "Sword", "Shield", "Arrow", "Bolt", "Spark", "Flame", "Frost", "Crystal", "Stone",
            "Steel");

    /**
     * Generates a random Minecraft-style username.
     *
     * @return a name matching {@code [a-zA-Z0-9_]+} with length 3–16
     */
    public static String generate() {
        int style = ThreadLocalRandom.current().nextInt(100);

        String name;
        if (style < 20) {
            name = generateSyllableName();
        } else if (style < 35) {
            name = generatePrefixBaseName();
        } else if (style < 50) {
            name = generateAdjectiveNounName();
        } else if (style < 60) {
            name = generateFirstNameStyle();
        } else if (style < 70) {
            name = generateGamingTagName();
        } else if (style < 80) {
            name = generateCompoundSyllableName();
        } else if (style < 90) {
            name = generateBaseWithSuffix();
        } else {
            name = generateShortRandomName();
        }

        name = sanitizeName(name);

        if (name.length() < 3 || name.length() > 16 || !name.matches("[a-zA-Z0-9_]+")) {
            return fallbackName();
        }
        return name;
    }

    private static String generateSyllableName() {
        int syllables = ThreadLocalRandom.current().nextInt(2, 5);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < syllables; i++) {
            sb.append(randomConsonant());
            sb.append(randomVowel());
            if (ThreadLocalRandom.current().nextBoolean()) {
                sb.append(randomConsonant());
            }
        }
        return capitalize(sb.toString());
    }

    private static String generatePrefixBaseName() {
        String prefix = PREFIXES.get(ThreadLocalRandom.current().nextInt(PREFIXES.size()));
        String base;
        if (ThreadLocalRandom.current().nextBoolean()) {
            base = FIRST_NAMES.get(ThreadLocalRandom.current().nextInt(FIRST_NAMES.size()));
        } else {
            base = generateSyllableName();
        }
        String combined = prefix + base;
        if (combined.length() > 14 && ThreadLocalRandom.current().nextBoolean()) {
            combined = prefix + base.substring(0, Math.min(base.length(), 16 - prefix.length()));
        }
        return combined;
    }

    private static String generateAdjectiveNounName() {
        String adj = ADJECTIVES.get(ThreadLocalRandom.current().nextInt(ADJECTIVES.size()));
        String noun = NOUNS.get(ThreadLocalRandom.current().nextInt(NOUNS.size()));
        String combined = adj + noun;
        if (combined.length() > 16) {
            combined = adj.substring(0, Math.min(adj.length(), 8)) + noun.substring(0, Math.min(noun.length(), 8));
        }
        return combined;
    }

    private static String generateFirstNameStyle() {
        String base = FIRST_NAMES.get(ThreadLocalRandom.current().nextInt(FIRST_NAMES.size()));
        if (ThreadLocalRandom.current().nextInt(3) == 0) {
            base += ThreadLocalRandom.current().nextInt(10, 100);
        }
        return base;
    }

    private static String generateGamingTagName() {
        if (ThreadLocalRandom.current().nextBoolean()) {
            String base = generateSyllableName().toLowerCase();
            return "xX" + capitalize(base) + "Xx";
        }
        String base = generateSyllableName();
        return base + "_" + ThreadLocalRandom.current().nextInt(10, 1000);
    }

    private static String generateCompoundSyllableName() {
        String first = generateSyllableName();
        String second = generateSyllableName();
        String combined = first + second;
        if (combined.length() > 16) {
            combined =
                    first.substring(0, Math.min(first.length(), 8)) + second.substring(0, Math.min(second.length(), 8));
        }
        return combined;
    }

    private static String generateBaseWithSuffix() {
        String base;
        if (ThreadLocalRandom.current().nextBoolean()) {
            base = FIRST_NAMES.get(ThreadLocalRandom.current().nextInt(FIRST_NAMES.size()));
        } else {
            base = generateSyllableName();
        }
        String suffix = SUFFIXES.get(ThreadLocalRandom.current().nextInt(SUFFIXES.size()));
        if (suffix.equals("_")) {
            suffix = "_" + ThreadLocalRandom.current().nextInt(1, 1000);
        }
        String combined = base + suffix;
        if (combined.length() > 16) {
            combined = base.substring(0, Math.min(base.length(), 16 - suffix.length())) + suffix;
        }
        return combined;
    }

    private static String generateShortRandomName() {
        int length = ThreadLocalRandom.current().nextInt(4, 10);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (ThreadLocalRandom.current().nextBoolean()) {
                sb.append(randomConsonant());
            } else {
                sb.append(randomVowel());
            }
        }
        if (ThreadLocalRandom.current().nextBoolean()) {
            sb.append(ThreadLocalRandom.current().nextInt(1, 100));
        }
        return capitalize(sb.toString());
    }

    private static String fallbackName() {
        return "Player" + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    private static String randomConsonant() {
        return CONSONANTS.get(ThreadLocalRandom.current().nextInt(CONSONANTS.size()));
    }

    private static String randomVowel() {
        return VOWELS.get(ThreadLocalRandom.current().nextInt(VOWELS.size()));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private static String sanitizeName(String name) {
        if (name == null || name.isEmpty()) return name;

        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '_') {
                sb.append(c);
            }
        }

        String result = sb.toString();
        if (result.length() > 16) {
            result = result.substring(0, 16);
        }
        return result;
    }
}
