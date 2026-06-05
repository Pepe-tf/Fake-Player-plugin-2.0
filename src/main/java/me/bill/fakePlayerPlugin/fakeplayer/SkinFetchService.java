package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.function.BiConsumer;

import org.jetbrains.annotations.NotNull;

public interface SkinFetchService {
    SkinFetchService NOOP = new SkinFetchService() {
        @Override
        public void fetchAsync(@NotNull String playerName, @NotNull BiConsumer<String, String> callback) {
            callback.accept(null, null);
        }

        @Override
        public void fetchByUrl(@NotNull String url, @NotNull BiConsumer<String, String> callback) {
            callback.accept(null, null);
        }
    };

    void fetchAsync(@NotNull String playerName, @NotNull BiConsumer<String, String> callback);

    void fetchByUrl(@NotNull String url, @NotNull BiConsumer<String, String> callback);

    default String[] getCached(@NotNull String playerName) {
        return null;
    }

    default boolean isCached(@NotNull String playerName) {
        return getCached(playerName) != null;
    }

    default void clearCache() {}
}
