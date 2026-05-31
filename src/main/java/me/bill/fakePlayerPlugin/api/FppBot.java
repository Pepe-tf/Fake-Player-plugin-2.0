package me.bill.fakePlayerPlugin.api;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FppBot {
    @NotNull
    String getName();

    @NotNull
    UUID getUuid();

    @NotNull
    String getDisplayName();

    void setDisplayName(@NotNull String name);

    @Nullable
    String getSkinName();

    @NotNull
    Location getLocation();

    @NotNull
    String getWorldName();

    @Nullable
    Player getEntity();

    boolean isBodyless();

    boolean isFrozen();

    void setFrozen(boolean frozen);

    boolean isAlive();

    boolean isRespawning();

    boolean isChatEnabled();

    void setChatEnabled(boolean enabled);

    @Nullable
    String getChatTier();

    void setChatTier(@Nullable String tier);

    @Nullable
    String getAiPersonality();

    void setAiPersonality(@Nullable String personality);

    boolean isHeadAiEnabled();

    void setHeadAiEnabled(boolean enabled);

    boolean isSwimAiEnabled();

    void setSwimAiEnabled(boolean enabled);

    boolean isPickUpItemsEnabled();

    void setPickUpItemsEnabled(boolean enabled);

    boolean isPickUpXpEnabled();

    void setPickUpXpEnabled(boolean enabled);

    boolean isNavParkour();

    void setNavParkour(boolean enabled);

    boolean isNavBreakBlocks();

    void setNavBreakBlocks(boolean enabled);

    boolean isNavPlaceBlocks();

    void setNavPlaceBlocks(boolean enabled);

    int getChunkLoadRadius();

    void setChunkLoadRadius(int radius);

    boolean isPveEnabled();

    void setPveEnabled(boolean enabled);

    double getPveRange();

    void setPveRange(double range);

    @Nullable
    String getPvePriority();

    void setPvePriority(@Nullable String priority);

    @NotNull
    String getSpawnedBy();

    @NotNull
    UUID getSpawnedByUuid();

    boolean isOwnedBy(@NotNull UUID playerUuid);

    boolean hasControllerAccess(@NotNull UUID playerUuid);

    @NotNull
    Set<UUID> getSharedControllerUuids();

    boolean grantControllerAccess(@NotNull UUID playerUuid);

    boolean revokeControllerAccess(@NotNull UUID playerUuid);

    @NotNull
    Duration getUptime();

    int getDeathCount();

    double getTotalDamageTaken();

    boolean isInWater();

    boolean isInLava();

    boolean isSprinting();

    int getPing();

    default boolean hasCustomPing() {
        return false;
    }

    // ── Health ────────────────────────────────────────────────────────────────
    double getHealth();

    void setHealth(double health);

    double getMaxHealth();

    void setMaxHealth(double health);

    boolean isDead();

    // ── GameMode ──────────────────────────────────────────────────────────────
    @NotNull
    GameMode getGameMode();

    void setGameMode(@NotNull GameMode mode);

    // ── Inventory ─────────────────────────────────────────────────────────────
    @Nullable
    PlayerInventory getInventory();

    @Nullable
    ItemStack getItemInMainHand();

    void setItemInMainHand(@Nullable ItemStack item);

    @Nullable
    ItemStack getItemInOffHand();

    void setItemInOffHand(@Nullable ItemStack item);

    // ── Teleport / Movement ───────────────────────────────────────────────────
    boolean teleport(@NotNull Location location);

    @NotNull
    Location getEyeLocation();

    void lookAt(@NotNull Location location);

    @NotNull
    Vector getVelocity();

    void setVelocity(@NotNull Vector velocity);

    // ── Experience ────────────────────────────────────────────────────────────
    int getLevel();

    void setLevel(int level);

    float getExp();

    void setExp(float exp);

    int getTotalExperience();

    void setTotalExperience(int exp);

    // ── Sleep ─────────────────────────────────────────────────────────────────
    boolean isSleeping();

    @Nullable
    Location getSleepOrigin();

    void setSleepOrigin(@Nullable Location origin);

    double getSleepRadius();

    void setSleepRadius(double radius);

    // ── Navigation ────────────────────────────────────────────────────────────
    boolean isNavAvoidWater();

    void setNavAvoidWater(boolean enabled);

    boolean isNavAvoidLava();

    void setNavAvoidLava(boolean enabled);

    default boolean isDefaultWaterPathAvoidanceEnabled() {
        return true;
    }

    default void setDefaultWaterPathAvoidanceEnabled(boolean enabled) {}

    // ── Bot type / metadata ───────────────────────────────────────────────────
    @NotNull
    String getBotTypeName();

    void setBotTypeName(@NotNull String type);

    @Nullable
    String getLuckpermsGroup();

    void setLuckpermsGroup(@Nullable String group);

    // ── Messaging / permissions ───────────────────────────────────────────────
    void sendMessage(@NotNull String message);

    boolean hasPermission(@NotNull String permission);

    boolean isOnline();

    // ── Animation / state ─────────────────────────────────────────────────────
    void swingMainHand();

    void swingOffHand();

    boolean isSneaking();

    void setSneaking(boolean sneaking);

    void setSprinting(boolean sprinting);

    boolean isOnGround();

    boolean isClimbing();

    boolean isPassenger();

    boolean hasVehicle();

    double getReachDistance();

    void performRespawn();

    // ── Addon metadata ────────────────────────────────────────────────────────
    void setMetadata(@NotNull String key, @Nullable Object value);

    @Nullable
    Object getMetadata(@NotNull String key);

    boolean hasMetadata(@NotNull String key);

    void removeMetadata(@NotNull String key);

    @NotNull
    Map<String, Object> getMetadataMap();
}
