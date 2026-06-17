package me.bill.fakePlayerPlugin.api.personality;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BotProfile {

    public static final String EXTENSION_KEY = "fpp-personality";
    public static final int SCHEMA_VERSION = 1;

    private final UUID botUuid;
    private final String displayName;
    private final String nickname;
    private final String ageGroup;
    private final Personality personality;
    private final Set<String> interests;
    private final Set<String> preferredTopics;
    private final ActivityLevel activityLevel;
    private final ChatFrequency chatFrequency;
    private final Map<UUID, Double> friendships;
    private final Map<UUID, Double> rivalries;
    private final String aiPromptModifier;
    private final String swapGroup;
    private final boolean peakParticipation;
    private final SleepSchedule sleepSchedule;
    private final Set<String> recentConversationRefs;
    private final Map<String, String> customProperties;
    private final String skinPreset;
    private final Set<String> skinTags;
    private final String preferredSkinSource;
    private final int schemaVersion;
    private final long lastModified;

    private BotProfile(Builder builder) {
        this.botUuid = Objects.requireNonNull(builder.botUuid, "botUuid");
        this.displayName = builder.displayName;
        this.nickname = builder.nickname;
        this.ageGroup = builder.ageGroup;
        this.personality = builder.personality != null ? builder.personality : Personality.neutral();
        this.interests = Set.copyOf(builder.interests);
        this.preferredTopics = Set.copyOf(builder.preferredTopics);
        this.activityLevel = builder.activityLevel != null ? builder.activityLevel : ActivityLevel.MODERATE;
        this.chatFrequency = builder.chatFrequency != null ? builder.chatFrequency : ChatFrequency.NORMAL;
        this.friendships = Map.copyOf(builder.friendships);
        this.rivalries = Map.copyOf(builder.rivalries);
        this.aiPromptModifier = builder.aiPromptModifier;
        this.swapGroup = builder.swapGroup;
        this.peakParticipation = builder.peakParticipation;
        this.sleepSchedule = builder.sleepSchedule != null ? builder.sleepSchedule : SleepSchedule.defaultSchedule();
        this.recentConversationRefs = Set.copyOf(builder.recentConversationRefs);
        this.customProperties = Map.copyOf(builder.customProperties);
        this.skinPreset = builder.skinPreset;
        this.skinTags = Set.copyOf(builder.skinTags);
        this.preferredSkinSource = builder.preferredSkinSource;
        this.schemaVersion = builder.schemaVersion > 0 ? builder.schemaVersion : SCHEMA_VERSION;
        this.lastModified = builder.lastModified > 0 ? builder.lastModified : System.currentTimeMillis();
    }

    public static Builder builder(@NotNull UUID botUuid) {
        return new Builder(botUuid);
    }

    public Builder toBuilder() {
        return new Builder(botUuid)
                .displayName(displayName)
                .nickname(nickname)
                .ageGroup(ageGroup)
                .personality(personality)
                .interests(interests)
                .preferredTopics(preferredTopics)
                .activityLevel(activityLevel)
                .chatFrequency(chatFrequency)
                .friendships(friendships)
                .rivalries(rivalries)
                .aiPromptModifier(aiPromptModifier)
                .swapGroup(swapGroup)
                .peakParticipation(peakParticipation)
                .sleepSchedule(sleepSchedule)
                .recentConversationRefs(recentConversationRefs)
                .customProperties(customProperties)
                .skinPreset(skinPreset)
                .skinTags(skinTags)
                .preferredSkinSource(preferredSkinSource)
                .schemaVersion(schemaVersion)
                .lastModified(lastModified);
    }

    public static Builder builderFrom(@NotNull UUID botUuid, @NotNull BotProfile template) {
        return template.toBuilder().botUuidBuilder(botUuid);
    }

    public @NotNull UUID getBotUuid() {
        return botUuid;
    }

    public @Nullable String getDisplayName() {
        return displayName;
    }

    public @Nullable String getNickname() {
        return nickname;
    }

    public @Nullable String getAgeGroup() {
        return ageGroup;
    }

    public @NotNull Personality getPersonality() {
        return personality;
    }

    public @NotNull Set<String> getInterests() {
        return interests;
    }

    public @NotNull Set<String> getPreferredTopics() {
        return preferredTopics;
    }

    public @NotNull ActivityLevel getActivityLevel() {
        return activityLevel;
    }

    public @NotNull ChatFrequency getChatFrequency() {
        return chatFrequency;
    }

    public @NotNull Map<UUID, Double> getFriendships() {
        return friendships;
    }

    public @NotNull Map<UUID, Double> getRivalries() {
        return rivalries;
    }

    public double getAffinity(@NotNull UUID other) {
        double friend = friendships.getOrDefault(other, 0.0);
        double rival = rivalries.getOrDefault(other, 0.0);
        return friend - rival;
    }

    public boolean isFriend(@NotNull UUID other) {
        return friendships.getOrDefault(other, 0.0) > 0.5;
    }

    public boolean isRival(@NotNull UUID other) {
        return rivalries.getOrDefault(other, 0.0) > 0.5;
    }

    public @Nullable String getAiPromptModifier() {
        return aiPromptModifier;
    }

    public @Nullable String getSwapGroup() {
        return swapGroup;
    }

    public boolean isPeakParticipation() {
        return peakParticipation;
    }

    public @NotNull SleepSchedule getSleepSchedule() {
        return sleepSchedule;
    }

    public @NotNull Set<String> getRecentConversationRefs() {
        return recentConversationRefs;
    }

    public @NotNull Map<String, String> getCustomProperties() {
        return customProperties;
    }

    public @Nullable String getCustomProperty(@NotNull String key) {
        return customProperties.get(key);
    }

    public @Nullable String getSkinPreset() {
        return skinPreset;
    }

    public @NotNull Set<String> getSkinTags() {
        return skinTags;
    }

    public @Nullable String getPreferredSkinSource() {
        return preferredSkinSource;
    }

    public boolean hasSkinTag(@NotNull String tag) {
        return skinTags.contains(tag.toLowerCase(Locale.ROOT));
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public long getLastModified() {
        return lastModified;
    }

    public boolean isAwakeAt(int hour) {
        return sleepSchedule.isAwakeAt(hour);
    }

    public boolean hasInterest(@NotNull String topic) {
        return interests.contains(topic.toLowerCase(Locale.ROOT));
    }

    public boolean prefersTopic(@NotNull String topic) {
        return preferredTopics.contains(topic.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BotProfile that)) return false;
        return botUuid.equals(that.botUuid) && lastModified == that.lastModified;
    }

    @Override
    public int hashCode() {
        return Objects.hash(botUuid, lastModified);
    }

    @Override
    public String toString() {
        return "BotProfile{" + "uuid=" + botUuid + ", personality=" + personality.getName() + ", activity="
                + activityLevel + '}';
    }

    public static final class Builder {
        private UUID botUuid;
        private String displayName;
        private String nickname;
        private String ageGroup;
        private Personality personality;
        private Set<String> interests = Set.of();
        private Set<String> preferredTopics = Set.of();
        private ActivityLevel activityLevel = ActivityLevel.MODERATE;
        private ChatFrequency chatFrequency = ChatFrequency.NORMAL;
        private Map<UUID, Double> friendships = Map.of();
        private Map<UUID, Double> rivalries = Map.of();
        private String aiPromptModifier;
        private String swapGroup;
        private boolean peakParticipation = true;
        private SleepSchedule sleepSchedule = SleepSchedule.defaultSchedule();
        private Set<String> recentConversationRefs = Set.of();
        private Map<String, String> customProperties = Map.of();
        private String skinPreset;
        private Set<String> skinTags = Set.of();
        private String preferredSkinSource;
        private int schemaVersion = SCHEMA_VERSION;
        private long lastModified;

        private Builder(@NotNull UUID botUuid) {
            this.botUuid = botUuid;
        }

        public Builder botUuidBuilder(@NotNull UUID botUuid) {
            this.botUuid = botUuid;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder nickname(String nickname) {
            this.nickname = nickname;
            return this;
        }

        public Builder ageGroup(String ageGroup) {
            this.ageGroup = ageGroup;
            return this;
        }

        public Builder personality(Personality personality) {
            this.personality = personality;
            return this;
        }

        public Builder interests(Set<String> interests) {
            this.interests = interests != null ? interests : Set.of();
            return this;
        }

        public Builder preferredTopics(Set<String> preferredTopics) {
            this.preferredTopics = preferredTopics != null ? preferredTopics : Set.of();
            return this;
        }

        public Builder activityLevel(ActivityLevel activityLevel) {
            this.activityLevel = activityLevel != null ? activityLevel : ActivityLevel.MODERATE;
            return this;
        }

        public Builder chatFrequency(ChatFrequency chatFrequency) {
            this.chatFrequency = chatFrequency != null ? chatFrequency : ChatFrequency.NORMAL;
            return this;
        }

        public Builder friendships(Map<UUID, Double> friendships) {
            this.friendships = friendships != null ? friendships : Map.of();
            return this;
        }

        public Builder rivalries(Map<UUID, Double> rivalries) {
            this.rivalries = rivalries != null ? rivalries : Map.of();
            return this;
        }

        public Builder aiPromptModifier(String aiPromptModifier) {
            this.aiPromptModifier = aiPromptModifier;
            return this;
        }

        public Builder swapGroup(String swapGroup) {
            this.swapGroup = swapGroup;
            return this;
        }

        public Builder peakParticipation(boolean peakParticipation) {
            this.peakParticipation = peakParticipation;
            return this;
        }

        public Builder sleepSchedule(SleepSchedule sleepSchedule) {
            this.sleepSchedule = sleepSchedule != null ? sleepSchedule : SleepSchedule.defaultSchedule();
            return this;
        }

        public Builder recentConversationRefs(Set<String> recentConversationRefs) {
            this.recentConversationRefs = recentConversationRefs != null ? recentConversationRefs : Set.of();
            return this;
        }

        public Builder customProperties(Map<String, String> customProperties) {
            this.customProperties = customProperties != null ? customProperties : Map.of();
            return this;
        }

        public Builder skinPreset(String skinPreset) {
            this.skinPreset = skinPreset;
            return this;
        }

        public Builder skinTags(Set<String> skinTags) {
            this.skinTags = skinTags != null ? skinTags : Set.of();
            return this;
        }

        public Builder preferredSkinSource(String preferredSkinSource) {
            this.preferredSkinSource = preferredSkinSource;
            return this;
        }

        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder lastModified(long lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public BotProfile build() {
            return new BotProfile(this);
        }
    }
}
