package me.bill.fakePlayerPlugin.api.impl;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.api.FppBot;
import me.bill.fakePlayerPlugin.api.event.FppBotGameModeChangeEvent;
import me.bill.fakePlayerPlugin.fakeplayer.BotType;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.util.AttributeCompat;

public final class FppBotImpl implements FppBot {

    private final FakePlayer fp;

    public FppBotImpl(@NotNull FakePlayer fp) {
        this.fp = fp;
    }

    public @NotNull FakePlayer getHandle() {
        return fp;
    }

    @Override
    public @NotNull String getName() {
        return fp.getName();
    }

    @Override
    public @NotNull UUID getUuid() {
        return fp.getUuid();
    }

    @Override
    public @NotNull String getDisplayName() {
        String dn = fp.getDisplayName();
        return dn != null ? dn : fp.getName();
    }

    @Override
    public void setDisplayName(@NotNull String name) {
        fp.setDisplayName(name);
    }

    @Override
    public @Nullable String getSkinName() {
        return fp.getSkinName();
    }

    @Override
    public @NotNull Location getLocation() {
        return fp.getLiveLocation();
    }

    @Override
    public @NotNull String getWorldName() {
        Location loc = fp.getLiveLocation();
        return loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
    }

    @Override
    public @Nullable Player getEntity() {
        return fp.getPhysicsEntity();
    }

    @Override
    public boolean isBodyless() {
        return fp.isBodyless();
    }

    @Override
    public boolean isFrozen() {
        return fp.isFrozen();
    }

    @Override
    public void setFrozen(boolean frozen) {
        fp.setFrozen(frozen);
    }

    @Override
    public boolean isAlive() {
        return fp.isAlive();
    }

    @Override
    public boolean isRespawning() {
        return fp.isRespawning();
    }

    @Override
    public boolean isChatEnabled() {
        return fp.isChatEnabled();
    }

    @Override
    public void setChatEnabled(boolean enabled) {
        fp.setChatEnabled(enabled);
    }

    @Override
    public @Nullable String getChatTier() {
        return fp.getChatTier();
    }

    @Override
    public void setChatTier(@Nullable String t) {
        fp.setChatTier(t);
    }

    @Override
    public @Nullable String getAiPersonality() {
        return fp.getAiPersonality();
    }

    @Override
    public void setAiPersonality(@Nullable String p) {
        fp.setAiPersonality(p);
    }

    @Override
    public boolean isHeadAiEnabled() {
        return fp.isHeadAiEnabled();
    }

    @Override
    public void setHeadAiEnabled(boolean e) {
        fp.setHeadAiEnabled(e);
    }

    @Override
    public boolean isSwimAiEnabled() {
        return fp.isSwimAiEnabled();
    }

    @Override
    public void setSwimAiEnabled(boolean e) {
        fp.setSwimAiEnabled(e);
    }

    @Override
    public boolean isPickUpItemsEnabled() {
        return fp.isPickUpItemsEnabled();
    }

    @Override
    public void setPickUpItemsEnabled(boolean e) {
        fp.setPickUpItemsEnabled(e);
    }

    @Override
    public boolean isPickUpXpEnabled() {
        return fp.isPickUpXpEnabled();
    }

    @Override
    public void setPickUpXpEnabled(boolean e) {
        fp.setPickUpXpEnabled(e);
    }

    @Override
    public boolean isNavParkour() {
        return fp.isNavParkour();
    }

    @Override
    public void setNavParkour(boolean e) {
        fp.setNavParkour(e);
    }

    @Override
    public boolean isNavBreakBlocks() {
        return fp.isNavBreakBlocks();
    }

    @Override
    public void setNavBreakBlocks(boolean e) {
        fp.setNavBreakBlocks(e);
    }

    @Override
    public boolean isNavPlaceBlocks() {
        return fp.isNavPlaceBlocks();
    }

    @Override
    public void setNavPlaceBlocks(boolean e) {
        fp.setNavPlaceBlocks(e);
    }

    @Override
    public int getChunkLoadRadius() {
        return fp.getChunkLoadRadius();
    }

    @Override
    public void setChunkLoadRadius(int r) {
        fp.setChunkLoadRadius(r);
    }

    @Override
    public boolean isPveEnabled() {
        return fp.isPveEnabled();
    }

    @Override
    public void setPveEnabled(boolean e) {
        fp.setPveEnabled(e);
    }

    @Override
    public double getPveRange() {
        return fp.getPveRange();
    }

    @Override
    public void setPveRange(double r) {
        fp.setPveRange(r);
    }

    @Override
    public @Nullable String getPvePriority() {
        return fp.getPvePriority();
    }

    @Override
    public void setPvePriority(@Nullable String p) {
        fp.setPvePriority(p);
    }

    @Override
    public @NotNull String getSpawnedBy() {
        String s = fp.getSpawnedBy();
        return s != null ? s : "CONSOLE";
    }

    @Override
    public @NotNull UUID getSpawnedByUuid() {
        UUID u = fp.getSpawnedByUuid();
        return u != null ? u : new UUID(0, 0);
    }

    @Override
    public boolean isOwnedBy(@NotNull UUID playerUuid) {
        return playerUuid.equals(getSpawnedByUuid());
    }

    @Override
    public boolean hasControllerAccess(@NotNull UUID playerUuid) {
        return isOwnedBy(playerUuid) || fp.hasSharedController(playerUuid);
    }

    @Override
    public @NotNull Set<UUID> getSharedControllerUuids() {
        return fp.getSharedControllers();
    }

    @Override
    public boolean grantControllerAccess(@NotNull UUID playerUuid) {
        return fp.addSharedController(playerUuid);
    }

    @Override
    public boolean revokeControllerAccess(@NotNull UUID playerUuid) {
        return fp.removeSharedController(playerUuid);
    }

    @Override
    public @NotNull Duration getUptime() {
        return fp.getUptime();
    }

    @Override
    public int getDeathCount() {
        return fp.getDeathCount();
    }

    @Override
    public double getTotalDamageTaken() {
        return fp.getTotalDamageTaken();
    }

    @Override
    public boolean isInWater() {
        Player ent = fp.getPhysicsEntity();
        return ent != null && ent.isInWater();
    }

    @Override
    public boolean isInLava() {
        Player ent = fp.getPhysicsEntity();
        return ent != null && ent.isInLava();
    }

    @Override
    public boolean isSprinting() {
        Player ent = fp.getPhysicsEntity();
        return ent != null && ent.isSprinting();
    }

    @Override
    public int getPing() {
        return fp.getEffectivePing();
    }

    @Override
    public boolean hasCustomPing() {
        return fp.hasCustomPing();
    }

    // ── Health ──────────────────────────────────────────────────────────────────
    @Override
    public double getHealth() {
        Player ent = fp.getPhysicsEntity();
        return ent != null ? ent.getHealth() : 0.0;
    }

    @Override
    public void setHealth(double health) {
        Player ent = fp.getPhysicsEntity();
        if (ent != null) ent.setHealth(health);
    }

    @Override
    public double getMaxHealth() {
        Player ent = fp.getPhysicsEntity();
        if (ent == null) return 20.0;
        var attr = ent.getAttribute(AttributeCompat.maxHealth());
        return attr != null ? attr.getValue() : 20.0;
    }

    @Override
    public void setMaxHealth(double health) {
        Player ent = fp.getPhysicsEntity();
        if (ent != null) {
            var attr = ent.getAttribute(AttributeCompat.maxHealth());
            if (attr != null) attr.setBaseValue(health);
        }
    }

    @Override
    public boolean isDead() {
        Player ent = fp.getPhysicsEntity();
        return ent == null || ent.isDead();
    }

    // ── GameMode ───────────────────────────────────────────────────────────────
    @Override
    public @NotNull GameMode getGameMode() {
        Player ent = fp.getPhysicsEntity();
        return ent != null ? ent.getGameMode() : GameMode.SURVIVAL;
    }

    @Override
    public void setGameMode(@NotNull GameMode mode) {
        Player ent = fp.getPhysicsEntity();
        if (ent == null) return;
        GameMode old = ent.getGameMode();
        if (old == mode) return;
        var gmEvt = new FppBotGameModeChangeEvent(this, old, mode);
        Bukkit.getPluginManager().callEvent(gmEvt);
        if (gmEvt.isCancelled()) return;
        ent.setGameMode(gmEvt.getNewMode());
    }

    // ── Inventory ──────────────────────────────────────────────────────────────
    @Override
    public @Nullable PlayerInventory getInventory() {
        Player ent = fp.getPhysicsEntity();
        return ent != null ? ent.getInventory() : null;
    }

    @Override
    public @Nullable ItemStack getItemInMainHand() {
        Player ent = fp.getPhysicsEntity();
        return ent != null ? ent.getInventory().getItemInMainHand() : null;
    }

    @Override
    public void setItemInMainHand(@Nullable ItemStack item) {
        Player ent = fp.getPhysicsEntity();
        if (ent != null) ent.getInventory().setItemInMainHand(item);
    }

    @Override
    public @Nullable ItemStack getItemInOffHand() {
        Player ent = fp.getPhysicsEntity();
        return ent != null ? ent.getInventory().getItemInOffHand() : null;
    }

    @Override
    public void setItemInOffHand(@Nullable ItemStack item) {
        Player ent = fp.getPhysicsEntity();
        if (ent != null) ent.getInventory().setItemInOffHand(item);
    }

    // ── Teleport / Movement ──────────────────────────────────────────────────
    @Override
    public boolean teleport(@NotNull Location location) {
        Player ent = fp.getPhysicsEntity();
        return ent != null && ent.teleport(location);
    }

    @Override
    public @NotNull Location getEyeLocation() {
        Player ent = fp.getPhysicsEntity();
        return ent != null ? ent.getEyeLocation() : fp.getLiveLocation().clone().add(0, 1.62, 0);
    }

    @Override
    public void lookAt(@NotNull Location location) {
        Player ent = fp.getPhysicsEntity();
        if (ent != null)
            ent.teleport(ent.getLocation()
                    .setDirection(location.clone().subtract(ent.getLocation()).toVector()));
    }

    @Override
    public @NotNull Vector getVelocity() {
        Player ent = fp.getPhysicsEntity();
        return ent != null ? ent.getVelocity() : new Vector(0, 0, 0);
    }

    @Override
    public void setVelocity(@NotNull Vector velocity) {
        Player ent = fp.getPhysicsEntity();
        if (ent != null) ent.setVelocity(velocity);
    }

    // ── Experience ────────────────────────────────────────────────────────────
    @Override
    public int getLevel() {
        Player ent = fp.getPhysicsEntity();
        return ent != null ? ent.getLevel() : 0;
    }

    @Override
    public void setLevel(int level) {
        Player ent = fp.getPhysicsEntity();
        if (ent != null) ent.setLevel(level);
    }

    @Override
    public float getExp() {
        Player ent = fp.getPhysicsEntity();
        return ent != null ? ent.getExp() : 0.0f;
    }

    @Override
    public void setExp(float exp) {
        Player ent = fp.getPhysicsEntity();
        if (ent != null) ent.setExp(exp);
    }

    @Override
    public int getTotalExperience() {
        Player ent = fp.getPhysicsEntity();
        return ent != null ? ent.getTotalExperience() : 0;
    }

    @Override
    public void setTotalExperience(int exp) {
        Player ent = fp.getPhysicsEntity();
        if (ent != null) ent.setTotalExperience(exp);
    }

    // ── Sleep ─────────────────────────────────────────────────────────────────
    @Override
    public boolean isSleeping() {
        return fp.isSleeping();
    }

    @Override
    public @Nullable Location getSleepOrigin() {
        return fp.getSleepOrigin();
    }

    @Override
    public void setSleepOrigin(@Nullable Location origin) {
        fp.setSleepOrigin(origin);
    }

    @Override
    public double getSleepRadius() {
        return fp.getSleepRadius();
    }

    @Override
    public void setSleepRadius(double radius) {
        fp.setSleepRadius(radius);
    }

    // ── Navigation ─────────────────────────────────────────────────────────────
    @Override
    public boolean isNavAvoidWater() {
        return fp.isNavAvoidWater();
    }

    @Override
    public void setNavAvoidWater(boolean enabled) {
        fp.setNavAvoidWater(enabled);
    }

    @Override
    public boolean isNavAvoidLava() {
        return fp.isNavAvoidLava();
    }

    @Override
    public void setNavAvoidLava(boolean enabled) {
        fp.setNavAvoidLava(enabled);
    }

    @Override
    public boolean isDefaultWaterPathAvoidanceEnabled() {
        return fp.isDefaultWaterPathAvoidanceEnabled();
    }

    @Override
    public void setDefaultWaterPathAvoidanceEnabled(boolean enabled) {
        fp.setDefaultWaterPathAvoidanceEnabled(enabled);
    }

    // ── Bot type / metadata ──────────────────────────────────────────────────
    @Override
    public @NotNull String getBotTypeName() {
        BotType type = fp.getBotType();
        return type != null ? type.name() : "AFK";
    }

    @Override
    public void setBotTypeName(@NotNull String type) {
        fp.setBotType(BotType.parse(type));
    }

    @Override
    public @Nullable String getLuckpermsGroup() {
        return fp.getLuckpermsGroup();
    }

    @Override
    public void setLuckpermsGroup(@Nullable String group) {
        fp.setLuckpermsGroup(group);
    }

    // ── Messaging / permissions ─────────────────────────────────────────────
    @Override
    public void sendMessage(@NotNull String message) {
        Player ent = fp.getPhysicsEntity();
        if (ent != null) ent.sendMessage(message);
    }

    @Override
    public boolean hasPermission(@NotNull String permission) {
        Player ent = fp.getPhysicsEntity();
        return ent != null && ent.hasPermission(permission);
    }

    @Override
    public boolean isOnline() {
        Player ent = fp.getPhysicsEntity();
        return ent != null && ent.isOnline();
    }

    // ── Addon metadata ────────────────────────────────────────────────────────
    @Override
    public void setMetadata(@NotNull String key, @Nullable Object value) {
        fp.setMetadata(key, value);
    }

    @Override
    public @Nullable Object getMetadata(@NotNull String key) {
        return fp.getMetadata(key);
    }

    @Override
    public boolean hasMetadata(@NotNull String key) {
        return fp.hasMetadata(key);
    }

    @Override
    public void removeMetadata(@NotNull String key) {
        fp.removeMetadata(key);
    }

    @Override
    public @NotNull Map<String, Object> getMetadataMap() {
        return fp.getMetadataMap();
    }

    // ── Animation / state ─────────────────────────────────────────────────────
    @Override
    public void swingMainHand() {
        Player ent = fp.getPhysicsEntity();
        if (ent != null) ent.swingMainHand();
    }

    @Override
    public void swingOffHand() {
        Player ent = fp.getPhysicsEntity();
        if (ent != null) ent.swingOffHand();
    }

    @Override
    public boolean isSneaking() {
        Player ent = fp.getPhysicsEntity();
        return ent != null && ent.isSneaking();
    }

    @Override
    public void setSneaking(boolean sneaking) {
        Player ent = fp.getPhysicsEntity();
        if (ent != null) ent.setSneaking(sneaking);
    }

    @Override
    public void setSprinting(boolean sprinting) {
        Player ent = fp.getPhysicsEntity();
        if (ent != null) ent.setSprinting(sprinting);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isOnGround() {
        Player ent = fp.getPhysicsEntity();
        return ent != null && ent.isOnGround();
    }

    @Override
    public boolean isClimbing() {
        Player ent = fp.getPhysicsEntity();
        return ent != null && ent.isClimbing();
    }

    @Override
    public boolean isPassenger() {
        Player ent = fp.getPhysicsEntity();
        return ent != null && ent.getVehicle() != null;
    }

    @Override
    public boolean hasVehicle() {
        Player ent = fp.getPhysicsEntity();
        return ent != null && ent.getVehicle() != null;
    }

    @Override
    public double getReachDistance() {
        Player ent = fp.getPhysicsEntity();
        if (ent == null) return 3.0;
        double base = 3.0;
        try {
            var attr = ent.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
            if (attr != null) base = attr.getValue();
        } catch (Throwable ignored) {
        }
        return base;
    }

    @Override
    public void performRespawn() {
        Player ent = fp.getPhysicsEntity();
        if (ent != null && ent.isDead()) {
            try {
                ent.spigot().respawn();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FppBotImpl other)) return false;
        return fp.getUuid().equals(other.fp.getUuid());
    }

    @Override
    public int hashCode() {
        return fp.getUuid().hashCode();
    }

    @Override
    public String toString() {
        return "FppBot{" + fp.getName() + "/" + fp.getUuid() + "}";
    }
}
