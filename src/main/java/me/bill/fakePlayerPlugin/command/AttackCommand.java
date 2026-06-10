package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.event.FppBotAttackEvent;
import me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotAccess;
import me.bill.fakePlayerPlugin.util.FppScheduler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class AttackCommand implements FppCommand {

    private static final Map<Material, Integer> WEAPON_COOLDOWN = Map.ofEntries(
            Map.entry(Material.NETHERITE_SWORD, 12),
            Map.entry(Material.DIAMOND_SWORD, 12),
            Map.entry(Material.IRON_SWORD, 12),
            Map.entry(Material.GOLDEN_SWORD, 12),
            Map.entry(Material.STONE_SWORD, 12),
            Map.entry(Material.WOODEN_SWORD, 12),
            Map.entry(Material.NETHERITE_AXE, 20),
            Map.entry(Material.DIAMOND_AXE, 20),
            Map.entry(Material.IRON_AXE, 22),
            Map.entry(Material.GOLDEN_AXE, 20),
            Map.entry(Material.STONE_AXE, 25),
            Map.entry(Material.WOODEN_AXE, 25),
            Map.entry(Material.TRIDENT, 22),
            Map.entry(Material.MACE, 33));
    private static final int DEFAULT_COOLDOWN = 5;

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;
    private final Map<UUID, Integer> attackTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> cooldownTicks = new ConcurrentHashMap<>();

    public AttackCommand(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "attack";
    }

    @Override
    public String getUsage() {
        return "<bot|all> [--once|--stop]  |  --stop";
    }

    @Override
    public String getDescription() {
        return "Make a bot swing at the entity it is looking at.";
    }

    @Override
    public String getPermission() {
        return Perm.ATTACK;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.ATTACK);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("attack-usage"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("--stop")) {
            stopAll();
            sender.sendMessage(Lang.get("attack-stopped-all"));
            return true;
        }

        String botName = args[0];
        boolean once = false;
        boolean stop = false;
        for (int i = 1; i < args.length; i++) {
            switch (args[i].toLowerCase()) {
                case "--once" -> once = true;
                case "--stop" -> stop = true;
                default -> {
                    sender.sendMessage(Lang.get("attack-usage"));
                    return true;
                }
            }
        }

        if (botName.equalsIgnoreCase("--all") || botName.equalsIgnoreCase("all")) {
            if (stop) {
                stopAll();
                sender.sendMessage(Lang.get("attack-stopped-all"));
                return true;
            }
            int count = startAllAllowed(sender, once);
            sender.sendMessage(
                    count == 0
                            ? Lang.get("attack-no-bots")
                            : Lang.get("attack-started-all", "count", String.valueOf(count)));
            return true;
        }

        FakePlayer fp = manager.getByName(botName);
        if (fp == null) {
            sender.sendMessage(Lang.get("attack-not-found", "name", botName));
            return true;
        }
        if (sender instanceof Player player
                && !Perm.hasOrOp(sender, Perm.ADMIN)
                && !BotAccess.canAdminister(player, fp)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            sender.sendMessage(Lang.get("attack-bot-offline", "name", fp.getDisplayName()));
            return true;
        }

        if (stop) {
            stopAttacking(fp.getUuid());
            sender.sendMessage(Lang.get("attack-stopped", "name", fp.getDisplayName()));
            return true;
        }

        startForBot(fp, once);
        sender.sendMessage(
                once
                        ? Lang.get("attack-started-once", "name", fp.getDisplayName())
                        : Lang.get("attack-started", "name", fp.getDisplayName()));
        return true;
    }

    private int startAllAllowed(CommandSender sender, boolean once) {
        int count = 0;
        for (FakePlayer fp : manager.getActivePlayers()) {
            if (sender instanceof Player player
                    && !Perm.hasOrOp(sender, Perm.ADMIN)
                    && !BotAccess.canAdminister(player, fp)) {
                continue;
            }
            Player bot = fp.getPlayer();
            if (bot == null || !bot.isOnline()) continue;
            startForBot(fp, once);
            count++;
        }
        return count;
    }

    private void startForBot(FakePlayer fp, boolean once) {
        Player bot = fp.getPlayer();
        if (bot == null) return;
        stopAttacking(fp.getUuid());
        FppApiImpl.fireTaskEvent(fp, "attack", FppBotTaskEvent.Action.START);
        UUID uuid = fp.getUuid();
        cooldownTicks.put(uuid, 0);
        int taskId = FppScheduler.runAtEntityRepeatingWithId(plugin, bot, () -> tickAttack(fp, uuid, once), 0L, 1L);
        attackTasks.put(uuid, taskId);
    }

    private void tickAttack(FakePlayer fp, UUID uuid, boolean once) {
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            stopAttacking(uuid);
            return;
        }
        if (fp.isInventoryOpen()) return;

        int cooldown = cooldownTicks.getOrDefault(uuid, 0);
        if (cooldown > 0) {
            cooldownTicks.put(uuid, cooldown - 1);
            return;
        }

        ServerPlayer nms = ((CraftPlayer) bot).getHandle();
        var nmsMainHand = nms.getItemInHand(InteractionHand.MAIN_HAND);
        if (nms.getCooldowns().isOnCooldown(nmsMainHand)) return;

        EntityHitResult entityHit = rayTraceForEntity(nms);
        if (entityHit == null) return;

        Entity target = entityHit.getEntity().getBukkitEntity();
        if (manager.getByUuid(target.getUniqueId()) != null) return;

        bot.swingMainHand();
        var event = new FppBotAttackEvent(new FppBotImpl(fp), target, 1.0);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        NmsPlayerSpawner.performAttack(bot, target, 1.0);
        ItemStack mainHand = bot.getInventory().getItemInMainHand();
        Material weapon = mainHand.getType() != Material.AIR ? mainHand.getType() : null;
        cooldownTicks.put(uuid, getWeaponCooldown(weapon));

        if (once) stopAttacking(uuid);
    }

    private static int getWeaponCooldown(@Nullable Material weapon) {
        if (weapon == null) return DEFAULT_COOLDOWN;
        return WEAPON_COOLDOWN.getOrDefault(weapon, DEFAULT_COOLDOWN);
    }

    public void stopAttacking(UUID botUuid) {
        Integer taskId = attackTasks.remove(botUuid);
        if (taskId != null) FppScheduler.cancelTask(taskId);
        cooldownTicks.remove(botUuid);
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp != null) FppApiImpl.fireTaskEvent(fp, "attack", FppBotTaskEvent.Action.STOP);
    }

    public void stopAll() {
        new HashSet<>(attackTasks.keySet()).forEach(this::stopAttacking);
    }

    public boolean isAttacking(UUID botUuid) {
        return attackTasks.containsKey(botUuid);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String s : List.of("--stop", "--all", "all")) if (s.startsWith(prefix)) out.add(s);
            for (FakePlayer fp : manager.getActivePlayers()) {
                if (fp.getName().toLowerCase().startsWith(prefix)) out.add(fp.getName());
            }
            return out;
        }
        String prefix = args[args.length - 1].toLowerCase();
        Set<String> used = new HashSet<>();
        for (int i = 1; i < args.length - 1; i++) used.add(args[i].toLowerCase());
        List<String> out = new ArrayList<>();
        for (String flag : List.of("--once", "--stop"))
            if (!used.contains(flag) && flag.startsWith(prefix)) out.add(flag);
        return out;
    }

    @SuppressWarnings("resource")
    @Nullable
    private static EntityHitResult rayTraceForEntity(ServerPlayer player) {
        double reach = player.gameMode.isCreative() ? 5.0 : 4.5;
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 viewVec = player.getViewVector(1.0f);
        Vec3 endPos = eyePos.add(viewVec.x * reach, viewVec.y * reach, viewVec.z * reach);
        AABB searchBox =
                player.getBoundingBox().expandTowards(viewVec.scale(reach)).inflate(1.0);

        EntityHitResult best = null;
        double bestSq = reach * reach;
        for (var entity : player.level().getEntities(player, searchBox, e -> !e.isSpectator() && e.isAttackable())) {
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
            var hitOpt = box.clip(eyePos, endPos);
            if (box.contains(eyePos)) {
                if (bestSq >= 0) {
                    best = new EntityHitResult(entity, hitOpt.orElse(eyePos));
                    bestSq = 0;
                }
            } else if (hitOpt.isPresent()) {
                double distance = eyePos.distanceToSqr(hitOpt.get());
                if (distance < bestSq) {
                    best = new EntityHitResult(entity, hitOpt.get());
                    bestSq = distance;
                }
            }
        }
        return best;
    }
}
