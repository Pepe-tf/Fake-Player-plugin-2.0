package me.bill.fakePlayerPlugin.api.personality;

import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Personality {

    public static final double MAX_TRAIT_VALUE = 1.0;
    public static final double MIN_TRAIT_VALUE = -1.0;

    private final String name;
    private final double friendliness;
    private final double toxicity;
    private final double helpfulness;
    private final double talkativeness;
    private final double humor;
    private final double aggression;
    private final double curiosity;
    private final double loyalty;
    private final Set<String> tags;

    public Personality(
            @NotNull String name,
            double friendliness,
            double toxicity,
            double helpfulness,
            double talkativeness,
            double humor,
            double aggression,
            double curiosity,
            double loyalty,
            @Nullable Set<String> tags) {
        this.name = name.toLowerCase(Locale.ROOT);
        this.friendliness = clamp(friendliness);
        this.toxicity = clamp(toxicity);
        this.helpfulness = clamp(helpfulness);
        this.talkativeness = clamp(talkativeness);
        this.humor = clamp(humor);
        this.aggression = clamp(aggression);
        this.curiosity = clamp(curiosity);
        this.loyalty = clamp(loyalty);
        this.tags = tags != null ? Set.copyOf(tags) : Set.of();
    }

    public static @NotNull Personality neutral() {
        return new Personality("neutral", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Set.of("balanced"));
    }

    public static @NotNull Personality friendly() {
        return new Personality("friendly", 0.8, -0.3, 0.5, 0.3, 0.4, -0.4, 0.2, 0.5, Set.of("friendly"));
    }

    public static @NotNull Personality toxic() {
        return new Personality("toxic", -0.6, 0.9, -0.4, 0.2, 0.3, 0.6, -0.2, -0.5, Set.of("toxic"));
    }

    public static @NotNull Personality quiet() {
        return new Personality("quiet", 0.1, -0.2, 0.0, -0.8, 0.0, -0.2, -0.1, 0.2, Set.of("quiet"));
    }

    public static @NotNull Personality helpful() {
        return new Personality("helpful", 0.6, -0.5, 0.9, 0.2, 0.1, -0.3, 0.4, 0.6, Set.of("helpful"));
    }

    public static @NotNull Personality builder() {
        return new Personality("builder", 0.2, -0.2, 0.3, 0.0, 0.1, -0.1, 0.3, 0.2, Set.of("builder"));
    }

    public static @NotNull Personality explorer() {
        return new Personality("explorer", 0.2, -0.1, 0.1, 0.1, 0.2, 0.0, 0.8, 0.1, Set.of("explorer"));
    }

    public @NotNull String getName() {
        return name;
    }

    public double getFriendliness() {
        return friendliness;
    }

    public double getToxicity() {
        return toxicity;
    }

    public double getHelpfulness() {
        return helpfulness;
    }

    public double getTalkativeness() {
        return talkativeness;
    }

    public double getHumor() {
        return humor;
    }

    public double getAggression() {
        return aggression;
    }

    public double getCuriosity() {
        return curiosity;
    }

    public double getLoyalty() {
        return loyalty;
    }

    public @NotNull Set<String> getTags() {
        return tags;
    }

    public boolean hasTag(@NotNull String tag) {
        return tags.contains(tag.toLowerCase(Locale.ROOT));
    }

    public double chatFrequencyMultiplier() {
        return 1.0 + talkativeness * 0.5;
    }

    private static double clamp(double value) {
        return Math.max(MIN_TRAIT_VALUE, Math.min(MAX_TRAIT_VALUE, value));
    }

    @Override
    public String toString() {
        return "Personality{" + "name='" + name + '\'' + '}';
    }
}
