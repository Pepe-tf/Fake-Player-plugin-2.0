package me.bill.fakePlayerPlugin.fakeplayer;

import com.destroystokyo.paper.profile.PlayerProfile;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.event.FppBotDespawnEvent;
import me.bill.fakePlayerPlugin.api.event.FppBotSpawnEvent;
import me.bill.fakePlayerPlugin.api.event.FppBotTeleportEvent;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.BotRecord;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.listener.PlayerJoinListener;
import me.bill.fakePlayerPlugin.util.AttributeCompat;
import me.bill.fakePlayerPlugin.util.AttributionApiManager;
import me.bill.fakePlayerPlugin.util.AttributionManager;
import me.bill.fakePlayerPlugin.util.BadwordFilter;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import me.bill.fakePlayerPlugin.util.RandomNameGenerator;
import me.bill.fakePlayerPlugin.util.TextUtil;
import me.bill.fakePlayerPlugin.util.WorldGuardHelper;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FakePlayerManager {

  public static NamespacedKey FAKE_PLAYER_KEY;

  private final FakePlayerPlugin plugin;
  private final Map<UUID, FakePlayer> activePlayers = new ConcurrentHashMap<>();

  private final Map<Integer, FakePlayer> entityIdIndex = new ConcurrentHashMap<>();

  private static final Vector ZERO_VELOCITY = new Vector(0, 0, 0);

  /**
   * Cosine of the maximum gaze angle for head-AI player tracking.
   * A player must be looking toward the bot within this cone for the bot to
   * react. cos(15°) ≈ 0.9659 produces natural-feeling eye-contact without
   * bots mechanically staring at every nearby player.
   */
  private static final double HEAD_AI_GAZE_COS = Math.cos(Math.toRadians(15.0));

  private final Map<String, FakePlayer> nameIndex = new ConcurrentHashMap<>();
  private final Set<String> usedNames = new HashSet<>();

  private final Map<UUID, Long> spawnCooldowns = new ConcurrentHashMap<>();

  private final Map<UUID, float[]> botHeadRotation = new ConcurrentHashMap<>();

  private final Map<UUID, float[]> botSpawnRotation = new ConcurrentHashMap<>();

  private volatile boolean restorationInProgress = false;

  private volatile List<String> cleanNamePool = Collections.emptyList();

  private List<Player> cachedOnlinePlayers = new ArrayList<>();

  private int headAiTickCounter = 0;

  private volatile long visualSyncTickCounter = 0L;

  private final Set<UUID> bodyTransitionBots = new HashSet<>();

  private final Map<UUID, Integer> navJumpHolding = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<UUID, Location> actionLockedBots = new ConcurrentHashMap<>();

  private final Set<UUID> navLockedBots = ConcurrentHashMap.newKeySet();

  private final ConcurrentHashMap<UUID, String> despawningBotIds = new ConcurrentHashMap<>();

  private final Set<UUID> syntheticQuitBotIds = ConcurrentHashMap.newKeySet();

  private final Set<UUID> renamingBotIds = ConcurrentHashMap.newKeySet();

  private final Set<UUID> suppressDespawnSnapshotIds = ConcurrentHashMap.newKeySet();

  private final Map<UUID, Double> trackedFallDistance = new ConcurrentHashMap<>();

  private final Map<UUID, Double> lastFallY = new ConcurrentHashMap<>();

  private final Set<UUID> wasOnGround = ConcurrentHashMap.newKeySet();

  /**
   * Inventory + XP snapshot saved when a bot is manually despawned (only when
   * {@code dropItemsOnDespawn=false}). Keyed by lowercase bot name. Consumed on the next
   * same-name spawn so inventory and XP survive a manual despawn → spawn cycle.
   */
  private record DespawnSnapshot(
      ItemStack[] mainContents,
      ItemStack[] armorContents,
      ItemStack[] extraContents,
      int xpTotal,
      int xpLevel,
      float xpProgress,
      String skinTexture,
      String skinSignature) {
  }

  private final ConcurrentHashMap<String, DespawnSnapshot> despawnSnapshots =
      new ConcurrentHashMap<>();

  /**
   * YAML fallback file for despawn snapshots (used when DB is disabled).
   */
  private volatile File despawnSnapshotFile = null;

  private ChunkLoader chunkLoader;
  private DatabaseManager db;
  private BotPersistence persistence;
  private final AtomicBoolean dbLocationFlushRunning = new AtomicBoolean(false);

  private BotSwapController botSwapAI;

  private BotIdentityCache identityCache;

  public void setChunkLoader(ChunkLoader cl) {
    this.chunkLoader = cl;
  }

  public void setDatabaseManager(DatabaseManager db) {
    this.db = db;
  }

  public void setBotPersistence(BotPersistence p) {
    this.persistence = p;
  }

  public void setBotSwapAI(BotSwapController ai) {
    this.botSwapAI = ai;
  }

  public void setIdentityCache(BotIdentityCache ic) {
    this.identityCache = ic;
  }

  public void refreshCleanNamePool() {
    List<String> raw = Config.namePool();
    List<String> clean = new ArrayList<>(raw.size());
    for (String n : raw) {
      if (n == null || n.isEmpty() || n.length() > 16 || !n.matches("[a-zA-Z0-9_]+")) continue;
      if (!BadwordFilter.isAllowed(n)) continue;
      clean.add(n);
    }
    cleanNamePool = Collections.unmodifiableList(clean);
    Config.debugStartup(
        "Clean name pool refreshed: "
            + clean.size()
            + "/"
            + raw.size()
            + " names pass the badword filter.");
  }

  public BotChatController getBotChatAI() {
    return plugin.getBotChatAI();
  }

  public BotSwapController getBotSwapAI() {
    return botSwapAI;
  }

  public FakePlayerManager(FakePlayerPlugin plugin) {
    this.plugin = plugin;
    FAKE_PLAYER_KEY = new NamespacedKey(plugin, "fake_player_name");

    if (!AttributionManager.quickAuthorCheck()
        || !AttributionApiManager.quickEndpointCheck()) {
      FppLogger.warn("Plugin attribution integrity check failed in FakePlayerManager.");
    }

    long flushTicks = Math.max(20L, Config.dbLocationFlushInterval() * 20L);
    FppScheduler.runSyncRepeating(
        plugin,
        () -> {
          if (activePlayers.isEmpty()) return;
          for (FakePlayer fp : activePlayers.values()) {

            Location loc = fp.getLiveLocation();
            if (loc == null || loc.getWorld() == null) continue;
            String world = loc.getWorld().getName();

            fp.setSpawnLocation(loc.clone());
            if (db != null) {
              db.updateLastLocation(
                  fp.getUuid(),
                  world,
                  loc.getX(),
                  loc.getY(),
                  loc.getZ(),
                  loc.getYaw(),
                  loc.getPitch());
            }
          }
          if (db != null && dbLocationFlushRunning.compareAndSet(false, true)) {
            FppScheduler.runAsync(
                plugin,
                () -> {
                  try {
                    db.flushPendingLocations();
                  } finally {
                    dbLocationFlushRunning.set(false);
                  }
                });
          }
        },
        flushTicks,
        flushTicks);

    FppScheduler.runSyncRepeating(
        plugin,
        () -> {
          if (!Config.tabListEnabled()) return;
          if (activePlayers.isEmpty()) return;
          List<Player> online = cachedOnlinePlayers;
          if (online == null || online.isEmpty()) return;
          for (FakePlayer fp : activePlayers.values()) {
            if (!fp.isTabListDirty()) continue;
            fp.clearTabListDirty();
            for (Player p : online) {
              PacketHelper.sendTabListDisplayNameUpdate(p, fp);
              if (fp.hasCustomPing() || fp.isPingSimulated()) {
                PacketHelper.sendTabListLatencyUpdate(p, fp);
              }
            }
          }
        },
        20L,
        20L);

    final int pingInterval = Math.max(20, Config.pingUpdateInterval());
    FppScheduler.runSyncRepeating(
        plugin,
        () -> {
          if (!Config.pingEnabled()) return;
          if (activePlayers.isEmpty()) return;
          List<Player> online = cachedOnlinePlayers;
          if (online == null || online.isEmpty()) online = new ArrayList<>(Bukkit.getOnlinePlayers());
          int variability = Config.pingVariability();
          double spikeChance = Config.pingSpikeChance();
          int spikeMin = Config.pingSpikeMin();
          int spikeMax = Config.pingSpikeMax();
          long currentMs = System.currentTimeMillis();
          int rampTicks = Config.pingJoinRampTicks();
          int rampMs = rampTicks * 50;
          ThreadLocalRandom rng = ThreadLocalRandom.current();
          for (FakePlayer fp : activePlayers.values()) {
            if (fp.hasCustomPing()) continue;
            if (!fp.isPingSimulated()) continue;
            int base = fp.getBasePing();
            if (base < 0) continue;
            int jitter = variability > 0 ? rng.nextInt(-variability, variability + 1) : 0;
            int newPing = base + jitter;
            if (spikeChance > 0 && rng.nextDouble() < spikeChance) {
              int spike = spikeMin + rng.nextInt(Math.max(1, spikeMax - spikeMin + 1));
              newPing += spike;
            }
            if (rampMs > 0 && fp.getSpawnTick() > 0) {
              long elapsed = currentMs - fp.getSpawnTick();
              if (elapsed < rampMs) {
                double progress = (double) elapsed / rampMs;
                newPing = Math.max(1, (int) (newPing * progress));
              }
            }
            newPing = Math.max(1, Math.min(9999, newPing));
            fp.setPing(newPing);
            Player bot = fp.getPlayer();
            if (bot != null && bot.isOnline()) {
              NmsPlayerSpawner.setPing(bot, newPing);
            }
            for (Player p : online) {
              PacketHelper.sendTabListLatencyUpdate(p, fp);
            }
          }
        },
        pingInterval,
        pingInterval);

    FppScheduler.runSyncRepeating(
        plugin,
        () -> {
          if (activePlayers.isEmpty()) return;

          Collection<? extends Player> live = Bukkit.getOnlinePlayers();
          List<Player> online = cachedOnlinePlayers;
          if (online == null || online.size() != live.size()) {
            online = new ArrayList<>(live);
            cachedOnlinePlayers = online;
          }

          headAiTickCounter++;
          final boolean headAiOn = Config.headAiEnabled();
          final int headAiRate = Config.headAiTickRate();

          final boolean doHeadAi = headAiOn && (headAiTickCounter % headAiRate == 0);
          final double rangeSq =
              doHeadAi ? Config.headAiLookRange() * Config.headAiLookRange() : 0;
          final float speed = doHeadAi ? Config.headAiTurnSpeed() : 0f;

          final double psd = Config.positionSyncDistance();
          final double posSyncDistSq = psd > 0 ? psd * psd : -1;
          visualSyncTickCounter++;

          final int onlineCount = online.size();
          final double[] playerX = new double[onlineCount];
          final double[] playerY = new double[onlineCount];
          final double[] playerZ = new double[onlineCount];
          final World[] playerWorld = new World[onlineCount];
          for (int pi = 0; pi < onlineCount; pi++) {
            Location pl = online.get(pi).getLocation();
            playerX[pi] = pl.getX();
            playerY[pi] = pl.getY();
            playerZ[pi] = pl.getZ();
            playerWorld[pi] = pl.getWorld();
          }

          for (FakePlayer fp : activePlayers.values()) {
            Player bot = fp.getPlayer();

            if (bot == null || !bot.isValid() || !bot.isOnline() || bot.isDead()) continue;
            boolean sendVisualSyncThisTick = shouldSendLaggedVisualUpdate(fp);
            boolean runBehaviorThisTick = shouldRunLaggedBehaviorUpdate(fp);
            Location before = bot.getLocation();

            if (!fp.isFrozen()) {

              boolean isNavigating = plugin.getPathfindingService() != null
                  && plugin.getPathfindingService().isNavigating(fp.getUuid());

              if (runBehaviorThisTick && fp.isSwimAiEnabled()) {
                boolean navJump =
                    isNavigating && navJumpHolding.getOrDefault(fp.getUuid(), 0) > 0;
                PathfindingService.tickSwimAi(bot, navJump, isNavigating);
              }
              if (runBehaviorThisTick) {
                navJumpHolding.computeIfPresent(fp.getUuid(), (k, v) -> v > 1 ? v - 1 : null);
              }

              if (runBehaviorThisTick && fp.isAutoEatEnabled()) {
                tickAutoEat(bot);
              }

              // Sleeping bots: check every tick whether NMS has woken the bot
              // (bed broken, monsters nearby, time-skip complete). Syncing here
              // rather than waiting for the 40-tick nightWatchTick sweep means the
              // bot stands up on the exact same tick the bed is removed.
              if (fp.isSleeping()) {
                ServerPlayer nmsBot =
                    ((CraftPlayer) bot).getHandle();
                if (!nmsBot.isSleeping()) {
                  // NMS already woke the bot — clear flag and fall through to normal tick.
                  fp.setSleeping(false);
                  actionLockedBots.remove(fp.getUuid());
                  // fall through — bot immediately resumes normal physics/AI below
                } else {
                  // Still genuinely sleeping: zero velocity, tick physics so that
                  // NMS sleepCounter increments (needed for time-skip to dawn),
                  // then skip all AI/movement for this tick.
                  bot.setVelocity(ZERO_VELOCITY);
                  NmsPlayerSpawner.tickPhysics(bot);
                  var fppApiTickSleep = plugin.getFppApiImpl();
                  if (fppApiTickSleep != null) fppApiTickSleep.fireTickHandlers(fp, bot);
                  continue;
                }
              }

              NmsPlayerSpawner.tickPhysics(bot);

              boolean isActing = actingBots.contains(fp.getUuid());
              boolean isNavLocked = navLockedBots.contains(fp.getUuid());
              if (Config.debugHeadAi()) {
                if (isActing) {
                  Config.debug("HeadAI[" + fp.getName() + "]: SKIPPED - bot is acting (mining/using)");
                } else if (isNavLocked) {
                  Config.debug("HeadAI[" + fp.getName() + "]: SKIPPED - bot is nav-locked");
                } else if (!fp.isHeadAiEnabled()) {
                  Config.debug("HeadAI[" + fp.getName() + "]: SKIPPED - head AI disabled for bot");
                } else if (!doHeadAi) {
                  Config.debug("HeadAI[" + fp.getName() + "]: SKIPPED - not head AI tick (rate=" + headAiRate + ")");
                }
              }
              if (runBehaviorThisTick
                  && doHeadAi
                  && fp.isHeadAiEnabled()
                  && !isNavLocked
                  && !isActing) {

                // Head-AI target selection: only track a player who is actively
                // looking at this bot (eye-contact model). Conditions:
                //   1. Player is within look range (rangeSq)
                //   2. Bot has line of sight to the player
                //   3. The player's look direction points toward the bot
                //      within HEAD_AI_GAZE_COS (cos of ~15 degrees).
                // This prevents bots from mechanically staring at whoever
                // walks past; they only react when a player is gazing at them.
                Player target = null;
                double bestSq = rangeSq;
                for (int pi2 = 0; pi2 < onlineCount; pi2++) {
                  Player p = online.get(pi2);
                  if (activePlayers.containsKey(p.getUniqueId())) continue;
                  if (p.getGameMode() == GameMode.SPECTATOR) continue;
                  if (playerWorld[pi2] != before.getWorld()) continue;
                  double ddx = playerX[pi2] - before.getX();
                  double ddy = playerY[pi2] - before.getY();
                  double ddz = playerZ[pi2] - before.getZ();
                  double dSq = ddx * ddx + ddy * ddy + ddz * ddz;
                  if (dSq > bestSq) continue;
                  // Check that the player is looking toward this bot (gaze test).
                  // We compare the player's look direction against the unit vector
                  // from the player's eye to the bot's eye.
                  Vector lookDir = p.getEyeLocation().getDirection();
                  double botEyeX = before.getX() - playerX[pi2];
                  double botEyeY = (before.getY() + 1.62) - (playerY[pi2] + 1.62);
                  double botEyeZ = before.getZ() - playerZ[pi2];
                  double dist = Math.sqrt(botEyeX * botEyeX + botEyeY * botEyeY + botEyeZ * botEyeZ);
                  if (dist < 0.001) continue;
                  double dot = (lookDir.getX() * botEyeX + lookDir.getY() * botEyeY + lookDir.getZ() * botEyeZ) / dist;
                  // cos(15°) ≈ 0.9659 — tighter cone means more deliberate eye-contact
                  if (dot < HEAD_AI_GAZE_COS) continue;
                  if (!hasLineOfSightIgnoringGlass(bot, p)) continue;
                  bestSq = dSq;
                  target = p;
                }

                final Location beforeCapture = before;
                float[] rot =
                    botHeadRotation.computeIfAbsent(
                        fp.getUuid(),
                        k -> new float[]{beforeCapture.getYaw(), beforeCapture.getPitch()});

                float prevYaw = rot[0];
                float prevPitch = rot[1];

                if (target != null) {

                  Location eye = bot.getEyeLocation();
                  Location tgt = target.getEyeLocation();
                  double dx = tgt.getX() - eye.getX();
                  double dy = tgt.getY() - eye.getY();
                  double dz = tgt.getZ() - eye.getZ();
                  double horiz = Math.sqrt(dx * dx + dz * dz);
                  float targetYaw = (float) (-Math.toDegrees(Math.atan2(dx, dz)));
                  float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
                  rot[0] = lerpAngle(rot[0], targetYaw, speed);
                  rot[1] = lerpAngle(rot[1], targetPitch, speed);
                }

                if (Math.abs(rot[0] - prevYaw) > 0.01f
                    || Math.abs(rot[1] - prevPitch) > 0.01f) {
                  bot.setRotation(rot[0], rot[1]);
                  NmsPlayerSpawner.setHeadYaw(bot, rot[0]);
                  if (sendVisualSyncThisTick) {
                    for (int pi2 = 0; pi2 < onlineCount; pi2++) {
                      Player p = online.get(pi2);
                      if (p.getUniqueId().equals(fp.getUuid())) continue;
                      if (playerWorld[pi2] != before.getWorld()) continue;
                      if (posSyncDistSq > 0) {
                        double ddx = playerX[pi2] - before.getX();
                        double ddz = playerZ[pi2] - before.getZ();
                        if (ddx * ddx + ddz * ddz > posSyncDistSq) continue;
                      }
                      PacketHelper.sendRotation(p, fp, rot[0], rot[1], rot[0]);
                    }
                  }
                }
              }

              Location miningLock = actionLockedBots.get(fp.getUuid());
              if (runBehaviorThisTick && miningLock != null) {
                Location cur = bot.getLocation();
                boolean outOfPlace =
                    !cur.getWorld().equals(miningLock.getWorld())
                        || cur.distanceSquared(miningLock) > 0.0001;
                if (outOfPlace) {
                  FppScheduler.teleportAsync(bot, miningLock);
                }

                bot.setVelocity(ZERO_VELOCITY);
              }

              if (runBehaviorThisTick) {
                // Tick handlers represent bot input/AI. Under simulated latency they are delayed,
                // while entity physics still runs every tick so the server body remains valid.
                var fppApiTick = plugin.getFppApiImpl();
                if (fppApiTick != null) fppApiTick.fireTickHandlers(fp, bot);
              }
            }

            Location after = bot.getLocation();
            tickFallDamage(fp, bot, before, after);
            double dxM = before.getX() - after.getX();
            double dyM = before.getY() - after.getY();
            double dzM = before.getZ() - after.getZ();

            Vector vel2 = bot.getVelocity();
            boolean moved =
                before.getWorld() == after.getWorld()
                    && (dxM * dxM + dyM * dyM + dzM * dzM) > 1e-8;
            double vx = vel2.getX(), vy = vel2.getY(), vz2 = vel2.getZ();
            if (sendVisualSyncThisTick
                && (moved || (vx * vx + vy * vy + vz2 * vz2) > 1e-6)) {
              if (onlineCount > 0) {
                for (int pi2 = 0; pi2 < onlineCount; pi2++) {
                  Player p = online.get(pi2);
                  if (p.equals(bot)) continue;

                  if (posSyncDistSq > 0) {
                    if (playerWorld[pi2] != after.getWorld()) continue;
                    double ddx = playerX[pi2] - after.getX();
                    double ddy = playerY[pi2] - after.getY();
                    double ddz = playerZ[pi2] - after.getZ();
                    if (ddx * ddx + ddy * ddy + ddz * ddz > posSyncDistSq) continue;
                  }
                  PacketHelper.sendPositionSync(p, bot, after);
                }
              }
            }
          }
        },
        1L,
        1L);
  }

  private void tickFallDamage(FakePlayer fp, Player bot, Location before, Location after) {
    UUID uuid = fp.getUuid();
    if (!Config.fallDamageEnabled()
        || actionLockedBots.containsKey(uuid)
        || bot.isDead()
        || bot.getGameMode() == GameMode.CREATIVE
        || bot.getGameMode() == GameMode.SPECTATOR) {
      trackedFallDistance.remove(uuid);
      lastFallY.remove(uuid);
      wasOnGround.add(uuid);
      return;
    }

    boolean onGround = isBotOnGround(bot);
    if (!before.getWorld().equals(after.getWorld())) {
      trackedFallDistance.remove(uuid);
      lastFallY.remove(uuid);
      if (onGround) wasOnGround.add(uuid);
      else wasOnGround.remove(uuid);
      return;
    }

    if (!onGround && isFallDamageResetByCurrentBlock(bot)) {
      trackedFallDistance.remove(uuid);
      lastFallY.put(uuid, after.getY());
      wasOnGround.remove(uuid);
      return;
    }

    double currentY = after.getY();
    Double previousY = lastFallY.put(uuid, currentY);
    if (!onGround) {
      if (previousY != null && previousY > currentY) {
        addTrackedFallDistance(uuid, previousY - currentY, bot.getFallDistance());
      }
      wasOnGround.remove(uuid);
      return;
    }

    if (previousY != null && previousY > currentY) {
      addTrackedFallDistance(uuid, previousY - currentY, bot.getFallDistance());
    }

    double distance = Math.max(trackedFallDistance.getOrDefault(uuid, 0.0), bot.getFallDistance());
    double safeDistance = Config.fallDamageSafeDistance();
    if (!wasOnGround.contains(uuid) && distance > safeDistance) {
      double damage =
          Math.floor(
              (distance - safeDistance)
                  * Config.fallDamageMultiplier()
                  * landingFallDamageMultiplier(bot));
      if (damage > 0.0) {
        double beforeHealth = bot.getHealth();
        bot.damage(damage);
        if (!bot.isDead() && Math.abs(bot.getHealth() - beforeHealth) < 0.001) {
          bot.setHealth(Math.max(0.0, beforeHealth - damage));
          playHurtFeedback(fp, bot);
        }
      }
    }
    trackedFallDistance.remove(uuid);
    lastFallY.remove(uuid);
    wasOnGround.add(uuid);
  }

  private void addTrackedFallDistance(UUID uuid, double delta, double bukkitFallDistance) {
    if (delta <= 0.0) return;
    double distance = trackedFallDistance.getOrDefault(uuid, 0.0) + delta;
    trackedFallDistance.put(uuid, Math.max(distance, bukkitFallDistance));
  }

  private boolean isBotOnGround(Player bot) {
    return !bot.getLocation().clone().subtract(0, 0.08, 0).getBlock().isPassable();
  }

  private boolean isFallDamageResetByCurrentBlock(Player bot) {
    if (bot.isInWater() || bot.isInLava()) return true;
    Material feet = bot.getLocation().getBlock().getType();
    Material below = bot.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
    return isFallDamageResetBlock(feet) || isFallDamageResetBlock(below);
  }

  private boolean isFallDamageResetBlock(Material material) {
    if (Tag.CLIMBABLE.isTagged(material)) return true;
    return material == Material.BUBBLE_COLUMN
        || material == Material.COBWEB
        || material == Material.POWDER_SNOW
        || material == Material.SLIME_BLOCK;
  }

  private double landingFallDamageMultiplier(Player bot) {
    if (isFallDamageResetByCurrentBlock(bot)) return 0.0;
    Material feet = bot.getLocation().getBlock().getType();
    Material below = bot.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
    if (feet == Material.HAY_BLOCK || below == Material.HAY_BLOCK) return 0.2;
    if (feet.name().endsWith("_BED") || below.name().endsWith("_BED")) return 0.5;
    return 1.0;
  }

  public void playHurtFeedback(FakePlayer fp, Player bot) {
    for (Player viewer : cachedOnlinePlayers) {
      if (viewer == null
          || !viewer.isOnline()
          || viewer.getWorld() != bot.getWorld()
          || viewer.getLocation().distanceSquared(bot.getLocation()) > 256 * 256) {
        continue;
      }
      PacketHelper.sendHurtAnimation(viewer, fp);
    }

    if (!Config.hurtSound()) {
      return;
    }

    Location loc = bot.getLocation();
    if (loc.getWorld() != null) {
      loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }
  }

  public boolean physicalBodiesEnabled() {
    return true;
  }

  public boolean isRestorationInProgress() {
    return restorationInProgress;
  }

  public void setRestorationInProgress(boolean inProgress) {
    this.restorationInProgress = inProgress;
  }

  public boolean isBodyTransitioning(UUID uuid) {
    return bodyTransitionBots.contains(uuid);
  }

  public boolean isDespawning(UUID uuid) {
    return despawningBotIds.containsKey(uuid);
  }

  public String getDespawningDisplayName(UUID uuid) {
    return despawningBotIds.get(uuid);
  }

  public boolean hasSyntheticQuit(UUID uuid) {
    return uuid != null && syntheticQuitBotIds.contains(uuid);
  }

  public void markDespawning(UUID uuid, String displayName) {
    despawningBotIds.put(uuid, displayName);
  }

  public void clearDespawningNextTick(UUID uuid) {
    FppScheduler.runSyncLater(
        plugin,
        () -> {
          despawningBotIds.remove(uuid);
          syntheticQuitBotIds.remove(uuid);
        },
        1L);
  }

  public void broadcastSyntheticQuit(
      FakePlayer fp,
      String displayName,
      boolean useDefaultMessage,
      PlayerQuitEvent.QuitReason reason) {
    if (fp == null) return;
    Player player = fp.getPlayer();
    if (player == null) player = fp.getPhysicsEntity();
    if (player == null) return;

    Component defaultMessage =
        useDefaultMessage && displayName != null && !displayName.isBlank()
            ? BotBroadcast.leaveComponent(fp)
            : null;
    PlayerQuitEvent quitEvent =
        new PlayerQuitEvent(player, defaultMessage, reason);
    Bukkit.getPluginManager().callEvent(quitEvent);
    syntheticQuitBotIds.add(fp.getUuid());

    Component message = quitEvent.quitMessage();
    if (message == null) return;
    for (Player online : Bukkit.getOnlinePlayers()) {
      if (online != null && online.isOnline()) online.sendMessage(message);
    }
    Bukkit.getConsoleSender().sendMessage(message);
  }

  public void markRenaming(UUID uuid) {
    renamingBotIds.add(uuid);
  }

  public void unmarkRenaming(UUID uuid) {
    renamingBotIds.remove(uuid);
  }

  public boolean isRenaming(UUID uuid) {
    return renamingBotIds.contains(uuid);
  }

  public void suppressNextDespawnSnapshot(UUID uuid) {
    if (uuid != null) suppressDespawnSnapshotIds.add(uuid);
  }

  public void clearDespawnSnapshotSuppression(UUID uuid) {
    if (uuid != null) suppressDespawnSnapshotIds.remove(uuid);
  }

  public String formatLocationForDisplay(FakePlayer fp) {
    if (!physicalBodiesEnabled()) {
      return "No Body";
    }
    var body = fp.getPhysicsEntity();
    if (body != null && body.isValid()) {
      var l = body.getLocation();
      return (l.getWorld() != null ? l.getWorld().getName() : "?")
          + " "
          + l.getBlockX()
          + ","
          + l.getBlockY()
          + ","
          + l.getBlockZ();
    }
    var sl = fp.getSpawnLocation();
    if (sl != null)
      return (sl.getWorld() != null ? sl.getWorld().getName() : "?")
          + " "
          + sl.getBlockX()
          + ","
          + sl.getBlockY()
          + ","
          + sl.getBlockZ();
    return "unknown";
  }

  public Location getLastKnownLocation(String botName) {
    if (botName == null || botName.isBlank()) return null;

    FakePlayer active = getByName(botName);
    if (active != null) {
      Location live = active.getLiveLocation();
      return live != null ? live.clone() : null;
    }

    if (db == null) return null;

    UUID uuid = resolveUuid(botName);
    if (uuid == null) return null;

    List<BotRecord> sessions = db.getSessionsByUuid(uuid);
    if (sessions.isEmpty()) return null;

    BotRecord session = sessions.get(0);
    String worldName =
        session.getLastWorld() != null && !session.getLastWorld().isBlank()
            ? session.getLastWorld()
            : session.getWorldName();
    if (worldName == null || worldName.isBlank()) return null;

    World world = Bukkit.getWorld(worldName);
    if (world == null) return null;

    return new Location(
        world,
        session.getLastX(),
        session.getLastY(),
        session.getLastZ(),
        session.getLastYaw(),
        session.getLastPitch());
  }

  public int spawn(Location location, int count, Player spawner) {
    return spawn(location, count, spawner, null, false, BotType.AFK);
  }

  public int spawn(Location location, int count, Player spawner, String customName) {
    return spawn(location, count, spawner, customName, false, BotType.AFK);
  }

  public int spawnUserBot(Location location, int count, Player spawner, boolean bypassMax) {
    return spawnUserBot(location, count, spawner, bypassMax, BotType.AFK);
  }

  public int spawnUserBot(
      Location location, int count, Player spawner, boolean bypassMax, BotType botType) {
    int maxBots = Config.maxBots();
    if (!bypassMax && maxBots > 0) {
      int available = maxBots - activePlayers.size();
      if (available <= 0) return -1;
      count = Math.min(count, available);
    }

    String spawnerName = spawner.getName();
    UUID spawnerUuid = spawner.getUniqueId();

    int alreadyOwned = getBotsOwnedBy(spawnerUuid).size();

    List<FakePlayer> batch = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      UserBotName ubn = generateUserBotName(spawnerName, alreadyOwned + i);
      UUID uuid = resolveUuid(ubn.internalName());
      PlayerProfile profile = Bukkit.createProfile(uuid, ubn.internalName());
      FakePlayer fp = new FakePlayer(uuid, ubn.internalName(), profile);
      fp.setBotType(botType);

      fp.setSkinName(spawnerName);
      applyDespawnSnapshotSkin(fp);

      String cleanBotName = "bot" + (alreadyOwned + i + 1);
      String rawUserName =
          Config.userBotNameFormat()
              .replace("{spawner}", spawnerName)
              .replace("{num}", String.valueOf(alreadyOwned + i + 1))
              .replace("{bot_name}", cleanBotName);

      fp.setRawDisplayName(rawUserName);
      String userDisplay = finalizeDisplayName(rawUserName, ubn.internalName());
      fp.setDisplayName(userDisplay);
      fp.setSpawnLocation(location);
      fp.setSpawnedBy(spawnerName, spawnerUuid);
      fp.setSpawnTick(System.currentTimeMillis());
      activePlayers.put(uuid, fp);
      nameIndex.put(ubn.internalName().toLowerCase(), fp);
      batch.add(fp);

      if (db != null) {
        BotRecord record =
            new BotRecord(
                0,
                ubn.internalName(),
                uuid,
                spawnerName,
                spawnerUuid,
                location.getWorld() != null ? location.getWorld().getName() : "unknown",
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                Instant.now(),
                null,
                null);
        fp.setDbRecord(record);
        db.recordSpawn(
            record,
            PlainTextComponentSerializer.plainText()
                .serialize(TextUtil.colorize(ubn.displayName())));
        persistActiveSkin(fp);
      }
    }
    if (batch.isEmpty()) return 0;

    int total = batch.size();
    FppScheduler.runSync(plugin, () -> visualChain(batch, 0, location));
    return total;
  }

  public int spawn(
      Location location, int count, Player spawner, String customName, boolean bypassMax) {
    return spawn(location, count, spawner, customName, bypassMax, BotType.AFK);
  }

  public int spawn(
      Location location,
      int count,
      Player spawner,
      String customName,
      boolean bypassMax,
      BotType botType) {
    return spawn(location, count, spawner, customName, bypassMax, botType, false);
  }

  public int spawn(
      Location location,
      int count,
      Player spawner,
      String customName,
      boolean bypassMax,
      BotType botType,
      boolean forceRandomName) {
    return spawn(location, count, spawner, customName, bypassMax, botType, forceRandomName, null);
  }

  public int spawn(
      Location location,
      int count,
      Player spawner,
      String customName,
      boolean bypassMax,
      UUID explicitUuid) {
    return spawn(location, count, spawner, customName, bypassMax, BotType.AFK, false, explicitUuid);
  }

  private int spawn(
      Location location,
      int count,
      Player spawner,
      String customName,
      boolean bypassMax,
      BotType botType,
      boolean forceRandomName,
      UUID explicitUuid) {
    int maxBots = Config.maxBots();
    if (!bypassMax && maxBots > 0) {
      int available = maxBots - activePlayers.size();
      if (available <= 0) return -1;
      count = Math.min(count, available);
    }

    String spawnerName = spawner != null ? spawner.getName() : "CONSOLE";
    UUID spawnerUuid = spawner != null ? spawner.getUniqueId() : new UUID(0, 0);
    UUID resolvedCustomUuid = null;

    if (customName != null) {

      String effectiveName = customName;

      if (effectiveName.isEmpty()
          || effectiveName.length() > 16
          || !effectiveName.matches("[a-zA-Z0-9_]+")) return -2;

      if (usedNames.contains(effectiveName)) return 0;
      if (getByName(effectiveName) != null) return 0;
      if (explicitUuid != null && activePlayers.containsKey(explicitUuid)) return 0;

      Player realPlayer = Bukkit.getPlayerExact(effectiveName);
      if (realPlayer != null && !activePlayers.containsKey(realPlayer.getUniqueId())) return -4;
      if (explicitUuid != null) {
        Player realPlayerByUuid = Bukkit.getPlayer(explicitUuid);
        if (realPlayerByUuid != null && !activePlayers.containsKey(explicitUuid)) return -4;
      }
      resolvedCustomUuid = explicitUuid != null ? explicitUuid : resolveUuid(effectiveName);
      if (activePlayers.containsKey(resolvedCustomUuid)) return 0;

      count = 1;
    }

    List<FakePlayer> batch = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      String baseName;
      String name;

      if (customName != null) {
        baseName = customName;
        name = customName;
      } else {
        name = generateName(forceRandomName);
        baseName = name;
      }

      if (name == null) break;
      UUID uuid =
          resolvedCustomUuid != null
              ? resolvedCustomUuid
              : explicitUuid != null ? explicitUuid : resolveUuid(name);
      PlayerProfile profile = Bukkit.createProfile(uuid, name);
      FakePlayer fp = new FakePlayer(uuid, name, profile);
      if (explicitUuid != null) {
        fp.setMetadata("fpp.explicit-uuid-spawn", true);
        fp.setMetadata("fpp.explicit-uuid-was-op", Bukkit.getOfflinePlayer(explicitUuid).isOp());
        snapshotExplicitUuidPlayerData(fp);
      }
      fp.setBotType(botType);

      fp.setSkinName(baseName != null ? baseName : name);
      applyDespawnSnapshotSkin(fp);

      String rawAdminName = Config.adminBotNameFormat().replace("{bot_name}", name);
      fp.setRawDisplayName(rawAdminName);
      String displayName = finalizeDisplayName(rawAdminName, name);
      fp.setDisplayName(displayName);
      fp.setSpawnLocation(location);
      fp.setSpawnedBy(spawnerName, spawnerUuid);
      fp.setSpawnTick(System.currentTimeMillis());
      usedNames.add(name);
      activePlayers.put(uuid, fp);
      nameIndex.put(name.toLowerCase(), fp);
      batch.add(fp);

      if (db != null) {
        BotRecord record =
            new BotRecord(
                0,
                name,
                uuid,
                spawnerName,
                spawnerUuid,
                location.getWorld() != null ? location.getWorld().getName() : "unknown",
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                Instant.now(),
                null,
                null);
        fp.setDbRecord(record);
        db.recordSpawn(
            record,
            PlainTextComponentSerializer.plainText()
                .serialize(TextUtil.colorize(displayName)));
        persistActiveSkin(fp);
      }
    }
    if (batch.isEmpty()) return 0;

    int total = batch.size();

    FppScheduler.runSync(plugin, () -> visualChain(batch, 0, location));
    return total;
  }

  public int spawnBodyless(
      Location location,
      int count,
      Player spawner,
      String customName,
      boolean bypassMax,
      boolean spawnBodyless) {
    return spawnBodyless(
        location, count, spawner, customName, bypassMax, spawnBodyless, BotType.AFK);
  }

  public int spawnBodyless(
      Location location,
      int count,
      Player spawner,
      String customName,
      boolean bypassMax,
      boolean spawnBodyless,
      BotType botType) {
    int result = spawn(location, count, spawner, customName, bypassMax, botType);
    if (result > 0 && spawnBodyless) {

      String spawnerName = spawner != null ? spawner.getName() : "CONSOLE";
      UUID spawnerUuid = spawner != null ? spawner.getUniqueId() : new UUID(0, 0);

      long now = System.currentTimeMillis();
      activePlayers.values().stream()
          .filter(
              fp ->
                  fp.getSpawnedBy().equals(spawnerName)
                      && fp.getSpawnedByUuid().equals(spawnerUuid)
                      && (now - fp.getSpawnTime().toEpochMilli()) < 1000)
          .limit(result)
          .forEach(fp -> fp.setBodyless(true));
    }
    return result;
  }

  private void visualChain(List<FakePlayer> batch, int index, Location location) {
    if (batch == null) return;

    for (int i = index; i < batch.size(); i++) {
      FakePlayer fp = batch.get(i);
      if (activePlayers.containsKey(fp.getUuid())) finishSpawn(fp, location);
    }
  }

  private void finishSpawn(FakePlayer fp, Location spawnLoc) {
    fp.setSpawnTime(Instant.now());
    spawnBodyAndFinish(fp, spawnLoc);
  }

  private int computeInitialPing(FakePlayer fp) {
    if (fp.hasCustomPing()) {
      return Math.max(0, fp.getPing());
    }
    if (Config.pingEnabled()) {
      int min = Config.pingMin();
      int max = Config.pingMax();
      int base = min + ThreadLocalRandom.current().nextInt(Math.max(1, max - min + 1));
      fp.setBasePing(base);
      fp.setPing(base);
      return base;
    }
    return -1;
  }

  private boolean isExplicitUuidSpawn(FakePlayer fp) {
    return fp != null && Boolean.TRUE.equals(fp.getMetadata("fpp.explicit-uuid-spawn"));
  }

  public boolean isExplicitUuidBot(FakePlayer fp) {
    return isExplicitUuidSpawn(fp);
  }

  private boolean wasExplicitUuidOp(FakePlayer fp) {
    return fp != null && Boolean.TRUE.equals(fp.getMetadata("fpp.explicit-uuid-was-op"));
  }

  private void restoreExplicitUuidOperator(FakePlayer fp) {
    if (!wasExplicitUuidOp(fp)) return;
    try {
      Bukkit.getOfflinePlayer(fp.getUuid()).setOp(true);
      Config.debug("Restored operator status for explicit UUID bot source '" + fp.getName() + "'.");
    } catch (Throwable t) {
      FppLogger.warn(
          "Failed to restore operator status for explicit UUID bot source '"
              + fp.getName()
              + "': "
              + t.getMessage());
    }
  }

  private void snapshotExplicitUuidPlayerData(FakePlayer fp) {
    Path file = explicitUuidPlayerDataFile(fp.getUuid());
    if (file == null) return;
    fp.setMetadata("fpp.explicit-uuid-playerdata-path", file.toString());
    try {
      if (Files.exists(file)) {
        fp.setMetadata("fpp.explicit-uuid-playerdata-bytes", Files.readAllBytes(file));
      } else {
        fp.setMetadata("fpp.explicit-uuid-playerdata-missing", true);
      }
    } catch (Throwable t) {
      FppLogger.warn(
          "Failed to snapshot playerdata for explicit UUID bot source '"
              + fp.getName()
              + "': "
              + t.getMessage());
    }
  }

  private void restoreExplicitUuidPlayerData(FakePlayer fp) {
    if (!isExplicitUuidSpawn(fp)) return;
    Object rawPath = fp.getMetadata("fpp.explicit-uuid-playerdata-path");
    if (!(rawPath instanceof String pathString) || pathString.isBlank()) return;

    Path file = Path.of(pathString);
    try {
      Object rawBytes = fp.getMetadata("fpp.explicit-uuid-playerdata-bytes");
      if (rawBytes instanceof byte[] bytes) {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".fpp-restore.tmp");
        Files.write(tmp, bytes);
        try {
          Files.move(
              tmp,
              file,
              StandardCopyOption.REPLACE_EXISTING,
              StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
          Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        Config.debug("Restored playerdata for explicit UUID bot source '" + fp.getName() + "'.");
      } else if (Boolean.TRUE.equals(fp.getMetadata("fpp.explicit-uuid-playerdata-missing"))) {
        Files.deleteIfExists(file);
      }
    } catch (Throwable t) {
      FppLogger.warn(
          "Failed to restore playerdata for explicit UUID bot source '"
              + fp.getName()
              + "': "
              + t.getMessage());
    }
  }

  public void restoreExplicitUuidSourceState(FakePlayer fp) {
    restoreExplicitUuidPlayerData(fp);
    restoreExplicitUuidOperator(fp);
  }

  private Path explicitUuidPlayerDataFile(UUID uuid) {
    if (uuid == null || Bukkit.getWorlds().isEmpty()) return null;
    File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
    if (worldFolder == null) return null;
    return worldFolder.toPath().resolve("playerdata").resolve(uuid + ".dat");
  }

  private void spawnBodyAndFinish(FakePlayer fp, Location spawnLoc) {

    FakePlayerBody.resolveAndFinish(
        plugin,
        fp,
        spawnLoc,
        () -> {
          if (!activePlayers.containsKey(fp.getUuid())) {
            Config.debug(
                "finishSpawn aborted for '"
                    + fp.getName()
                    + "' - removed before body spawn callback fired.");
            return;
          }

          int initialPing = computeInitialPing(fp);

          if (!fp.isBodyless()) {
            Player body = FakePlayerBody.spawn(fp, spawnLoc, initialPing);
            if (body != null) {
              fp.setPhysicsEntity(body);
              entityIdIndex.put(body.getEntityId(), fp);
              fp.setPacketProfileName(fp.getName());
              // Ping was already set on the NMS ServerPlayer before placeNewPlayer,
              // but update it again here in case the pre-spawn set didn't take effect.
              NmsPlayerSpawner.setPing(body, fp.getEffectivePing());

              if (plugin.isWorldGuardAvailable()
                  && !WorldGuardHelper.isPvpAllowed(spawnLoc)) {
                Config.debug(
                    "WorldGuard: bot '"
                        + fp.getName()
                        + "' spawned in a no-pvp region at "
                        + spawnLoc.getBlockX()
                        + ","
                        + spawnLoc.getBlockY()
                        + ","
                        + spawnLoc.getBlockZ());
              }

              final Player savedBody = body;
              final String savedName = fp.getName();
              final UUID savedUuid = fp.getUuid();
              final boolean skipPlayerDataSave = isExplicitUuidSpawn(fp);
              if (wasExplicitUuidOp(fp)) {
                try {
                  savedBody.setOp(true);
                } catch (Throwable ignored) {
                }
              }
              FppScheduler.runSyncLater(
                  plugin,
                  () -> {
                    if (!savedBody.isOnline()) return;
                    if (skipPlayerDataSave) {
                      FppLogger.debug(
                          "FakePlayerManager: skipped initial playerdata save for explicit UUID bot '"
                              + savedName
                              + "' uuid="
                              + savedUuid);
                      return;
                    }
                    try {

                      PlayerJoinListener.stampFirstPlayed(savedBody);
                      savedBody.saveData();
                      FppLogger.debug(
                          "FakePlayerManager: initial playerdata"
                              + " saved for '"
                              + savedName
                              + "' uuid="
                              + savedUuid);
                    } catch (Exception e) {
                      FppLogger.warn(
                          "FakePlayerManager: initial saveData"
                              + " failed for '"
                              + savedName
                              + "' uuid="
                              + savedUuid
                              + ": "
                              + e.getMessage());
                    }
                  },
                  2L);
            } else {
              FppLogger.warn(
                  "finishSpawn: body spawn failed for '"
                      + fp.getName()
                      + "' - rolling back bot to avoid ghost keepalive connection.");
              delete(fp.getName());
              return;
            }
          } else if (fp.isBodyless()) {

            Config.debug("Bodyless spawn: skipping physical body for " + fp.getName());
          }

          List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
          if (Config.tabListEnabled()) {
            boolean isNmsPlayer = fp.getPlayer() != null;
            Config.debug(
                "Sending tab-list for '"
                    + fp.getName()
                    + "' display='"
                    + fp.getDisplayName()
                    + "' packet='"
                    + fp.getPacketProfileName()
                    + "' nms="
                    + isNmsPlayer);

            if (isNmsPlayer) {
              for (Player p : online) PacketHelper.sendTabListAdd(p, fp);
              if (fp.hasCustomPing() || fp.isPingSimulated()) {
                for (Player p : online) PacketHelper.sendTabListLatencyUpdate(p, fp);
              }
            } else {

              for (Player p : online) PacketHelper.sendTabListAdd(p, fp);
              if (fp.hasCustomPing() || fp.isPingSimulated()) {
                for (Player p : online) PacketHelper.sendTabListLatencyUpdate(p, fp);
              }
            }

            FppScheduler.runSyncLater(
                plugin,
                () -> {
                  if (!activePlayers.containsKey(fp.getUuid())) return;
                  boolean nms = fp.getPlayer() != null;
                  Config.debug("Re-sending tab-list display-name (3t) for '" + fp.getName() + "' nms=" + nms);
                  for (Player p : Bukkit.getOnlinePlayers())
                    PacketHelper.sendTabListDisplayNameUpdate(p, fp);
                },
                3L);

            FppScheduler.runSyncLater(
                plugin,
                () -> {
                  if (!activePlayers.containsKey(fp.getUuid())) return;
                  Config.debug("Finalising tab-list for '" + fp.getName() + "'");

                  var vc = plugin.getVelocityChannel();
                  if (vc != null) vc.broadcastBotSpawn(fp);
                },
                20L);
          } else {
            Config.debug("Tab-list disabled - unlisting '" + fp.getName() + "'");

            Player bot = fp.getPlayer();
            if (bot != null) NmsPlayerSpawner.setListed(bot, false);

            FppScheduler.runSyncLater(
                plugin,
                () -> {
                  if (!activePlayers.containsKey(fp.getUuid())) return;
                  for (Player p : Bukkit.getOnlinePlayers()) {
                    PacketHelper.sendTabListUpdateListed(p, fp, false);
                  }

                  var vc = plugin.getVelocityChannel();
                  if (vc != null) vc.broadcastBotSpawn(fp);
                },
                3L);
          }

          if (persistence != null && Config.persistOnRestart()) {
            persistence.saveAsync(activePlayers.values());
          }

          // Restore inventory+XP from a prior manual despawn of the same bot name.
          DespawnSnapshot despawnSnap = despawnSnapshots.remove(fp.getName().toLowerCase());
          if (despawnSnap != null) {
            removeDespawnSnapshotPersistent(fp.getName().toLowerCase());
            final UUID snapBotUuid = fp.getUuid();
            FppScheduler.runSyncLater(
                plugin,
                () -> {
                  FakePlayer restoredFp = getByUuid(snapBotUuid);
                  if (restoredFp == null) return;
                  Player restoredBot = restoredFp.getPlayer();
                  if (restoredBot == null || !restoredBot.isValid()) return;
                  applyDespawnSnapshot(restoredBot, despawnSnap);
                  Config.debugSwap(
                      "[DespawnSnapshot] Restored inventory+XP to '" + restoredFp.getName() + "'.");
                },
                5L);
          }

          if (botSwapAI != null) {
            FppScheduler.runSyncLater(
                plugin,
                () -> {
                  if (!activePlayers.containsKey(fp.getUuid())) return;
                  botSwapAI.schedule(fp);
                },
                10L);
          }

          // Fire API spawn event.
          var fppApi = plugin.getFppApi();
          if (fppApi != null) {
            FppBotSpawnEvent spawnEvt =
                new FppBotSpawnEvent(
                    new FppBotImpl(fp), fp.isRestoredSpawn());
            Bukkit.getPluginManager().callEvent(spawnEvt);
          }
        },
        () -> {
          if (!activePlayers.containsKey(fp.getUuid())) return;
          if (Config.tabListEnabled()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
              PacketHelper.sendTabListDisplayNameUpdate(p, fp);
            }
          }
        });
  }

  public void spawnRestored(
      String name,
      UUID uuid,
      String savedDisplayName,
      String spawnedBy,
      UUID spawnedByUuid,
      Location location) {
    spawnRestored(name, uuid, savedDisplayName, spawnedBy, spawnedByUuid, location, BotType.AFK);
  }

  public void spawnRestored(
      String name,
      UUID uuid,
      String savedDisplayName,
      String spawnedBy,
      UUID spawnedByUuid,
      Location location,
      BotType botType) {
    spawnRestored(name, uuid, savedDisplayName, spawnedBy, spawnedByUuid, location, botType, null);
  }

  public void spawnRestored(
      String name,
      UUID uuid,
      String savedDisplayName,
      String spawnedBy,
      UUID spawnedByUuid,
      Location location,
      BotType botType,
      SkinProfile restoredSkin) {

    if (usedNames.contains(name)) return;

    UUID restoredUuid = resolveUuid(name);
    if (uuid != null && !uuid.equals(restoredUuid)) {
      Config.debugDatabase(
          "Restored bot '"
              + name
              + "' UUID corrected from stored "
              + uuid
              + " to "
              + restoredUuid);
    }

    PlayerProfile profile = Bukkit.createProfile(restoredUuid, name);
    FakePlayer fp = new FakePlayer(restoredUuid, name, profile);
    fp.setBotType(botType);
    fp.setRestoredSpawn(true);
    if (restoredSkin != null && restoredSkin.isValid()) {
      fp.setResolvedSkin(restoredSkin);
      Config.debugSkin("Restored persisted skin for bot '" + name + "'");
    }

    boolean isUserBot = name.startsWith("ubot_");
    if (isUserBot) {
      fp.setSkinName(
          spawnedBy != null && !spawnedBy.isBlank()
              ? spawnedBy
              : name);
    } else {
      fp.setSkinName(name);
    }

    String effectiveSpawner = (spawnedBy != null && !spawnedBy.isBlank()) ? spawnedBy : "Unknown";
    String displayName;

    if (isUserBot) {
      int lastUs = name.lastIndexOf('_');
      int botIdx = 1;
      if (lastUs > 0 && lastUs < name.length() - 1) {
        try {
          botIdx = Integer.parseInt(name.substring(lastUs + 1));
        } catch (NumberFormatException ignored) {
          botIdx = 1;
        }
      }
      String rawName =
          Config.userBotNameFormat()
              .replace("{spawner}", effectiveSpawner)
              .replace("{num}", String.valueOf(botIdx))
              .replace("{bot_name}", name);

      Config.debug(
          "[Restore] user-bot '"
              + name
              + "' type="
              + botType
              + " spawner='"
              + effectiveSpawner
              + "' num="
              + botIdx);
      fp.setRawDisplayName(rawName);
      displayName = finalizeDisplayName(rawName, name);
    } else {
      String rawName = Config.adminBotNameFormat().replace("{bot_name}", name);
      Config.debug("[Restore] admin-bot '" + name + "' type=" + botType);
      fp.setRawDisplayName(rawName);
      displayName = finalizeDisplayName(rawName, name);
    }

    fp.setDisplayName(displayName);
    fp.setSpawnLocation(location);
    fp.setSpawnedBy(effectiveSpawner, spawnedByUuid);
    usedNames.add(name);
    fp.setSpawnTick(System.currentTimeMillis());
    activePlayers.put(restoredUuid, fp);
    nameIndex.put(name.toLowerCase(), fp);

    if (db != null) {
      BotRecord record =
          new BotRecord(
              0,
              name,
              restoredUuid,
              effectiveSpawner,
              spawnedByUuid,
              location.getWorld() != null ? location.getWorld().getName() : "unknown",
              location.getX(),
              location.getY(),
              location.getZ(),
              location.getYaw(),
              location.getPitch(),
              Instant.now(),
              null,
              null);
      fp.setDbRecord(record);
      String plainDisplay =
          PlainTextComponentSerializer.plainText()
              .serialize(TextUtil.colorize(displayName));
      db.recordSpawn(record, plainDisplay);
    }

    finishSpawn(fp, location);
    Config.debug("Restored bot: " + name + " at " + location);
  }

  public void validateUserBotNames(UUID spawnerUuid, String spawnerName) {
    if (activePlayers.isEmpty()) return;
    List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
    for (FakePlayer fp : activePlayers.values()) {
      if (!spawnerUuid.equals(fp.getSpawnedByUuid())) continue;
      if (!fp.getName().startsWith("ubot_")) continue;

      String current = fp.getDisplayName();

      if (!PLACEHOLDER_PATTERN.matcher(current).find()) continue;

      String botName = fp.getName();
      int lastUs = botName.lastIndexOf('_');
      int idx = 1;
      if (lastUs > 0 && lastUs < botName.length() - 1) {
        try {
          idx = Integer.parseInt(botName.substring(lastUs + 1));
        } catch (NumberFormatException ignored) {
          idx = 1;
        }
      }
      String rawDisplay =
          Config.userBotNameFormat()
              .replace("{spawner}", spawnerName)
              .replace("{num}", String.valueOf(idx))
              .replace("{bot_name}", fp.getName());
      fp.setRawDisplayName(rawDisplay);
      String newDisplay = finalizeDisplayName(rawDisplay, fp.getName());
      fp.setDisplayName(newDisplay);

      if (Config.tabListEnabled()) {
        for (Player p : online) PacketHelper.sendTabListDisplayNameUpdate(p, fp);
      }
      FppLogger.warn(
          "[FPP] Repaired placeholder name for bot '"
              + fp.getName()
              + "' (owner: "
              + spawnerName
              + ") → '"
              + newDisplay
              + "'");
    }
  }

  public void removeAll() {
    if (activePlayers.isEmpty()) return;

    List<FakePlayer> toRemove = new ArrayList<>(activePlayers.values());

    // Snapshot inventory + XP for ALL bots BEFORE clearing maps or removing entities.
    // This ensures /fpp despawn all preserves items just like single-bot despawn does.
    boolean preserveInventoryOnDespawn = !Config.dropItemsOnDespawn();
    for (FakePlayer fp : toRemove) {
      if (renamingBotIds.contains(fp.getUuid())) continue;
      if (isExplicitUuidSpawn(fp)) continue;
      if (!preserveInventoryOnDespawn && !hasResolvedSkin(fp)) continue;
      Player snapBody = fp.getPhysicsEntity();
      if (snapBody != null && snapBody.isOnline()) {
        String botName = fp.getName();
        DespawnSnapshot snap =
            new DespawnSnapshot(
                preserveInventoryOnDespawn
                    ? cloneContents(snapBody.getInventory().getContents())
                    : new ItemStack[41],
                preserveInventoryOnDespawn
                    ? cloneContents(snapBody.getInventory().getArmorContents())
                    : new ItemStack[4],
                preserveInventoryOnDespawn
                    ? cloneContents(snapBody.getInventory().getExtraContents())
                    : new ItemStack[1],
                preserveInventoryOnDespawn ? snapBody.getTotalExperience() : 0,
                preserveInventoryOnDespawn ? snapBody.getLevel() : 0,
                preserveInventoryOnDespawn ? snapBody.getExp() : 0f,
                fp.getResolvedSkin() != null ? fp.getResolvedSkin().getValue() : null,
                fp.getResolvedSkin() != null ? fp.getResolvedSkin().getSignature() : null);
        despawnSnapshots.put(botName.toLowerCase(), snap);
        persistDespawnSnapshot(botName.toLowerCase(), snap);
        Config.debugSwap("[DespawnSnapshot] Saved despawn state for '" + botName + "' (bulk despawn).");
      }
    }

    for (FakePlayer fp : toRemove) {
      unregisterBotState(fp, "DELETED");
    }

    for (int i = 0; i < toRemove.size(); i++) {
      FakePlayer fp = toRemove.get(i);

      final FakePlayer target = fp;
      final boolean explicitUuidSpawn = isExplicitUuidSpawn(target);
      Runnable doVisualRemove =
          () -> {
            if (!explicitUuidSpawn && Config.dropItemsOnDespawn()) {
              dropBotContents(target);
            }

            String despawnName = resolveDespawnDisplayName(target);
            boolean broadcastLeave = Config.leaveMessage() && !renamingBotIds.contains(target.getUuid());
            despawningBotIds.put(target.getUuid(), broadcastLeave ? despawnName : "");
            if (broadcastLeave) {
              var vc3 = plugin.getVelocityChannel();
              if (vc3 != null) vc3.broadcastLeaveToNetwork(despawnName);
            }
            if (!renamingBotIds.contains(target.getUuid())) {
              broadcastSyntheticQuit(
                  target,
                  despawnName,
                  broadcastLeave,
                  PlayerQuitEvent.QuitReason.DISCONNECTED);
            }
            try {
              if (explicitUuidSpawn) FakePlayerBody.removeAllWithoutSaving(target);
              else FakePlayerBody.removeAll(target);
            } finally {
              restoreExplicitUuidPlayerData(target);
              restoreExplicitUuidOperator(target);
              clearDespawningNextTick(target.getUuid());
            }
            if (chunkLoader != null) chunkLoader.releaseForBot(target);

            List<Player> snapshot = new ArrayList<>(Bukkit.getOnlinePlayers());
            for (Player online : snapshot) PacketHelper.sendTabListRemove(online, target);

            var vc2 = plugin.getVelocityChannel();
            if (vc2 != null) vc2.broadcastBotDespawn(target.getUuid());
            Config.debug("Removed bot: " + target.getName());
          };

      Player body = target.getPlayer();
      if (body != null) FppScheduler.runAtEntity(plugin, body, doVisualRemove);
      else FppScheduler.runSync(plugin, doVisualRemove);
    }

    final long saveDelay = 20L;
    FppScheduler.runSyncLater(
        plugin,
        () -> {
          if (persistence != null && Config.persistOnRestart()) {
            persistence.saveAsync(activePlayers.values());
          }
        },
        saveDelay);

    Config.debug("Staggered visual removal of " + toRemove.size() + " fake player(s).");
  }

  private static void dropBotContents(FakePlayer target) {
    Player bot = target.getPhysicsEntity();
    if (bot == null || !bot.isOnline()) return;

    Location loc = bot.getLocation();
    World world = loc.getWorld();
    if (world == null) return;

    for (ItemStack item : bot.getInventory().getContents()) {
      if (item != null && item.getType() != Material.AIR) {
        world.dropItemNaturally(loc, item);
      }
    }
    bot.getInventory().clear();

    int xp = bot.getTotalExperience();
    if (xp > 0) {
      world.spawn(loc, ExperienceOrb.class, orb -> orb.setExperience(xp));
      bot.setTotalExperience(0);
      bot.setLevel(0);
      bot.setExp(0f);
    }
  }

  private static ItemStack[] cloneContents(ItemStack[] contents) {
    if (contents == null) return new ItemStack[0];
    ItemStack[] clone = new ItemStack[contents.length];
    for (int i = 0; i < contents.length; i++) {
      if (contents[i] != null) clone[i] = contents[i].clone();
    }
    return clone;
  }

  private static void applyDespawnSnapshot(Player bot, DespawnSnapshot snap) {
    PlayerInventory inv = bot.getInventory();
    if (snap.mainContents().length > 0) inv.setContents(snap.mainContents());
    if (snap.armorContents().length > 0) inv.setArmorContents(snap.armorContents());
    if (snap.extraContents().length > 0) inv.setExtraContents(snap.extraContents());
    bot.setTotalExperience(0);
    bot.setLevel(0);
    bot.setExp(0f);
    bot.setLevel(snap.xpLevel());
    bot.setExp(Math.max(0f, Math.min(1f, snap.xpProgress())));
    bot.setTotalExperience(snap.xpTotal());
  }

  private void applyDespawnSnapshotSkin(FakePlayer fp) {
    DespawnSnapshot snap = despawnSnapshots.get(fp.getName().toLowerCase());
    if (snap == null || snap.skinTexture() == null || snap.skinTexture().isBlank()) return;

    fp.setResolvedSkin(new SkinProfile(snap.skinTexture(), snap.skinSignature(), "despawn:" + fp.getName()));
    Config.debugSkin("Restored despawned skin for bot '" + fp.getName() + "'");
  }

  private void persistActiveSkin(FakePlayer fp) {
    if (db == null) return;
    SkinProfile skin = fp.getResolvedSkin();
    if (skin == null || !skin.isValid()) return;
    db.updateBotSkin(fp.getUuid().toString(), skin.getValue(), skin.getSignature());
  }

  private static boolean hasResolvedSkin(FakePlayer fp) {
    SkinProfile skin = fp != null ? fp.getResolvedSkin() : null;
    return skin != null && skin.isValid();
  }

  // ── Despawn snapshot persistence ─────────────────────────────────────────

  /**
   * Called once from {@code FakePlayerPlugin.onEnable()} after the DB manager is wired in.
   * Loads any persisted despawn snapshots into the in-memory map so bots whose inventory was saved
   * before a restart can still be restored when they are spawned again.
   */
  public void initDespawnSnapshots() {
    despawnSnapshotFile =
        new File(plugin.getDataFolder(), "data" + File.separator + "despawn-snapshots.yml");

    // 1. Try DB first (primary store)
    if (db != null) {
      try {
        List<DatabaseManager.DespawnSnapshotRow> rows =
            db.loadDespawnSnapshotsForServer(Config.serverId());
        for (DatabaseManager.DespawnSnapshotRow row : rows) {
          DespawnSnapshot snap =
              deserializeSlots(
                  row.inventoryData(),
                  row.xpTotal(),
                  row.xpLevel(),
                  row.xpProgress(),
                  row.skinTexture(),
                  row.skinSignature());
          if (snap != null) despawnSnapshots.put(row.botName().toLowerCase(), snap);
        }
        if (!rows.isEmpty()) {
          Config.debugDatabase(
              "[DespawnSnapshot] Loaded " + rows.size() + " snapshot(s) from DB.");
        }
      } catch (Exception e) {
        FppLogger.warn("[DespawnSnapshot] Failed to load from DB: " + e.getMessage());
      }
      return; // DB is authoritative — skip YAML
    }

    // 2. YAML fallback
    if (!despawnSnapshotFile.exists()) return;
    try {
      YamlConfiguration yaml =
          YamlConfiguration.loadConfiguration(despawnSnapshotFile);
      ConfigurationSection sec = yaml.getConfigurationSection("snapshots");
      if (sec == null) return;
      for (String key : sec.getKeys(false)) {
        ConfigurationSection entry = sec.getConfigurationSection(key);
        if (entry == null) continue;
        String invData = entry.getString("inventory-data", "");
        int xpTotal = entry.getInt("xp-total", 0);
        int xpLevel = entry.getInt("xp-level", 0);
        float xpProgress = (float) entry.getDouble("xp-progress", 0.0);
        String skinTexture = entry.getString("skin-texture", null);
        String skinSignature = entry.getString("skin-signature", null);
        DespawnSnapshot snap =
            deserializeSlots(invData, xpTotal, xpLevel, xpProgress, skinTexture, skinSignature);
        if (snap != null) despawnSnapshots.put(key.toLowerCase(), snap);
      }
      if (!sec.getKeys(false).isEmpty()) {
        Config.debugDatabase(
            "[DespawnSnapshot] Loaded " + sec.getKeys(false).size() + " snapshot(s) from YAML.");
      }
    } catch (Exception e) {
      FppLogger.warn("[DespawnSnapshot] Failed to load YAML: " + e.getMessage());
    }
  }

  /**
   * Persists a snapshot to the DB (primary) or YAML fallback (async, best-effort).
   */
  private void persistDespawnSnapshot(String botNameLower, DespawnSnapshot snap) {
    String invData = serializeSlots(snap);
    String serverId = Config.serverId();

    if (db != null) {
      db.saveDespawnSnapshot(
          botNameLower,
          serverId,
          invData,
          snap.xpTotal(),
          snap.xpLevel(),
          snap.xpProgress(),
          snap.skinTexture(),
          snap.skinSignature());
      return;
    }

    // YAML fallback — write async
    final File yamlFile = despawnSnapshotFile;
    if (yamlFile == null) return;
    final String invDataFinal = invData;
    final int xpT = snap.xpTotal(), xpL = snap.xpLevel();
    final float xpP = snap.xpProgress();
    final String skinTex = snap.skinTexture(), skinSig = snap.skinSignature();
    FppScheduler.runAsync(
        plugin,
        () -> {
          try {
            YamlConfiguration yaml =
                yamlFile.exists()
                    ? YamlConfiguration.loadConfiguration(yamlFile)
                    : new YamlConfiguration();
            String path = "snapshots." + botNameLower;
            yaml.set(path + ".inventory-data", invDataFinal);
            yaml.set(path + ".xp-total", xpT);
            yaml.set(path + ".xp-level", xpL);
            yaml.set(path + ".xp-progress", (double) xpP);
            yaml.set(path + ".skin-texture", skinTex);
            yaml.set(path + ".skin-signature", skinSig);
            yaml.set(path + ".saved-at", System.currentTimeMillis());
            yaml.save(yamlFile);
          } catch (Exception e) {
            FppLogger.warn("[DespawnSnapshot] YAML save failed: " + e.getMessage());
          }
        });
  }

  /**
   * Removes a snapshot from DB/YAML after it has been restored (called on respawn).
   */
  private void removeDespawnSnapshotPersistent(String botNameLower) {
    String serverId = Config.serverId();
    if (db != null) {
      db.deleteDespawnSnapshot(botNameLower, serverId);
      return;
    }

    final File yamlFile = despawnSnapshotFile;
    if (yamlFile == null || !yamlFile.exists()) return;
    FppScheduler.runAsync(
        plugin,
        () -> {
          try {
            YamlConfiguration yaml =
                YamlConfiguration.loadConfiguration(yamlFile);
            yaml.set("snapshots." + botNameLower, null);
            yaml.save(yamlFile);
          } catch (Exception e) {
            FppLogger.warn("[DespawnSnapshot] YAML delete failed: " + e.getMessage());
          }
        });
  }

  /**
   * Serialises a {@link DespawnSnapshot} to a compact pipe-delimited string.
   * Format: {@code slot:base64|slot:base64|…} — uses only chars safe from splitting.
   * Uses {@code mainContents} (all 41 slots) which already contains armor and offhand.
   */
  private static String serializeSlots(DespawnSnapshot snap) {
    StringBuilder sb = new StringBuilder();
    ItemStack[] all = snap.mainContents();
    for (int i = 0; i < all.length; i++) {
      if (all[i] != null && all[i].getType() != Material.AIR) {
        try {
          String b64 = Base64.getEncoder().encodeToString(all[i].serializeAsBytes());
          if (sb.length() > 0) sb.append('|');
          sb.append(i).append(':').append(b64);
        } catch (Exception ignored) {
        }
      }
    }
    return sb.toString();
  }

  /**
   * Deserialises the pipe-delimited slot string back into a {@link DespawnSnapshot}.
   * Returns {@code null} if the data is blank or entirely corrupt.
   */
  private static DespawnSnapshot deserializeSlots(
      String data,
      int xpTotal,
      int xpLevel,
      float xpProgress,
      String skinTexture,
      String skinSignature) {
    ItemStack[] main = new ItemStack[41];
    if (data != null && !data.isBlank()) {
      for (String token : data.split("\\|")) {
        int colon = token.indexOf(':');
        if (colon < 1) continue;
        try {
          int slot = Integer.parseInt(token.substring(0, colon));
          if (slot < 0 || slot >= main.length) continue;
          byte[] bytes = Base64.getDecoder().decode(token.substring(colon + 1));
          main[slot] = ItemStack.deserializeBytes(bytes);
        } catch (Exception ignored) {
        }
      }
    }
    // Build armor (slots 36-39) and extra (slot 40) sub-arrays for applyDespawnSnapshot
    ItemStack[] armor = new ItemStack[]{main[36], main[37], main[38], main[39]};
    ItemStack[] extra = new ItemStack[]{main[40]};
    boolean hasContent =
        xpTotal > 0 || xpLevel > 0 || xpProgress > 0f || (skinTexture != null && !skinTexture.isBlank());
    for (ItemStack item : main) {
      if (item != null && item.getType() != Material.AIR) {
        hasContent = true;
        break;
      }
    }
    if (!hasContent) return null;
    return new DespawnSnapshot(main, armor, extra, xpTotal, xpLevel, xpProgress, skinTexture, skinSignature);
  }

  public boolean delete(String name) {
    return deleteInternal(name, false, false, null);
  }

  public boolean deleteForLoginHandoff(
      String name, @Nullable Runnable onComplete) {
    return deleteInternal(name, true, true, onComplete);
  }

  private boolean deleteInternal(
      String name,
      boolean fastVisualRemove,
      boolean suppressLeaveBroadcast,
      @Nullable Runnable onComplete) {
    FakePlayer fp = getByName(name);
    if (fp == null) return false;

    // Fire API despawn event before any state is removed.
    var fppApi = plugin.getFppApi();
    if (fppApi != null) {
      FppBotDespawnEvent despawnEvt =
          new FppBotDespawnEvent(
              new FppBotImpl(fp));
      Bukkit.getPluginManager().callEvent(despawnEvt);
    }

    final FakePlayer target = fp;
    final String botName = target.getName();
    final boolean explicitUuidSpawn = isExplicitUuidSpawn(target);

    // Snapshot inventory + XP BEFORE removing from maps or despawning entity.
    // Must happen synchronously here (not in the delayed task) so bulk operations
    // like /fpp despawn all don't race — by the time the 1-tick delay fires,
    // earlier bots' entities may already be gone and snapBody.isOnline() fails.
    boolean preserveInventoryOnDespawn = !Config.dropItemsOnDespawn();
    if (!explicitUuidSpawn
        && (preserveInventoryOnDespawn || hasResolvedSkin(target))
        && !renamingBotIds.contains(target.getUuid())
        && !suppressDespawnSnapshotIds.contains(target.getUuid())) {
      Player snapBody = target.getPhysicsEntity();
      if (snapBody != null && snapBody.isOnline()) {
        DespawnSnapshot snap =
            new DespawnSnapshot(
                preserveInventoryOnDespawn
                    ? cloneContents(snapBody.getInventory().getContents())
                    : new ItemStack[41],
                preserveInventoryOnDespawn
                    ? cloneContents(snapBody.getInventory().getArmorContents())
                    : new ItemStack[4],
                preserveInventoryOnDespawn
                    ? cloneContents(snapBody.getInventory().getExtraContents())
                    : new ItemStack[1],
                preserveInventoryOnDespawn ? snapBody.getTotalExperience() : 0,
                preserveInventoryOnDespawn ? snapBody.getLevel() : 0,
                preserveInventoryOnDespawn ? snapBody.getExp() : 0f,
                target.getResolvedSkin() != null ? target.getResolvedSkin().getValue() : null,
                target.getResolvedSkin() != null ? target.getResolvedSkin().getSignature() : null);
        despawnSnapshots.put(botName.toLowerCase(), snap);
        persistDespawnSnapshot(botName.toLowerCase(), snap);
        Config.debugSwap("[DespawnSnapshot] Saved despawn state for '" + botName + "'.");
      }
    }

    unregisterBotState(target, "DELETED");
    if (!explicitUuidSpawn) target.clearMetadata();

    Runnable doVisualRemove =
        () -> {
          try {
            if (!explicitUuidSpawn && Config.dropItemsOnDespawn()) {
              dropBotContents(target);
            }

            String despawnName = resolveDespawnDisplayName(target);
            boolean broadcastLeave =
                Config.leaveMessage()
                    && !renamingBotIds.contains(target.getUuid())
                    && !suppressLeaveBroadcast;
            despawningBotIds.put(target.getUuid(), broadcastLeave ? despawnName : "");

            if (broadcastLeave) {
              var vc3 = plugin.getVelocityChannel();
              if (vc3 != null) vc3.broadcastLeaveToNetwork(despawnName);
            }
            if (!renamingBotIds.contains(target.getUuid()) && !suppressLeaveBroadcast) {
              broadcastSyntheticQuit(
                  target,
                  despawnName,
                  broadcastLeave,
                  PlayerQuitEvent.QuitReason.DISCONNECTED);
            }

            try {
              if (explicitUuidSpawn || fastVisualRemove) FakePlayerBody.removeAllFast(target);
              else FakePlayerBody.removeAll(target);
            } finally {
              restoreExplicitUuidPlayerData(target);
              restoreExplicitUuidOperator(target);
              clearDespawningNextTick(target.getUuid());
            }
            if (chunkLoader != null) chunkLoader.releaseForBot(target);

            List<Player> snapshot = new ArrayList<>(Bukkit.getOnlinePlayers());
            for (Player online : snapshot) PacketHelper.sendTabListRemove(online, target);

            if (db != null) db.recordRemoval(target.getUuid(), "DELETED");

            var vc2 = plugin.getVelocityChannel();
            if (vc2 != null) vc2.broadcastBotDespawn(target.getUuid());
            Config.debug("Deleted fake player: " + botName);
            if (persistence != null && Config.persistOnRestart()) {
              persistence.saveAsync(activePlayers.values());
            }
          } finally {
            if (onComplete != null) {
              try {
                onComplete.run();
              } catch (Throwable ignored) {
              }
            }
          }
        };

    Player body = target.getPlayer();
    if (body != null) FppScheduler.runAtEntity(plugin, body, doVisualRemove);
    else FppScheduler.runSync(plugin, doVisualRemove);

    return true;
  }

  public void removeAllSync() {
    removeAllSync(false);
  }

  public void removeAllSyncFast() {
    removeAllSync(true);
  }

  private void removeAllSync(boolean fastShutdown) {
    if (activePlayers.isEmpty()) return;

    List<FakePlayer> toRemove = new ArrayList<>(activePlayers.values());
    activePlayers.clear();
    nameIndex.clear();
    usedNames.clear();
    entityIdIndex.clear();

    botHeadRotation.clear();
    botSpawnRotation.clear();

    List<Player> snapshot = new ArrayList<>(Bukkit.getOnlinePlayers());

    for (FakePlayer fp : toRemove) {
      boolean explicitUuidSpawn = isExplicitUuidSpawn(fp);
      String despawnName = resolveDespawnDisplayName(fp);
      boolean broadcastLeave = Config.leaveMessage();
      despawningBotIds.put(fp.getUuid(), broadcastLeave ? despawnName : "");
      broadcastSyntheticQuit(
          fp,
          despawnName,
          broadcastLeave,
          PlayerQuitEvent.QuitReason.DISCONNECTED);

      try {
        if (explicitUuidSpawn || fastShutdown) FakePlayerBody.removeAllFast(fp);
        else FakePlayerBody.removeAll(fp);
      } finally {
        restoreExplicitUuidPlayerData(fp);
        restoreExplicitUuidOperator(fp);
      }
      if (chunkLoader != null) chunkLoader.releaseForBot(fp);

      for (Player online : snapshot) PacketHelper.sendTabListRemove(online, fp);

      despawningBotIds.remove(fp.getUuid());
      syntheticQuitBotIds.remove(fp.getUuid());
      Config.debug("Shutdown removed bot: " + fp.getName());
    }

    FppLogger.info("Shutdown: removed " + toRemove.size() + " bot(s)." + (fastShutdown ? " (fast)" : ""));
  }

  private void tickAutoEat(Player bot) {
    if (bot.getGameMode() == GameMode.CREATIVE || bot.getGameMode() == GameMode.SPECTATOR) return;
    if (bot.getFoodLevel() > 14 && (bot.getFoodLevel() >= 6 || bot.isSprinting())) return;
    var inv = bot.getInventory();
    for (int slot = 0; slot < inv.getSize(); slot++) {
      ItemStack item = inv.getItem(slot);
      if (item == null || item.getAmount() <= 0) continue;
      int food = foodValue(item.getType());
      if (food <= 0) continue;
      item.setAmount(item.getAmount() - 1);
      if (item.getAmount() <= 0) inv.setItem(slot, null);
      else inv.setItem(slot, item);
      bot.setFoodLevel(Math.min(20, bot.getFoodLevel() + food));
      bot.setSaturation(Math.min(20f, bot.getSaturation() + Math.max(1f, food * 0.6f)));
      bot.swingMainHand();
      return;
    }
  }

  private int foodValue(Material type) {
    return switch (type) {
      case COOKED_BEEF, COOKED_PORKCHOP -> 8;
      case GOLDEN_CARROT, BAKED_POTATO, COOKED_CHICKEN, MUSHROOM_STEW, BEETROOT_SOUP, RABBIT_STEW -> 6;
      case BREAD, COOKED_COD, COOKED_RABBIT -> 5;
      case APPLE, CARROT, COOKED_SALMON, CHORUS_FRUIT -> 4;
      case POTATO, BEETROOT, MELON_SLICE, SWEET_BERRIES, GLOW_BERRIES -> 2;
      case COOKIE, DRIED_KELP -> 1;
      default -> 0;
    };
  }

  public void applyBodyConfig() {
    List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());

    if (physicalBodiesEnabled()) {

      for (FakePlayer fp : new ArrayList<>(activePlayers.values())) {
        Player body = fp.getPlayer();

        if (body != null && body.isValid()) {

          body.setInvulnerable(false);
          body.setCollidable(Config.bodyPushable());
          try {
            var attr = body.getAttribute(AttributeCompat.MAX_HEALTH);
            if (attr != null) {
              double hp = Config.maxHealth();
              attr.setBaseValue(hp);
              if (body.getHealth() > hp) body.setHealth(hp);
            }
          } catch (Exception ignored) {
          }

        } else if (fp.isBodyless()) {

          Location loc = fp.getSpawnLocation();
          if (loc == null || loc.getWorld() == null) continue;

          bodyTransitionBots.add(fp.getUuid());
          fp.setBodyless(false);
          try {
            FakePlayerBody.resolveAndFinish(
                plugin,
                fp,
                loc,
                () -> {
                  Player newBody = FakePlayerBody.spawn(fp, loc, fp.getEffectivePing());
                  if (newBody != null) {
                    fp.setPhysicsEntity(newBody);
                    entityIdIndex.put(newBody.getEntityId(), fp);
                    fp.setPacketProfileName(fp.getName());

                    if (Config.tabListEnabled()) {
                      for (Player p : online) {
                        PacketHelper.sendTabListRefreshEntry(p, fp);
                      }
                    }

                    PlayerJoinListener.stampFirstPlayed(newBody);
                    FppLogger.info("BodyConfig: body shown for '" + fp.getName() + "'");
                  } else {
                    fp.setBodyless(true);
                    FppLogger.warn("BodyConfig: failed to show body for '" + fp.getName() + "'");
                  }
                });
          } finally {
            bodyTransitionBots.remove(fp.getUuid());
          }
        }
      }

      return;
    }

    for (FakePlayer fp : new ArrayList<>(activePlayers.values())) {
      Player body = fp.getPlayer();
      if (body == null || !body.isOnline()) continue;

      fp.setSpawnLocation(body.getLocation());
      int entityId = body.getEntityId();

      entityIdIndex.remove(entityId);
      fp.setPhysicsEntity(null);
      fp.setBodyless(true);

      bodyTransitionBots.add(fp.getUuid());
      boolean explicitUuidSpawn = isExplicitUuidSpawn(fp);
      FppScheduler.runAtEntity(
          plugin,
          body,
          () -> {
            try {
              if (explicitUuidSpawn) NmsPlayerSpawner.removeFakePlayerFast(body);
              else NmsPlayerSpawner.removeFakePlayer(body);
            } finally {
              restoreExplicitUuidPlayerData(fp);
              restoreExplicitUuidOperator(fp);
              bodyTransitionBots.remove(fp.getUuid());
            }

            if (Config.tabListEnabled()) {
              for (Player p : online) PacketHelper.sendTabListRefreshEntry(p, fp);
            }
            FppLogger.info("BodyConfig: body hidden for '" + fp.getName() + "', now tab-list only");
          });
    }
  }

  public void removeByName(String name) {
    FakePlayer fp = getByName(name);
    if (fp != null) {
      unregisterBotState(fp, "DIED");
      restoreExplicitUuidSourceState(fp);
      fp.clearMetadata();
      Config.debug("Removed from registry: " + name);
    }
    if (persistence != null && Config.persistOnRestart()) {
      persistence.saveAsync(activePlayers.values());
    }
  }

  private void unregisterBotState(FakePlayer fp, String removalReason) {
    if (fp == null) return;

    UUID uuid = fp.getUuid();
    String name = fp.getName();

    activePlayers.remove(uuid, fp);
    nameIndex.remove(name.toLowerCase(), fp);
    usedNames.remove(name);
    suppressDespawnSnapshotIds.remove(uuid);

    Player body = fp.getPhysicsEntity();
    if (body != null) entityIdIndex.remove(body.getEntityId(), fp);

    if (botSwapAI != null) botSwapAI.cancel(uuid);

    botHeadRotation.remove(uuid);
    botSpawnRotation.remove(uuid);
    actionLockedBots.remove(uuid);
    navLockedBots.remove(uuid);
    trackedFallDistance.remove(uuid);
    lastFallY.remove(uuid);
    wasOnGround.remove(uuid);
    bodyTransitionBots.remove(uuid);

    var pathfinding = plugin.getPathfindingService();
    if (pathfinding != null) pathfinding.cancel(uuid);

    var moveCmd = plugin.getMoveCommand();
    if (moveCmd != null) moveCmd.cleanupBot(uuid);
    var mineCmd = plugin.getMineCommand();
    if (mineCmd != null) {
      mineCmd.cleanupBot(uuid);
      mineCmd.clearSelection(uuid);
    }
    var placeCmd = plugin.getPlaceCommand();
    if (placeCmd != null) placeCmd.cleanupBot(uuid);
    var useCmd = plugin.getUseCommand();
    if (useCmd != null) useCmd.stopUsing(uuid);
    var followCmd = plugin.getFollowCommand();
    if (followCmd != null) followCmd.cleanupBot(uuid);
    var sleepCmd = plugin.getSleepCommand();
    if (sleepCmd != null) sleepCmd.cleanupBot(uuid);

    if (chunkLoader != null) chunkLoader.releaseForBot(fp);
    if (db != null && removalReason != null) db.recordRemoval(uuid, removalReason);
  }

  public void syncToPlayer(Player player) {
    if (!Config.tabListEnabled()) {

      for (FakePlayer fp : activePlayers.values()) {
        PacketHelper.sendTabListUpdateListed(player, fp, false);
      }
      return;
    }
    for (FakePlayer fp : activePlayers.values()) {

      PacketHelper.sendTabListAdd(player, fp);
      PacketHelper.sendTabListLatencyUpdate(player, fp);
    }
  }

  public void applyTabListConfig() {
    List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
    if (online.isEmpty() || activePlayers.isEmpty()) return;

    boolean listed = Config.tabListEnabled();

    for (FakePlayer fp : activePlayers.values()) {
      Player bot = fp.getPlayer();
      if (bot != null) {
        boolean nmsOk = NmsPlayerSpawner.setListed(bot, listed);
        if (!nmsOk) {
          for (Player p : online) {
            PacketHelper.sendTabListUpdateListed(p, fp, listed);
          }
        }
      }
    }

    if (listed) {
      for (FakePlayer fp : activePlayers.values()) {
        for (Player p : online) {
          PacketHelper.sendTabListRefreshEntry(p, fp);
        }
      }
      Config.debug("applyTabListConfig: refreshed " + activePlayers.size() + " bots in tab list.");
    } else {
      for (FakePlayer fp : activePlayers.values()) {
        for (Player p : online) {
          PacketHelper.sendTabListUpdateListed(p, fp, false);
        }
      }

      Config.debug("applyTabListConfig: unlisted " + activePlayers.size() + " bots from tab list.");
    }
  }

  public void validateEntities() {

    Set<String> activeNames =
        activePlayers.values().stream()
            .map(FakePlayer::getName)
            .collect(Collectors.toSet());

    for (FakePlayer fp : activePlayers.values()) {
      Entity body = fp.getPhysicsEntity();

      if (!physicalBodiesEnabled()) {
        if (body != null && body.isValid()) {
          try {
            body.remove();
          } catch (Exception ignored) {
          }
          entityIdIndex.remove(body.getEntityId());
          fp.setPhysicsEntity(null);
        }
        continue;
      }

      if (fp.isBodyless()) continue;

      if (body != null && body.isValid()) continue;

      Config.debug(
          "validateEntities: body of '" + fp.getName() + "' invalid - attempting respawn.");

      fp.setPhysicsEntity(null);

      Location loc = fp.getSpawnLocation();
      if (loc == null || loc.getWorld() == null) continue;

      FakePlayerBody.resolveAndFinish(
          plugin,
          fp,
          loc,
          () -> {
            Player newBody = FakePlayerBody.spawn(fp, loc, fp.getEffectivePing());
            if (newBody == null) return;

            fp.setPhysicsEntity(newBody);
            entityIdIndex.put(newBody.getEntityId(), fp);

            final FakePlayer target = fp;
            FppScheduler.runSyncLater(
                plugin,
                () -> {
                  for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListRefreshEntry(p, target);
                },
                2L);
          });
    }
  }

  public FakePlayer getByEntity(Entity entity) {

    FakePlayer fp = entityIdIndex.get(entity.getEntityId());
    if (fp != null) return fp;

    String botName = null;
    if (FAKE_PLAYER_KEY != null) {
      String marker =
          entity
              .getPersistentDataContainer()
              .get(FAKE_PLAYER_KEY, PersistentDataType.STRING);
      if (marker != null && !marker.isBlank()) {
        String prefix = FakePlayerBody.VISUAL_PDC_VALUE + ":";
        botName = marker.startsWith(prefix) ? marker.substring(prefix.length()) : marker;
      }
    }

    if ((botName == null || botName.isBlank()) && entity instanceof Player player) {
      FakePlayer byUuid = getByUuid(player.getUniqueId());
      if (byUuid != null && byUuid.getName().equalsIgnoreCase(player.getName())) {
        stampFakePlayerMarker(player, byUuid);
        byUuid.setPhysicsEntity(player);
        entityIdIndex.put(entity.getEntityId(), byUuid);
        return byUuid;
      }
    }

    if (botName == null || botName.isBlank()) return null;

    FakePlayer candidate = getByName(botName);
    if (candidate == null) return null;

    Entity oldBody = candidate.getPhysicsEntity();
    if (oldBody != null && oldBody.getEntityId() != entity.getEntityId()) {
      entityIdIndex.remove(oldBody.getEntityId());
    }

    if (entity instanceof Player player) {
      candidate.setPhysicsEntity(player);
      entityIdIndex.put(entity.getEntityId(), candidate);
      stampFakePlayerMarker(player, candidate);
      Config.debug(
          "getByEntity: recovered '"
              + botName
              + "' via PDC after world-change - new entityId="
              + entity.getEntityId());
      return candidate;
    }

    Config.debug("getByEntity: entity is not a Player, cannot recover bot: " + botName);
    return null;
  }

  public void stampFakePlayerMarker(Player player, FakePlayer fp) {
    if (player == null || fp == null || FAKE_PLAYER_KEY == null) return;
    try {
      fp.setPhysicsEntity(player);
      entityIdIndex.put(player.getEntityId(), fp);
      player
          .getPersistentDataContainer()
          .set(
              FAKE_PLAYER_KEY,
              PersistentDataType.STRING,
              FakePlayerBody.VISUAL_PDC_VALUE + ":" + fp.getName());
    } catch (Throwable ignored) {
    }
  }

  public FakePlayer getByUuid(UUID uuid) {
    if (uuid == null) return null;
    return activePlayers.get(uuid);
  }

  public void removeFromEntityIndex(int entityId) {
    entityIdIndex.remove(entityId);
  }

  public void interruptNavigationOwner(UUID botUuid, PathfindingService.Owner owner) {
    if (botUuid == null || owner == null) return;
    switch (owner) {
      case MOVE -> {
        var cmd = plugin.getMoveCommand();
        if (cmd != null) cmd.cleanupBot(botUuid);
      }
      case MINE -> {
        var cmd = plugin.getMineCommand();
        if (cmd != null) cmd.stopMining(botUuid);
      }
      case PLACE -> {
        var cmd = plugin.getPlaceCommand();
        if (cmd != null) cmd.stopPlacing(botUuid);
      }
      case USE -> {
        var cmd = plugin.getUseCommand();
        if (cmd != null) cmd.stopUsing(botUuid);
      }
      case ATTACK -> {
        var cmd = plugin.getAttackCommand();
        if (cmd != null) cmd.stopAttacking(botUuid);
      }
      case FOLLOW -> {
        var cmd = plugin.getFollowCommand();
        if (cmd != null) cmd.stopFollowing(botUuid);
      }
      case SLEEP -> {
        var cmd = plugin.getSleepCommand();
        if (cmd != null) cmd.cleanupBot(botUuid);
      }
      case SYSTEM -> {
        var pathfinding = plugin.getPathfindingService();
        if (pathfinding != null) pathfinding.cancel(botUuid);
      }
    }
  }

  public void registerEntityIndex(int entityId, FakePlayer fp) {
    entityIdIndex.put(entityId, fp);
  }

  public FakePlayer getByEntityId(int entityId) {
    return entityIdIndex.get(entityId);
  }

  public List<String> getActiveNames() {
    return activePlayers.values().stream().map(FakePlayer::getName).collect(Collectors.toList());
  }

  public boolean isNameUsed(String name) {
    return usedNames.contains(name);
  }

  public Set<UUID> getActiveUUIDs() {
    return Collections.unmodifiableSet(new HashSet<>(activePlayers.keySet()));
  }

  public FakePlayer getByName(String name) {
    if (name == null || name.isBlank()) return null;
    return nameIndex.get(name.toLowerCase());
  }

  public void renameBot(FakePlayer bot, String newName) {
    String oldDisplay = bot.getDisplayName();
    if (oldDisplay.equalsIgnoreCase(newName)) return;

    bot.setDisplayName(newName);
    bot.setRawDisplayName(newName);

    if (Config.tabListEnabled() && bot.getPlayer() != null) {
      for (Player p : Bukkit.getOnlinePlayers()) {
        PacketHelper.sendTabListDisplayNameUpdate(p, bot);
      }
    }
  }

  public boolean isOnCooldown(UUID playerUuid) {
    int secs = Config.spawnCooldown();
    if (secs <= 0) return false;
    Long last = spawnCooldowns.get(playerUuid);
    if (last == null) return false;
    return (System.currentTimeMillis() - last) / 1000L < secs;
  }

  public void requestNavJump(UUID botUuid) {
    navJumpHolding.put(botUuid, 5);
  }

  public void clearNavJump(UUID botUuid) {
    navJumpHolding.remove(botUuid);
  }

  public Integer getNavJumpHolding(UUID botUuid) {
    return navJumpHolding.get(botUuid);
  }

  public void setNavJumpHolding(UUID botUuid, int value) {
    navJumpHolding.put(botUuid, value);
  }

  private final Set<UUID> actingBots = ConcurrentHashMap.newKeySet();

  public void lockForAction(UUID botUuid, Location loc) {
    lockForAction(botUuid, loc, false);
  }

  public void lockForAction(UUID botUuid, Location loc, boolean lockPosition) {
    if (lockPosition) {
      actionLockedBots.put(botUuid, loc.clone());
    }
    actingBots.add(botUuid);
    botHeadRotation.put(botUuid, new float[]{loc.getYaw(), loc.getPitch()});
    botSpawnRotation.put(botUuid, new float[]{loc.getYaw(), loc.getPitch()});
  }

  public void unlockAction(UUID botUuid) {
    actionLockedBots.remove(botUuid);
    actingBots.remove(botUuid);
  }

  public boolean isActionLocked(UUID botUuid) {
    return actingBots.contains(botUuid);
  }

  public void updateActionLockRotation(UUID botUuid, float yaw, float pitch) {
    Location loc = actionLockedBots.get(botUuid);
    if (loc != null) {
      loc.setYaw(yaw);
      loc.setPitch(pitch);
    }
  }

  public void lockForNavigation(UUID botUuid) {
    navLockedBots.add(botUuid);
  }

  public void unlockNavigation(UUID botUuid) {
    navLockedBots.remove(botUuid);
  }

  public boolean isNavigationLocked(UUID botUuid) {
    return navLockedBots.contains(botUuid);
  }

  public long getRemainingCooldown(UUID playerUuid) {
    int secs = Config.spawnCooldown();
    if (secs <= 0) return 0;
    Long last = spawnCooldowns.get(playerUuid);
    if (last == null) return 0;
    long elapsed = (System.currentTimeMillis() - last) / 1000L;
    return Math.max(0, secs - elapsed);
  }

  public void recordSpawnCooldown(UUID playerUuid) {
    spawnCooldowns.put(playerUuid, System.currentTimeMillis());
  }

  public void clearCooldown(UUID playerUuid) {
    spawnCooldowns.remove(playerUuid);
  }

  public Collection<FakePlayer> getActivePlayers() {
    return Collections.unmodifiableCollection(activePlayers.values());
  }

  public int getCount() {
    return activePlayers.size();
  }

  public List<FakePlayer> getBotsOwnedBy(UUID ownerUuid) {
    return activePlayers.values().stream()
        .filter(fp -> ownerUuid.equals(fp.getSpawnedByUuid()))
        .collect(Collectors.toList());
  }

  public boolean teleportBot(FakePlayer fp, Location destination) {
    Player body = fp.getPlayer();
    if (body == null || !body.isValid()) return false;
    var event = new FppBotTeleportEvent(
        new FppBotImpl(fp), body.getLocation(), destination);
    Bukkit.getPluginManager().callEvent(event);
    if (event.isCancelled()) return false;
    FppScheduler.teleportAsync(body, event.getTo());
    fp.setSpawnLocation(event.getTo().clone());
    return true;
  }

  private String finalizeDisplayName(String rawName, String botName) {
    String display = rawName;

    if (display.contains("%")) {
      try {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
          display = PlaceholderAPI.setPlaceholders(null, display);
        }
      } catch (Exception ignored) {
      }
    }

    return sanitizeDisplayName(display, botName);
  }

  public void refreshDisplayName(FakePlayer fp) {
    if (!activePlayers.containsKey(fp.getUuid())) return;

    String rawContent = fp.getRawDisplayName();
    if (rawContent == null || rawContent.isBlank()) rawContent = fp.getName();

    String display = rawContent;

    if (display.contains("%")) {
      try {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
          display = PlaceholderAPI.setPlaceholders(null, display);
        }
      } catch (Exception ignored) {
      }
    }

    display = sanitizeDisplayName(display, fp.getName());

    if (display == null || display.isBlank()) {
      display = fp.getName();
      Config.debug(
          "Display name was blank after sanitise for '"
              + fp.getName()
              + "' — falling back to raw bot name.");
    }
    fp.setDisplayName(display);

    fp.clearTabListDirty();

    Player body = fp.getPlayer();
    if (body != null && body.isValid()) {
      try {
        body.displayName(TextUtil.colorize(rawContent));
      } catch (Exception ignored) {
      }
    }

    if (Config.tabListEnabled()) {
      List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
      for (Player p : online) PacketHelper.sendTabListDisplayNameUpdate(p, fp);
    }

    if (Config.isNetworkMode()) {
      var vc = plugin.getVelocityChannel();
      if (vc != null) vc.broadcastBotDisplayNameUpdate(fp);
    }

    Config.debug("Refreshed display name for '" + fp.getName() + "': '" + display + "'");
  }

  public void refreshSkinForAll(FakePlayer fp) {
    if (!activePlayers.containsKey(fp.getUuid())) return;
    Player body = fp.getPlayer();
    if (body == null || !body.isValid()) return;
    boolean tabVisible = Config.tabListEnabled();

    fp.clearCachedTabListGameProfile();
    NmsPlayerSpawner.applySkinToGameProfile(body, fp.getResolvedSkin());
    NmsPlayerSpawner.refreshPaperPlayer(body);

    for (Player viewer : new ArrayList<>(Bukkit.getOnlinePlayers())) {
      if (viewer.getUniqueId().equals(body.getUniqueId())) continue;
      if (activePlayers.containsKey(viewer.getUniqueId())) continue;
      if (!viewer.canSee(body)) continue;

      viewer.hidePlayer(plugin, body);
      viewer.showPlayer(plugin, body);
      if (!tabVisible) PacketHelper.sendTabListUpdateListed(viewer, fp, false);
    }
  }

  private static final Pattern PLACEHOLDER_PATTERN =
      Pattern.compile("\\{[a-zA-Z_][a-zA-Z0-9_]*\\}");

  private String sanitizeDisplayName(String displayName, String context) {
    if (displayName == null || !displayName.contains("{")) {

      return (displayName == null || displayName.isBlank()) ? context : displayName;
    }
    Matcher m = PLACEHOLDER_PATTERN.matcher(displayName);
    if (!m.find()) {
      return displayName.isBlank() ? context : displayName;
    }
    String fallback = pickRandomSkinName();
    String sanitized = PLACEHOLDER_PATTERN.matcher(displayName).replaceAll(fallback);
    FppLogger.warn(
        "Unreplaced placeholder(s) in display name for '"
            + context
            + "': '"
            + displayName
            + "' - replaced with '"
            + fallback
            + "'. Check bot-name.user-format / bot-name.admin-format in config.yml.");

    return sanitized.isBlank() ? context : sanitized;
  }

  public void applyPing(FakePlayer fp, int pingMs) {
    if (fp == null) return;
    if (pingMs >= 0) {
      fp.setUserPing(pingMs);
      fp.setBasePing(-1);
    } else {
      fp.setUserPing(-1);
      if (Config.pingEnabled()) {
        int min = Config.pingMin();
        int max = Config.pingMax();
        int base = min + ThreadLocalRandom.current().nextInt(Math.max(1, max - min + 1));
        fp.setBasePing(base);
        fp.setPing(base);
      } else {
        fp.setPing(-1);
      }
    }
    NmsPlayerSpawner.setPing(
        fp.getPlayer(), fp.getEffectivePing());
    for (Player p : Bukkit.getOnlinePlayers()) {
      PacketHelper.sendTabListLatencyUpdate(p, fp);
    }
  }

  private boolean shouldSendLaggedVisualUpdate(FakePlayer fp) {
    if (!Config.pingLatencyEffect()) return true;
    int cadence = visualSyncCadenceTicks(fp);
    if (cadence <= 1) return true;
    long tick = visualSyncTickCounter;
    int offset = Math.floorMod(fp.getUuid().hashCode(), cadence);
    return Math.floorMod(tick, cadence) == offset;
  }

  private boolean shouldRunLaggedBehaviorUpdate(FakePlayer fp) {
    if (!Config.pingLatencyEffect() || !Config.pingBehaviorEffect()) return true;
    int cadence = behaviorUpdateCadenceTicks(fp);
    if (cadence <= 1) return true;
    long tick = visualSyncTickCounter;
    int offset = Math.floorMod(fp.getUuid().hashCode(), cadence);
    return Math.floorMod(tick, cadence) == offset;
  }

  private int visualSyncCadenceTicks(FakePlayer fp) {
    int eff = fp.getEffectivePing();
    if (eff <= 0) return 1;
    if (eff <= 100) return 1;
    return Math.min(10, (eff + 99) / 100);
  }

  private int behaviorUpdateCadenceTicks(FakePlayer fp) {
    int eff = fp.getEffectivePing();
    if (eff <= 0) return 1;
    if (eff <= 150) return 1;
    return Math.min(Config.pingMaxBehaviorSkipTicks(), Math.max(2, (eff + 149) / 150));
  }

  private String resolveDespawnDisplayName(FakePlayer fp) {
    return BotBroadcast.resolveDisplayName(fp);
  }

  private String pickRandomSkinName() {
    String skinMode = Config.skinMode();
    if (skinMode != null) {
      String normalized = skinMode.trim().toLowerCase();
      if ("player".equals(normalized) || "auto".equals(normalized)) {

        return SkinManager.pickRandomPoolName();
      }
    }

    List<String> pool = Config.namePool();
    if (pool.isEmpty()) return fallbackName();
    return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
  }

  private UUID resolveUuid(String botName) {
    if (identityCache != null) return identityCache.lookupOrCreate(botName);
    return BotIdentityCache.deterministicBotUuid(botName);
  }

  public UUID refreshIdentity(String botName) {
    if (identityCache != null) return identityCache.refresh(botName);
    return BotIdentityCache.deterministicBotUuid(botName);
  }

  private String generateName() {
    return generateName(false);
  }

  private String generateName(boolean forceRandom) {
    if (forceRandom || "random".equals(Config.botNameMode())) {
      return generateRandomName();
    }

    List<String> pool = cleanNamePool;
    if (pool.isEmpty()) return fallbackName();

    String chosen = null;
    int count = 0;
    for (String n : pool) {

      if (n == null || n.isEmpty() || n.length() > 16 || !n.matches("[a-zA-Z0-9_]+")) continue;
      if (usedNames.contains(n) || Bukkit.getPlayerExact(n) != null) continue;
      count++;
      if (ThreadLocalRandom.current().nextInt(count) == 0) chosen = n;
    }
    if (chosen != null) {
      usedNames.add(chosen);
      return chosen;
    }
    return fallbackName();
  }

  private String generateRandomName() {
    String generated;
    int attempts = 0;
    do {
      generated = RandomNameGenerator.generate();
      if (++attempts > 200) return fallbackName();
    } while (generated == null || usedNames.contains(generated) || Bukkit.getPlayerExact(generated) != null);
    usedNames.add(generated);
    return generated;
  }

  private String fallbackName() {
    String generated;
    int attempts = 0;
    do {
      generated = "Bot" + ThreadLocalRandom.current().nextInt(1000, 9999);
      if (++attempts > 200) return null;
    } while (usedNames.contains(generated) || Bukkit.getPlayerExact(generated) != null);
    usedNames.add(generated);
    return generated;
  }

  public record UserBotName(String internalName, String displayName) {
  }

  public UserBotName generateUserBotName(String spawnerName, int existingCount) {
    String suffix = String.valueOf(existingCount + 1);
    final String PREFIX = "ubot_";
    final String SEP = "_";
    int maxSpawnerLen = 16 - PREFIX.length() - SEP.length() - suffix.length();
    String truncated =
        spawnerName.length() > maxSpawnerLen
            ? spawnerName.substring(0, Math.max(1, maxSpawnerLen))
            : spawnerName;
    String internal = PREFIX + truncated + SEP + suffix;
    if (internal.length() > 16) internal = internal.substring(0, 16);
    usedNames.add(internal);

    String display =
        sanitizeDisplayName(
            Config.userBotNameFormat()
                .replace("{spawner}", spawnerName)
                .replace("{num}", suffix)
                .replace("{bot_name}", internal),
            internal);
    return new UserBotName(internal, display);
  }

  public void updateAllBotPrefixes() {

    Config.debug("updateAllBotPrefixes: skipped (bots are real players, LP handles natively)");
  }

  private static boolean hasLineOfSightIgnoringGlass(Player from, Player to) {
    Location start = from.getEyeLocation();
    Location end = to.getEyeLocation();
    if (start.getWorld() == null || !start.getWorld().equals(end.getWorld())) return false;

    Vector dir = end.toVector().subtract(start.toVector());
    double distance = dir.length();
    if (distance < 1e-6) return true;
    dir.normalize();

    try {
      BlockIterator iter =
          new BlockIterator(
              start.getWorld(), start.toVector(), dir, 0.0, (int) Math.ceil(distance) + 1);
      while (iter.hasNext()) {
        Block block = iter.next();
        Material type = block.getType();
        if (!type.isSolid()) continue;
        if (isGlassLike(type)) continue;

        double blockDistSq = block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(start);
        if (blockDistSq < distance * distance) return false;
      }
    } catch (Exception e) {
      return from.hasLineOfSight(to);
    }
    return true;
  }

  private static boolean isGlassLike(Material mat) {
    return mat.name().contains("GLASS");
  }

  private static float lerpAngle(float from, float to, float t) {
    float diff = to - from;
    while (diff > 180f) diff -= 360f;
    while (diff < -180f) diff += 360f;
    return from + diff * t;
  }

  public void persistBotSettings(FakePlayer fp) {
    DatabaseManager db = plugin.getDatabaseManager();
    if (db == null || fp == null) return;
    db.updateBotAllSettings(
        fp.getUuid().toString(),
        fp.isFrozen(),
        fp.isChatEnabled(),
        fp.getChatTier(),
        fp.getRightClickCommand(),
        fp.getAiPersonality(),
        fp.isPickUpItemsEnabled(),
        fp.isPickUpXpEnabled(),
        fp.isHeadAiEnabled(),
        fp.isNavParkour(),
        fp.isNavBreakBlocks(),
        fp.isNavPlaceBlocks(),
        fp.isNavAvoidWater(),
        fp.isNavAvoidLava(),
        fp.isSwimAiEnabled(),
        fp.getChunkLoadRadius(),
        fp.getPing(),
        fp.isPveEnabled(),
        fp.getPveRange(),
        fp.getPvePriority(),
        fp.getPveMobType(),
        fp.getPveSmartAttackMode().name(),
        fp.isAutoMilkEnabled(),
        fp.isPreventBadOmen(),
        fp.isRespawnOnDeath(),
        fp.hasCustomPing(),
        fp.getLuckpermsGroup());
  }
}
