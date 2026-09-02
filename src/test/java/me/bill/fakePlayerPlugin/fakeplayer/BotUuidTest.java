package me.bill.fakePlayerPlugin.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class BotUuidTest {

    @Test
    void sequentialNamesEmbedTheirNumber() {
        assertEquals(
                UUID.fromString("fb070000-0000-0000-0000-000000000001"), BotIdentityCache.deterministicBotUuid("bot"));
        assertEquals(
                UUID.fromString("fb070000-0000-0000-0000-000000000002"), BotIdentityCache.deterministicBotUuid("bot2"));
        assertEquals(
                UUID.fromString("fb070000-0000-0000-0000-00000000007b"),
                BotIdentityCache.deterministicBotUuid("bot123"));
    }

    @Test
    void namesAreCaseInsensitive() {
        assertEquals(BotIdentityCache.deterministicBotUuid("bot2"), BotIdentityCache.deterministicBotUuid("Bot2"));
        assertEquals(BotIdentityCache.deterministicBotUuid("Steve"), BotIdentityCache.deterministicBotUuid("steve"));
    }

    @Test
    void nonCanonicalSpellingsNeverCollideWithSequentialNames() {
        // "bot1" is not a generated name (number 1 is spelled "bot") - it must not claim bot #1.
        assertNotEquals(BotIdentityCache.deterministicBotUuid("bot"), BotIdentityCache.deterministicBotUuid("bot1"));
        assertNotEquals(BotIdentityCache.deterministicBotUuid("bot2"), BotIdentityCache.deterministicBotUuid("bot02"));
    }

    @Test
    void customNamesAreDeterministicAndDistinct() {
        assertEquals(BotIdentityCache.deterministicBotUuid("miner"), BotIdentityCache.deterministicBotUuid("miner"));
        assertNotEquals(
                BotIdentityCache.deterministicBotUuid("miner"), BotIdentityCache.deterministicBotUuid("farmer"));
    }

    @Test
    void allBotUuidsCarryTheFpPrefixAndAreRecognized() {
        assertTrue(BotIdentityCache.isBotUuid(BotIdentityCache.deterministicBotUuid("bot")));
        assertTrue(BotIdentityCache.isBotUuid(BotIdentityCache.deterministicBotUuid("bot42")));
        assertTrue(BotIdentityCache.isBotUuid(BotIdentityCache.deterministicBotUuid("Steve")));

        assertFalse(BotIdentityCache.isBotUuid(UUID.randomUUID()));
        // Floodgate-style Bedrock UUID (zero high bits) must not read as a bot.
        assertFalse(BotIdentityCache.isBotUuid(new UUID(0, 5)));
        // Legacy offline-mode bot UUIDs are not the new scheme.
        assertFalse(BotIdentityCache.isBotUuid(BotIdentityCache.offlineModeUuid("bot")));
    }

    @Test
    void sequentialAndNamedFormsCannotCollide() {
        // The named form sets a marker bit in the high half, so even a hash that lands on a tiny
        // value can't equal a sequential bot's UUID.
        UUID sequential = BotIdentityCache.deterministicBotUuid("bot");
        UUID named = BotIdentityCache.deterministicBotUuid("bot1");
        assertNotEquals(sequential.getMostSignificantBits(), named.getMostSignificantBits());
    }
}
