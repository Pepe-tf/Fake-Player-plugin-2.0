package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.UUID;

public interface BotChatController {
    void restartLoops();

    void cancelAll();

    void stopAllLoopsNow();

    void forceSendMessage(FakePlayer bot, String message);

    void forceSendMessageResolved(FakePlayer bot, String message);

    double getActivityMultiplier(UUID botUuid);

    void timedMute(UUID botUuid, int seconds);
}
