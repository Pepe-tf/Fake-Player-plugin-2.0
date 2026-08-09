package me.bill.fakePlayerPlugin.auth;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bukkit.plugin.Plugin;

import me.bill.fakePlayerPlugin.util.FppLogger;

/**
 * Encrypts/decrypts the per-bot passwords {@link BotAuthManager} stores in the database, so
 * {@code fpp_bot_auth.password_enc} is never a plaintext column even though it has to be
 * reversible (unlike a login password hash, this one has to be typed again on every future join -
 * see {@link BotAuthManager}'s own class doc).
 *
 * <p>AES-256/GCM with a random 12-byte IV per call, key held only in memory plus one file on
 * disk: {@code <dataFolder>/auth.key}, generated once with {@link SecureRandom} on first use and
 * never written anywhere else (not config.yml, not logs). Anyone who can read that file plus the
 * database can recover every stored password, exactly like any other symmetric-key-at-rest
 * scheme - back up/restrict {@code auth.key} the same way you would the database itself.
 *
 * <p>Losing or rotating {@code auth.key} makes every previously-stored password permanently
 * undecryptable (by design - there's no recovery path around the encryption). {@link
 * BotAuthManager} treats a decrypt failure as "forgot this bot's password", logging a pointer to
 * {@code /fpp auth reset <bot>} rather than silently retrying with garbage.
 */
final class AuthCipher {

    private static final String KEY_FILE_NAME = "auth.key";
    private static final int KEY_BYTES = 32; // AES-256
    private static final int IV_BYTES = 12; // GCM-recommended nonce size
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    AuthCipher(Plugin plugin) {
        this.key = new SecretKeySpec(loadOrCreateKey(plugin), "AES");
    }

    private byte[] loadOrCreateKey(Plugin plugin) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();
        File keyFile = new File(dataFolder, KEY_FILE_NAME);
        try {
            if (keyFile.exists()) {
                byte[] decoded = Base64.getDecoder().decode(Files.readString(keyFile.toPath()).trim());
                if (decoded.length == KEY_BYTES) return decoded;
                FppLogger.warn("Auth: " + KEY_FILE_NAME + " is malformed - generating a fresh key. Any "
                        + "passwords already stored will fail to decrypt; run /fpp auth reset <bot> for "
                        + "any bot that then gets stuck, so it registers a new one on its next join.");
            }
            byte[] fresh = new byte[KEY_BYTES];
            random.nextBytes(fresh);
            Files.writeString(keyFile.toPath(), Base64.getEncoder().encodeToString(fresh));
            // Best-effort file lockdown - a no-op (not a failure) on filesystems/OSes that don't
            // support POSIX-style owner-only permissions (e.g. plain Windows/FAT).
            try {
                keyFile.setReadable(false, false);
                keyFile.setReadable(true, true);
                keyFile.setWritable(false, false);
                keyFile.setWritable(true, true);
            } catch (Throwable ignored) {
            }
            return fresh;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Auth: couldn't read or create " + KEY_FILE_NAME + " in the plugin data folder", e);
        }
    }

    /** Base64(iv || ciphertext+tag) - a fresh random IV every call, safe to reuse the same key indefinitely. */
    String encrypt(String plaintext) throws GeneralSecurityException {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    String decrypt(String stored) throws GeneralSecurityException {
        byte[] combined = Base64.getDecoder().decode(stored);
        if (combined.length <= IV_BYTES) throw new GeneralSecurityException("stored value too short");
        byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(combined, IV_BYTES, combined.length);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }
}
