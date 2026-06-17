package me.bill.fakePlayerPlugin.api.personality;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ProfileService {

    @NotNull
    String EXTENSION_KEY = BotProfile.EXTENSION_KEY;

    @NotNull
    BotProfile getProfile(@NotNull UUID botUuid);

    @NotNull
    Optional<BotProfile> findProfile(@NotNull UUID botUuid);

    @NotNull
    BotProfile createProfile(@NotNull UUID botUuid);

    @NotNull
    BotProfile updateProfile(@NotNull UUID botUuid, @NotNull Consumer<BotProfile.Builder> updater);

    void saveProfile(@NotNull BotProfile profile);

    void invalidateCache(@NotNull UUID botUuid);

    void invalidateAllCache();

    @NotNull
    Collection<BotProfile> loadAllProfiles();

    void registerListener(@NotNull Object listener);

    void unregisterListener(@NotNull Object listener);

    @NotNull
    BotProfile getDefaultTemplate();

    void setDefaultTemplate(@NotNull BotProfile template);

    void migrateLegacyData(@NotNull UUID botUuid, @Nullable String legacySwapGroup, boolean legacyPeakFlag);
}
