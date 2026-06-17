package me.bill.fakePlayerPlugin.api.personality;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.NotNull;

public final class ProfileApi {

    private static final Class<ProfileService> SERVICE_CLASS = ProfileService.class;

    private ProfileApi() {}

    public static void register(@NotNull Plugin plugin, @NotNull ProfileService service) {
        plugin.getServer().getServicesManager().register(SERVICE_CLASS, service, plugin, ServicePriority.Normal);
    }

    public static void unregister(@NotNull Plugin plugin, @NotNull ProfileService service) {
        plugin.getServer().getServicesManager().unregister(service);
    }

    public static @NotNull Optional<ProfileService> getService() {
        var provider = org.bukkit.Bukkit.getServer().getServicesManager().getRegistration(SERVICE_CLASS);
        return provider != null ? Optional.ofNullable(provider.getProvider()) : Optional.empty();
    }

    public static @NotNull BotProfile getProfile(@NotNull UUID botUuid) {
        return getService().map(s -> s.getProfile(botUuid)).orElseGet(() -> BotProfile.builder(botUuid)
                .personality(Personality.neutral())
                .build());
    }

    public static @NotNull Optional<BotProfile> findProfile(@NotNull UUID botUuid) {
        return getService().flatMap(s -> s.findProfile(botUuid));
    }
}
