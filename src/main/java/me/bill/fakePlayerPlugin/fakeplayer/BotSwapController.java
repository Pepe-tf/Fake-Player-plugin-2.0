package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.Set;
import java.util.UUID;

public interface BotSwapController {
    void schedule(FakePlayer fp);

    void cancel(UUID uuid);

    void cancelAll();

    boolean triggerNow(String botName);

    int getSwappedOutCount();

    int getActiveSessionCount();

    Set<UUID> getActiveSessions();

    long getNextSwapSeconds();

    long getSessionExpiry(UUID uuid);

    String getPersonalityLabel(UUID uuid);

    int getSwapCount(UUID uuid);
}
