package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.function.BiConsumer;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.util.FppScheduler;

public final class StorageInteractionHelper {

    private StorageInteractionHelper() {}

    public static void interact(
            FakePlayer fp,
            Location faceLoc,
            Block block,
            FakePlayerPlugin plugin,
            FakePlayerManager manager,
            BiConsumer<InventoryHolder, Player> transferFn,
            @Nullable Runnable onFinally) {

        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            if (onFinally != null) onFinally.run();
            return;
        }

        Location actualLoc = bot.getLocation().clone();
        actualLoc.setYaw(faceLoc.getYaw());
        actualLoc.setPitch(faceLoc.getPitch());
        manager.lockForAction(fp.getUuid(), actualLoc);
        bot.setRotation(faceLoc.getYaw(), faceLoc.getPitch());
        NmsPlayerSpawner.setHeadYaw(bot, faceLoc.getYaw());
        NmsPlayerSpawner.setMovementForward(bot, 0f);
        bot.setSprinting(false);

        FppScheduler.runSyncLater(
                plugin,
                () -> {
                    Player lb = fp.getPlayer();
                    if (lb == null || !lb.isOnline()) {
                        manager.unlockAction(fp.getUuid());
                        if (onFinally != null) onFinally.run();
                        return;
                    }
                    BotNavUtil.useStorageBlock(lb, block);

                    FppScheduler.runSyncLater(
                            plugin,
                            () -> {
                                Player liveBot = fp.getPlayer();
                                if (liveBot == null || !liveBot.isOnline()) {
                                    manager.unlockAction(fp.getUuid());
                                    if (onFinally != null) onFinally.run();
                                    return;
                                }
                                Block liveBlock = block.getLocation().getBlock();
                                if (!(liveBlock.getState() instanceof InventoryHolder liveHolder)) {
                                    manager.unlockAction(fp.getUuid());
                                    if (onFinally != null) onFinally.run();
                                    return;
                                }
                                transferFn.accept(liveHolder, liveBot);
                                liveBot.closeInventory();
                                manager.unlockAction(fp.getUuid());
                                if (onFinally != null) onFinally.run();
                            },
                            3L);
                },
                1L);
    }
}
