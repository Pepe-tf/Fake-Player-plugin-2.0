package me.bill.fakePlayerPlugin.listener;

import java.util.Collection;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.util.FppScheduler;

public class BotCollisionListener implements Listener {

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    private static final double VANILLA_ATTACK_UPWARD = 0.40D;
    private static final double PLAYER_SPRINT_BONUS = 0.28D;

    private int separationTickCounter = 0;
    private int separationCursor = 0;

    public BotCollisionListener(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        FppScheduler.runSyncRepeating(plugin, this::tickBotSeparation, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!Config.bodyPushable()) {
            Config.debugNms("[KB-DEBUG] BotCollision: SKIP - bodyPushable=false");
            return;
        }
        if (!(event.getEntity() instanceof Player target)) return;
        if (!isFakeBody(target)) return;

        Entity attacker = resolveKnockbackSource(event.getDamager());
        if (attacker == null) {
            Config.debugNms("[KB-DEBUG] BotCollision: SKIP - attacker null for bot=" + target.getName());
            return;
        }

        boolean fromPlayer = attacker instanceof Player;
        Config.debugNms("[KB-DEBUG] BotCollision: hit event for bot="
                + target.getName()
                + " attacker="
                + attacker.getType()
                + " fromPlayer="
                + fromPlayer
                + " cancelled="
                + event.isCancelled()
                + " bodyDamageable="
                + Config.bodyDamageable());

        if (event.isCancelled()) {
            Config.debugNms("[KB-DEBUG] BotCollision: SKIP - damage event cancelled for bot=" + target.getName());
            return;
        }

        double hitStrength = Config.collisionHitStrength();
        double hitMaxHoriz = Config.collisionHitMaxHoriz();

        Location aLoc = attacker.getLocation();
        Location bLoc = target.getLocation();
        double dx = bLoc.getX() - aLoc.getX();
        double dz = bLoc.getZ() - aLoc.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        Vector kb = computeHorizontalKnockback(attacker, target, dx, dz, dist, VANILLA_ATTACK_UPWARD);
        if (fromPlayer && attacker instanceof Player p && p.isSprinting()) {
            kb = scaleHorizontal(kb, hitStrength + PLAYER_SPRINT_BONUS);
        } else {
            kb = scaleHorizontal(kb, hitStrength);
        }

        double kbX = kb.getX();
        double kbZ = kb.getZ();
        double speed = Math.sqrt(kbX * kbX + kbZ * kbZ);
        if (speed > hitMaxHoriz) {
            double scale = hitMaxHoriz / speed;
            kbX *= scale;
            kbZ *= scale;
        }

        Vector finalVel = new Vector(kbX, kb.getY(), kbZ);
        Config.debugNms("[KB-DEBUG] BotCollision: applying FPP knockback to "
                + target.getName()
                + " vel=("
                + String.format("%.4f", finalVel.getX())
                + ","
                + String.format("%.4f", finalVel.getY())
                + ","
                + String.format("%.4f", finalVel.getZ())
                + ") hitStrength="
                + hitStrength);

        applyBotKnockback(target, finalVel, "entity-damage");
    }

    private void applyBotKnockback(Player target, Vector velocity, String source) {
        NmsPlayerSpawner.applyServerVelocity(target, velocity);
        FppScheduler.runAtEntityLaterWithId(
                plugin,
                target,
                () -> {
                    if (target.isOnline() && target.isValid() && isFakeBody(target)) {
                        NmsPlayerSpawner.applyServerVelocity(target, velocity);
                        Vector readback = target.getVelocity();
                        Config.debugNms("[KB-DEBUG] FPP "
                                + source
                                + " readback velocity for "
                                + target.getName()
                                + " x="
                                + String.format("%.4f", readback.getX())
                                + " y="
                                + String.format("%.4f", readback.getY())
                                + " z="
                                + String.format("%.4f", readback.getZ()));
                    }
                },
                1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExplosionDamage(EntityDamageEvent event) {
        if (!Config.bodyPushable()) return;
        if (!(event.getEntity() instanceof Player target)) return;
        if (!isFakeBody(target)) return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }

        if (event instanceof EntityDamageByEntityEvent) return;

        if (event.isCancelled()) return;

        double hitMaxHoriz = Config.collisionHitMaxHoriz();
        double hitStrength = Config.collisionHitStrength();

        Vector facing = target.getLocation().getDirection();
        Vector kb = new Vector(facing.getX(), 0.45, facing.getZ()).normalize().multiply(hitStrength * 0.7);

        double kbX = kb.getX();
        double kbZ = kb.getZ();
        double speed = Math.sqrt(kbX * kbX + kbZ * kbZ);
        if (speed > hitMaxHoriz) {
            double scale = hitMaxHoriz / speed;
            kbX *= scale;
            kbZ *= scale;
        }

        applyBotKnockback(target, new Vector(kbX, kb.getY(), kbZ), "explosion-damage");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!Config.bodyPushable()) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (Math.abs(to.getX() - from.getX()) < 1e-6 && Math.abs(to.getZ() - from.getZ()) < 1e-6) return;

        double walkRadius = Config.collisionWalkRadius();
        double walkStrength = Config.collisionWalkStrength();
        double maxHoriz = Config.collisionMaxHoriz();

        Player player = event.getPlayer();
        Location pLoc = player.getLocation();

        for (FakePlayer fp : manager.getActivePlayers()) {
            Player body = fp.getPlayer();
            if (body == null || !body.isValid()) continue;

            if (!body.getWorld().equals(player.getWorld())) continue;
            if (!canCollide(player, body)) continue;

            Location bLoc = body.getLocation();
            double dy = pLoc.getY() - bLoc.getY();
            if (dy > 2.2 || dy < -1.2) continue;

            double dx = bLoc.getX() - pLoc.getX();
            double dz = bLoc.getZ() - pLoc.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq > walkRadius * walkRadius || distSq < 1e-12) continue;

            double dist = Math.sqrt(distSq);
            double overlap = 1.0 - (dist / walkRadius);
            double strength = walkStrength * overlap;

            applyImpulse(body, (dx / dist) * strength, (dz / dist) * strength, maxHoriz);
        }
    }

    private void tickBotSeparation() {
        if (NmsPlayerSpawner.isFoliaServer()) return;
        if (!Config.bodyPushable()) return;

        if ((++separationTickCounter % 5) != 0) return;

        Collection<FakePlayer> all = manager.getActivePlayers();
        if (all.size() < 2) return;

        double botRadius = Config.collisionBotRadius();
        double botStrength = Config.collisionBotStrength();
        double maxHoriz = Config.collisionMaxHoriz();

        FakePlayer[] bots = all.toArray(new FakePlayer[0]);
        int len = bots.length;
        int checks = 0;
        int maxChecks = Math.max(32, len * 3);
        for (int offset = 0; offset < len && checks < maxChecks; offset++) {
            int i = (separationCursor + offset) % len;
            Player bodyA = bots[i].getPlayer();
            if (bodyA == null || !bodyA.isValid()) continue;
            Location locA = bodyA.getLocation();

            for (int j = i + 1; j < len; j++) {
                if (++checks > maxChecks) break;
                Player bodyB = bots[j].getPlayer();
                if (bodyB == null || !bodyB.isValid()) continue;
                if (!bodyA.getWorld().equals(bodyB.getWorld())) continue;
                if (!canCollide(bodyA, bodyB)) continue;

                Location locB = bodyB.getLocation();
                double dy = locA.getY() - locB.getY();
                if (dy > 2.0 || dy < -2.0) continue;

                double dx = locB.getX() - locA.getX();
                double dz = locB.getZ() - locA.getZ();
                double distSq = dx * dx + dz * dz;
                if (distSq > botRadius * botRadius || distSq < 1e-12) continue;

                double dist = Math.sqrt(distSq);
                double nx = dx / dist;
                double nz = dz / dist;
                double overlap = 1.0 - (dist / botRadius);
                double strength = botStrength * overlap * 0.5;

                applyImpulse(bodyB, nx * strength, nz * strength, maxHoriz);
                applyImpulse(bodyA, -nx * strength, -nz * strength, maxHoriz);
            }
        }
        separationCursor = (separationCursor + Math.max(1, len / 4)) % len;
    }

    private static void applyImpulse(Entity body, double ix, double iz, double maxHoriz) {
        applyImpulse(body, ix, 0.0, iz, maxHoriz, 0.9);
    }

    private static Vector computeHorizontalKnockback(
            Entity attacker, Player target, double dx, double dz, double dist, double upward) {
        if (dist >= 1e-4) {
            return new Vector(dx / dist, upward, dz / dist);
        }

        Vector fromFacing = attacker.getLocation().getDirection().setY(0);
        if (fromFacing.lengthSquared() < 1e-8) {
            fromFacing = target.getLocation().getDirection().setY(0);
        }
        if (fromFacing.lengthSquared() < 1e-8) {
            fromFacing = new Vector(1, 0, 0);
        }
        fromFacing.normalize();
        return new Vector(fromFacing.getX(), upward, fromFacing.getZ());
    }

    private static void applyImpulse(Entity body, double ix, double iy, double iz, double maxHoriz, double maxUpward) {
        Vector vel = body.getVelocity();
        double newX = vel.getX() + ix;
        double newY = vel.getY() + iy;
        double newZ = vel.getZ() + iz;

        double speed = Math.sqrt(newX * newX + newZ * newZ);
        if (speed > maxHoriz) {
            double scale = maxHoriz / speed;
            newX *= scale;
            newZ *= scale;
        }

        vel.setX(newX);
        if (newY > maxUpward) newY = maxUpward;
        if (newY < -4.0) newY = -4.0;
        vel.setY(newY);
        vel.setZ(newZ);
        if (body instanceof Player player) {
            NmsPlayerSpawner.applyServerVelocity(player, vel);
        } else {
            body.setVelocity(vel);
        }
    }

    private boolean canCollide(Player source, Entity other) {
        if (source == null || other == null) return false;
        if (source.getGameMode() == GameMode.SPECTATOR) return false;
        if (other instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) return false;

        if (!source.isCollidable()) return false;
        if (other instanceof Player otherPlayer && !otherPlayer.isCollidable()) return false;

        Scoreboard board = source.getScoreboard();
        if (board == null) return true;

        Team sourceTeam = board.getEntryTeam(source.getName());
        if (sourceTeam == null) return true;

        Team otherTeam = board.getEntryTeam(other.getName());
        Team.OptionStatus rule = sourceTeam.getOption(Team.Option.COLLISION_RULE);
        if (rule == null) return true;

        if (rule == Team.OptionStatus.NEVER) return false;
        if (rule == Team.OptionStatus.ALWAYS) return true;
        if (rule == Team.OptionStatus.FOR_OWN_TEAM) {
            return otherTeam != null && sourceTeam.equals(otherTeam);
        }
        if (rule == Team.OptionStatus.FOR_OTHER_TEAMS) {
            return otherTeam == null || !sourceTeam.equals(otherTeam);
        }
        return true;
    }

    private boolean isFakeBody(Entity entity) {
        if (!(entity instanceof Player)) return false;
        if (manager.getByEntityId(entity.getEntityId()) != null) return true;
        FakePlayer byEntity = manager.getByEntity(entity);
        if (byEntity != null) return true;

        FakePlayer byUuid = manager.getByUuid(entity.getUniqueId());
        return byUuid != null && byUuid.getName().equalsIgnoreCase(entity.getName());
    }

    private static Vector scaleHorizontal(Vector input, double multiplier) {
        double scale = Math.max(0.0, multiplier);
        return new Vector(input.getX() * scale, input.getY(), input.getZ() * scale);
    }

    private static Entity resolveKnockbackSource(Entity damager) {
        if (damager == null) return null;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity shooterEntity) return shooterEntity;
        }
        return damager;
    }
}
