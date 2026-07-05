package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.event.FppBotAttackEvent;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.command.AttackCommand;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppScheduler;

/**
 * Core PVE engine — the executor behind the per-bot "🗡 ᴘᴠᴇ" settings. Previously those settings
 * only fired an event a removed extension used to consume, so nothing ever scanned or attacked.
 *
 * <p>A lightweight supervisor (1 s cadence) starts a per-entity combat task for every bot whose
 * smart-attack mode is enabled and stops it when disabled/despawned. The combat task runs every
 * tick at the bot's region (Folia-safe): it picks a target from the configured mob types (empty
 * selection = every hostile, via {@link Enemy}) by the configured priority (nearest /
 * lowest-health), faces it, attacks with real weapon-cooldown pacing, and — in {@code ON_MOVE}
 * mode — chases it through the shared pathfinding engine ({@code Owner.ATTACK}, live re-pathing as
 * the target moves, never hijacking a navigation another system owns).
 */
public final class PveController {

    /** Vanilla melee reach against mobs. */
    private static final double ATTACK_REACH = 3.0;

    private static final long SUPERVISOR_PERIOD_TICKS = 20L;

    /** Re-path when the chased target strayed this far from where we planned to go. */
    private static final double CHASE_RECALC_DISTANCE = 2.5;

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;
    private final PathfindingService pathfinding;

    private final Map<UUID, Integer> combatTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> cooldownTicks = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> currentTarget = new ConcurrentHashMap<>();
    private int supervisorTaskId = -1;

    public PveController(FakePlayerPlugin plugin, FakePlayerManager manager, PathfindingService pathfinding) {
        this.plugin = plugin;
        this.manager = manager;
        this.pathfinding = pathfinding;
    }

    /** Starts the supervisor that attaches/detaches combat tasks as bots toggle PVE. */
    public void start() {
        if (supervisorTaskId >= 0) return;
        supervisorTaskId = FppScheduler.runSyncRepeatingWithId(
                plugin, this::superviseAll, SUPERVISOR_PERIOD_TICKS, SUPERVISOR_PERIOD_TICKS);
    }

    public void shutdown() {
        if (supervisorTaskId >= 0) {
            FppScheduler.cancelTask(supervisorTaskId);
            supervisorTaskId = -1;
        }
        combatTasks.keySet().forEach(this::stopCombat);
    }

    /** Immediate reaction to a GUI toggle instead of waiting for the next supervisor sweep. */
    public void refresh(@NotNull FakePlayer fp) {
        if (fp.isPveEnabled()) {
            startCombat(fp);
        } else {
            stopCombat(fp.getUuid());
        }
    }

    /** Despawn cleanup — called from the shared bot-removal path. */
    public void cleanupBot(@NotNull UUID botUuid) {
        stopCombat(botUuid);
    }

    /** True while this bot has an acquired PVE target (used by the nametag activity line). */
    public boolean isEngaged(@NotNull UUID botUuid) {
        return currentTarget.containsKey(botUuid);
    }

    private void superviseAll() {
        for (FakePlayer fp : manager.getActivePlayers()) {
            if (fp.isPveEnabled() && !combatTasks.containsKey(fp.getUuid())) {
                startCombat(fp);
            }
        }
    }

    private void startCombat(FakePlayer fp) {
        UUID uuid = fp.getUuid();
        if (combatTasks.containsKey(uuid)) return;
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return;

        Config.debugPathfinding("event=PVE_START bot='" + fp.getName() + "' mode=" + fp.getPveSmartAttackMode()
                + " range=" + fp.getPveRange());
        int taskId = FppScheduler.runAtEntityRepeatingWithId(plugin, bot, () -> tickCombat(fp), 0L, 1L);
        combatTasks.put(uuid, taskId);
    }

    private void stopCombat(UUID uuid) {
        Integer taskId = combatTasks.remove(uuid);
        if (taskId != null) FppScheduler.cancelTask(taskId);
        cooldownTicks.remove(uuid);
        currentTarget.remove(uuid);
        if (pathfinding.isNavigating(uuid, PathfindingService.Owner.ATTACK)) {
            pathfinding.cancel(uuid);
        }
    }

    private void tickCombat(FakePlayer fp) {
        UUID uuid = fp.getUuid();
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline() || !fp.isPveEnabled()) {
            stopCombat(uuid);
            return;
        }
        if (fp.isFrozen() || fp.isInventoryOpen() || fp.isActionsPaused()) return;

        // Single-action: yield to any user-issued task (move/find/mine/use/attack) instead of
        // multitasking. PVE re-engages automatically once that task finishes.
        if (manager.hasActiveManualAction(uuid)) {
            currentTarget.remove(uuid);
            if (pathfinding.isNavigating(uuid, PathfindingService.Owner.ATTACK)) {
                pathfinding.cancel(uuid);
            }
            return;
        }

        int cooldown = cooldownTicks.getOrDefault(uuid, 0);
        if (cooldown > 0) cooldownTicks.put(uuid, cooldown - 1);

        LivingEntity target = resolveTarget(fp, bot);
        if (target == null) {
            currentTarget.remove(uuid);
            if (pathfinding.isNavigating(uuid, PathfindingService.Owner.ATTACK)) {
                pathfinding.cancel(uuid);
            }
            return;
        }

        boolean switchedTarget = !target.getUniqueId().equals(currentTarget.put(uuid, target.getUniqueId()));

        faceTarget(bot, target);

        double distance = bot.getLocation().distance(target.getLocation());
        if (distance <= ATTACK_REACH) {
            if (pathfinding.isNavigating(uuid, PathfindingService.Owner.ATTACK)) {
                pathfinding.cancel(uuid);
            }
            if (cooldown <= 0) {
                attack(fp, bot, target);
            }
            return;
        }

        if (fp.getPveSmartAttackMode().movesWhileAttacking()) {
            chase(fp, bot, target, switchedTarget);
        }
    }

    /** Keeps the current target while it stays valid and in range; otherwise scans for a new one. */
    private @Nullable LivingEntity resolveTarget(FakePlayer fp, Player bot) {
        double range = Math.max(1.0, fp.getPveRange());
        UUID targetUuid = currentTarget.get(fp.getUuid());
        if (targetUuid != null) {
            Entity existing = Bukkit.getEntity(targetUuid);
            if (existing instanceof LivingEntity living
                    && living.isValid()
                    && !living.isDead()
                    && living.getWorld() == bot.getWorld()
                    // Small grace factor so a target that steps just outside range isn't dropped
                    // mid-chase, which would cause rapid target flapping at the range boundary.
                    && living.getLocation().distanceSquared(bot.getLocation()) <= range * range * 2.25) {
                return living;
            }
        }
        return scanForTarget(fp, bot, range);
    }

    private @Nullable LivingEntity scanForTarget(FakePlayer fp, Player bot, double range) {
        Set<String> wantedTypes = fp.getPveMobTypes();
        boolean nearestPriority = !"lowest-health".equalsIgnoreCase(fp.getPvePriority());

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity entity : bot.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()) continue;
            if (manager.getByUuid(living.getUniqueId()) != null) continue; // never target other bots
            if (living instanceof Player) continue; // PVE only — real players are never targets
            if (wantedTypes.isEmpty()) {
                if (!(living instanceof Enemy)) continue;
            } else if (!wantedTypes.contains(living.getType().name())) {
                continue;
            }

            double score =
                    nearestPriority ? living.getLocation().distanceSquared(bot.getLocation()) : living.getHealth();
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    private void faceTarget(Player bot, LivingEntity target) {
        Location face = BotNavUtil.faceToward(bot.getLocation(), target.getEyeLocation());
        bot.setRotation(face.getYaw(), face.getPitch());
        NmsPlayerSpawner.setHeadYaw(bot, face.getYaw());
    }

    private void attack(FakePlayer fp, Player bot, LivingEntity target) {
        bot.swingMainHand();
        var event = new FppBotAttackEvent(new FppBotImpl(fp), target, 1.0);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        NmsPlayerSpawner.performAttack(bot, target, 1.0);
        ItemStack mainHand = bot.getInventory().getItemInMainHand();
        Material weapon = mainHand.getType() != Material.AIR ? mainHand.getType() : null;
        cooldownTicks.put(fp.getUuid(), AttackCommand.getWeaponCooldown(weapon));
    }

    /**
     * Chases through the shared pathfinding engine. Only starts a navigation when the bot isn't
     * already navigating for another system (mining, moving, …) — PVE never hijacks those; it keeps
     * attacking anything that comes into reach instead.
     */
    private void chase(FakePlayer fp, Player bot, LivingEntity target, boolean switchedTarget) {
        UUID uuid = fp.getUuid();
        boolean chasing = pathfinding.isNavigating(uuid, PathfindingService.Owner.ATTACK);
        if (pathfinding.isNavigating(uuid) && !chasing) return;
        if (chasing && !switchedTarget) return; // already following this target live

        final UUID targetUuid = target.getUniqueId();
        pathfinding.navigate(
                fp,
                new PathfindingService.NavigationRequest(
                        PathfindingService.Owner.ATTACK,
                        () -> {
                            Entity live = Bukkit.getEntity(targetUuid);
                            return live != null && live.isValid() && live.getWorld() == bot.getWorld()
                                    ? live.getLocation()
                                    : null;
                        },
                        ATTACK_REACH - 0.5,
                        CHASE_RECALC_DISTANCE,
                        10,
                        null,
                        null,
                        null,
                        null));
    }
}
