package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.fakeplayer.BotNavUtil;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.fakeplayer.StorageInteractionHelper;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class StorageCommand implements FppCommand {

    private static final List<String> SUBCOMMANDS =
            List.of("--list", "--remove", "--clear", "--enable", "--disable", "--deposit");

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;
    private final StorageStore storageStore;
    private final PathfindingService pathfinding;

    public StorageCommand(
            FakePlayerPlugin plugin,
            FakePlayerManager manager,
            StorageStore storageStore,
            PathfindingService pathfinding) {
        this.plugin = plugin;
        this.manager = manager;
        this.storageStore = storageStore;
        this.pathfinding = pathfinding;
    }

    @Override
    public String getName() {
        return "storage";
    }

    @Override
    public String getUsage() {
        return "<bot> [storage_name|--list|--remove <name>|--clear]";
    }

    @Override
    public String getDescription() {
        return "Set or manage storage targets for a bot (chest, barrel, hopper, shulker, etc.).";
    }

    @Override
    public String getPermission() {
        return Perm.STORAGE;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.STORAGE);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Lang.get("storage-usage"));
            return true;
        }

        String botName = args[0];
        FakePlayer fp = manager.getByName(botName);
        if (fp == null) {
            sender.sendMessage(Lang.get("storage-bot-not-found", "name", botName));
            return true;
        }

        if (args.length >= 2) {
            String sub = args[1].toLowerCase(Locale.ROOT);

            switch (sub) {
                case "--list", "list" -> {
                    handleList(sender, fp);
                    return true;
                }
                case "--remove", "remove" -> {
                    if (args.length < 3) {
                        sender.sendMessage(Lang.get("storage-remove-usage", "name", fp.getDisplayName()));
                        return true;
                    }
                    handleRemove(sender, fp, args[2]);
                    return true;
                }
                case "--clear", "clear" -> {
                    handleClear(sender, fp);
                    return true;
                }
                case "--enable", "enable", "--disable", "disable" -> {
                    if (args.length < 3) {
                        sender.sendMessage(Component.text(
                                "Usage: /fpp storage " + fp.getName() + " " + sub + " <name>", NamedTextColor.RED));
                        return true;
                    }
                    boolean enabled = sub.contains("enable") && !sub.contains("disable");
                    boolean ok = storageStore.setEnabled(fp.getName(), args[2], enabled);
                    sender.sendMessage(Component.text(
                            ok ? "Storage updated." : "Storage not found.",
                            ok ? NamedTextColor.YELLOW : NamedTextColor.RED));
                    return true;
                }
                case "--deposit", "deposit" -> {
                    depositInventory(sender, fp, args.length >= 3 ? args[2] : null);
                    return true;
                }
                default -> {}
            }
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("player-only"));
            return true;
        }

        Block target = player.getTargetBlockExact(8);
        if (target == null) {
            sender.sendMessage(Lang.get("storage-look-at-container"));
            return true;
        }

        BlockState state = target.getState();
        if (target.getType() != Material.CHEST
                && target.getType() != Material.TRAPPED_CHEST
                && target.getType() != Material.BARREL) {
            sender.sendMessage(
                    Lang.get("storage-invalid-block", "block", target.getType().name()));
            return true;
        }

        String storageName = (args.length >= 2 && !args[1].startsWith("--") && !args[1].isBlank())
                ? args[1]
                : storageStore.nextAutoName(fp.getName());

        storageStore.setStorage(fp.getName(), storageName, target.getLocation());
        sender.sendMessage(Lang.get(
                "storage-set",
                "name",
                fp.getDisplayName(),
                "storage",
                storageName,
                "block",
                target.getType().name(),
                "x",
                String.valueOf(target.getX()),
                "y",
                String.valueOf(target.getY()),
                "z",
                String.valueOf(target.getZ())));
        return true;
    }

    private void handleList(CommandSender sender, FakePlayer fp) {
        List<StorageStore.StoragePoint> list = storageStore.getAllStorages(fp.getName());
        if (list.isEmpty()) {
            sender.sendMessage(Lang.get("storage-list-empty", "name", fp.getDisplayName()));
            return;
        }
        sender.sendMessage(
                Lang.get("storage-list-header", "name", fp.getDisplayName(), "count", String.valueOf(list.size())));
        int i = 1;
        for (StorageStore.StoragePoint point : list) {
            Location loc = point.location();
            String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "?";
            sender.sendMessage(Lang.get(
                    "storage-list-entry",
                    "index",
                    String.valueOf(i++),
                    "storage",
                    point.name() + (point.enabled() ? "" : " (disabled)"),
                    "world",
                    worldName,
                    "x",
                    String.valueOf(loc.getBlockX()),
                    "y",
                    String.valueOf(loc.getBlockY()),
                    "z",
                    String.valueOf(loc.getBlockZ())));
        }
    }

    private void handleRemove(CommandSender sender, FakePlayer fp, String storageName) {
        boolean removed = storageStore.removeStorage(fp.getName(), storageName);
        if (removed) {
            sender.sendMessage(Lang.get("storage-removed", "name", fp.getDisplayName(), "storage", storageName));
        } else {
            sender.sendMessage(Lang.get("storage-not-found", "name", fp.getDisplayName(), "storage", storageName));
        }
    }

    private void handleClear(CommandSender sender, FakePlayer fp) {
        int cleared = storageStore.clearStorages(fp.getName());
        sender.sendMessage(Lang.get("storage-cleared", "name", fp.getDisplayName(), "count", String.valueOf(cleared)));
    }

    private void depositInventory(CommandSender sender, FakePlayer fp, String storageName) {
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return;
        StorageStore.StoragePoint point = chooseStorage(fp, bot, storageName);
        if (point == null) {
            sender.sendMessage(Component.text("No enabled storage found.", NamedTextColor.RED));
            return;
        }
        Block block = point.location().getBlock();
        LocationFace face = faceLocation(bot, block);
        pathfinding.navigate(
                fp,
                new PathfindingService.NavigationRequest(
                        PathfindingService.Owner.SYSTEM,
                        () -> face.loc,
                        1.25,
                        0.0,
                        10,
                        () -> StorageInteractionHelper.interact(
                                fp,
                                face.loc,
                                block,
                                plugin,
                                manager,
                                (holder, liveBot) -> moveInventory(liveBot.getInventory(), holder.getInventory()),
                                null),
                        null,
                        null));
        sender.sendMessage(Component.text("Walking to storage: " + point.name(), NamedTextColor.YELLOW));
    }

    private StorageStore.StoragePoint chooseStorage(FakePlayer fp, Player bot, String storageName) {
        List<StorageStore.StoragePoint> points = storageStore.getStorages(fp.getName());
        StorageStore.StoragePoint best = null;
        double bestDist = Double.MAX_VALUE;
        for (StorageStore.StoragePoint point : points) {
            if (storageName != null && !point.name().equalsIgnoreCase(storageName)) continue;
            if (point.location().getWorld() != bot.getWorld()) continue;
            Block block = point.location().getBlock();
            if (!(block.getState() instanceof InventoryHolder)) continue;
            double dist = point.location().distanceSquared(bot.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = point;
            }
        }
        return best;
    }

    private record LocationFace(Location loc) {}

    private LocationFace faceLocation(Player bot, Block block) {
        Location loc = block.getLocation().add(0.5, 0, 0.5);
        Location stand = BotNavUtil.findStandLocation(
                block.getWorld(), (x, y, z) -> false, block.getX(), block.getY(), block.getZ());
        if (stand == null) stand = bot.getLocation();
        Location face = stand.clone();
        face.setYaw(BotNavUtil.faceToward(face, loc).getYaw());
        face.setPitch(BotNavUtil.faceToward(face, loc).getPitch());
        return new LocationFace(face);
    }

    private int moveInventory(Inventory from, Inventory to) {
        int moved = 0;
        for (int i = 0; i < from.getSize(); i++) {
            ItemStack item = from.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            Map<Integer, ItemStack> leftover = to.addItem(item.clone());
            if (leftover.isEmpty()) {
                from.setItem(i, null);
                moved += item.getAmount();
            } else {
                ItemStack rest = leftover.values().iterator().next();
                moved += item.getAmount() - rest.getAmount();
                from.setItem(i, rest);
            }
        }
        return moved;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) return List.of();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (FakePlayer fp : manager.getActivePlayers()) {
                if (fp.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(fp.getName());
            }
            return out;
        }

        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            FakePlayer fp = manager.getByName(args[0]);
            List<String> out = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) out.add(sub);
            }
            if (fp != null) {
                String next = storageStore.nextAutoName(fp.getName());
                if (next.startsWith(prefix)) out.add(next);
                for (String name : storageStore.getStorageNames(fp.getName())) {
                    if (name.toLowerCase(Locale.ROOT).startsWith(prefix) && !out.contains(name)) out.add(name);
                }
            }
            return out;
        }

        if (args.length == 3) {
            String sub = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("--remove") || sub.equals("remove")) {
                FakePlayer fp = manager.getByName(args[0]);
                if (fp == null) return List.of();
                String prefix = args[2].toLowerCase(Locale.ROOT);
                List<String> out = new ArrayList<>();
                for (String name : storageStore.getStorageNames(fp.getName())) {
                    if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(name);
                }
                return out;
            }
        }

        return List.of();
    }
}
