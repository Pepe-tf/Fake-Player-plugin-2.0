package me.bill.fakePlayerPlugin.license;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Fetches license credentials securely from the FPP frontend API.
 * Endpoint, API key and signing secret are all hardcoded and obfuscated.
 *
 * <p>Security model:
 * <ul>
 *   <li>HTTPS prevents passive MITM.</li>
 *   <li>Bearer API key prevents unauthenticated calls to the endpoint.</li>
 *   <li>HMAC-SHA256 signature on the response proves the payload came from our server.</li>
 *   <li>Local file cache allows offline restarts (cache is also signature-verified).</li>
 * </ul>
 */
public final class LicenseCredentialsApi {

  // ── hardcoded endpoint ──────────────────────────────────────────────────────
  // https://fpp.wtf/api/license/free
  private static final int[] _EP = {
      104, 116, 116, 112, 115, 58, 47, 47, 102, 112, 112, 46, 119, 116, 102,
      47, 97, 112, 105, 47, 108, 105, 99, 101, 110, 115, 101, 47, 102, 114, 101, 101
  };

  // ── hardcoded API key ───────────────────────────────────────────────────────
  private static final int[] _AK = {
      56, 55, 49, 57, 49, 51, 56, 48, 50, 52, 98, 53, 57, 102, 99,
      51, 57, 100, 98, 48, 57, 100, 100, 57, 49, 102, 51, 54, 51, 53,
      101, 55, 54, 51, 51, 55, 52, 52, 102, 55, 56, 54, 99, 52, 99,
      101, 50, 52, 48, 102, 54, 51, 101, 55, 102, 100, 100, 57, 53, 98,
      49, 50, 52, 54
  };

  // ── hardcoded HMAC verification secret ─────────────────────────────────────
  private static final int[] _SS = {
      101, 48, 98, 102, 99, 56, 52, 99, 45, 99, 49, 101, 48, 45, 52,
      50, 49, 97, 45, 57, 57, 102, 97, 45, 99, 53, 51, 57, 100, 52,
      102, 52, 53, 101, 98, 49
  };

  private static String _d(int[] c) {
    char[] r = new char[c.length];
    for (int i = 0; i < c.length; i++) r[i] = (char) c[i];
    return new String(r);
  }

  private static final String CACHE_FILE = "data/license-credentials-cache.json";
  private static final long CONNECT_TIMEOUT_MS = 5_000;
  private static final long READ_TIMEOUT_MS = 5_000;

  private LicenseCredentialsApi() {
  }

  public record Credentials(
      String teamId,
      String productId,
      String publicKey,
      String licenseKey,
      String signature) {
  }

  /**
   * Fetches credentials from the frontend API (preferred) or local cache (fallback).
   * Verifies the HMAC signature before returning.
   *
   * @param plugin the plugin instance for cache I/O
   * @return validated credentials, or null if nothing valid could be obtained
   */
  public static Credentials fetch(Plugin plugin) {
    FppLogger.debug("LICENSE", Config.debugLicense(), "Fetching license credentials from API...");
    Credentials live = fetchFromApi();
    if (live != null) {
      FppLogger.debug("LICENSE", Config.debugLicense(), "API returned credentials: teamId=" + live.teamId() + ", productId=" + live.productId() + ", licenseKey=" + (live.licenseKey() != null ? live.licenseKey().substring(0, Math.min(8, live.licenseKey().length())) + "..." : "null"));
      if (verifySignature(live)) {
        FppLogger.debug("LICENSE", Config.debugLicense(), "Live credentials signature verified.");
        saveCache(plugin, live);
        return live;
      } else {
        FppLogger.debug("LICENSE", Config.debugLicense(), "Live credentials signature verification failed.");
      }
    }

    Credentials cached = loadCache(plugin);
    if (cached != null && verifySignature(cached)) {
      FppLogger.debug("LICENSE", Config.debugLicense(), "Using cached credentials (signature valid).");
      return cached;
    } else if (cached != null) {
      FppLogger.debug("LICENSE", Config.debugLicense(), "Cached credentials signature verification failed.");
    }

    FppLogger.debug("LICENSE", Config.debugLicense(), "No valid license credentials available.");
    return null;
  }

  private static Credentials fetchFromApi() {
    HttpURLConnection conn = null;
    try {
      String endpoint = _d(_EP);
      FppLogger.debug("LICENSE", Config.debugLicense(), "API endpoint: " + endpoint);
      conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty("Authorization", "Bearer " + _d(_AK));
      conn.setRequestProperty("User-Agent", "FakePlayerPlugin-License");
      conn.setConnectTimeout((int) CONNECT_TIMEOUT_MS);
      conn.setReadTimeout((int) READ_TIMEOUT_MS);
      conn.setInstanceFollowRedirects(true);

      int code = conn.getResponseCode();
      FppLogger.debug("LICENSE", Config.debugLicense(), "API response code: " + code);
      if (code != 200) {
        String errBody = null;
        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
          StringBuilder sb = new StringBuilder();
          String line;
          while ((line = br.readLine()) != null) sb.append(line);
          errBody = sb.toString();
        } catch (Exception ignored) {
        }
        FppLogger.debug("LICENSE", Config.debugLicense(), "API error body: " + errBody);
        return null;
      }

      String body;
      try (BufferedReader br = new BufferedReader(
          new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        body = sb.toString();
      }
      FppLogger.debug("LICENSE", Config.debugLicense(), "API raw response: " + body);

      return parse(body);
    } catch (Exception e) {
      FppLogger.debug("LICENSE", Config.debugLicense(), "API fetch failed: " + e.getMessage());
      return null;
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
    }
  }

  private static Credentials parse(String json) {
    try {
      JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
      String teamId = getStr(obj, "team_id");
      String productId = getStr(obj, "product_id");
      String publicKey = getStr(obj, "public_key");
      String licenseKey = getStr(obj, "license_key");
      String signature = getStr(obj, "signature");

      if (teamId == null || productId == null || publicKey == null || licenseKey == null) {
        return null;
      }
      return new Credentials(teamId, productId, publicKey, licenseKey, signature);
    } catch (Exception e) {
      return null;
    }
  }

  private static String getStr(JsonObject obj, String key) {
    if (obj.has(key) && !obj.get(key).isJsonNull()) {
      return obj.get(key).getAsString();
    }
    return null;
  }

  private static boolean verifySignature(Credentials creds) {
    if (creds.signature == null || creds.signature.isBlank()
        || "unsigned".equals(creds.signature)) {
      return false;
    }
    try {
      String payload = String.join("|",
          creds.teamId, creds.productId, creds.publicKey, creds.licenseKey);
      String expected = hmacSha256(payload, _d(_SS));
      return constantTimeEquals(expected, creds.signature);
    } catch (Exception e) {
      return false;
    }
  }

  private static String hmacSha256(String payload, String secret) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    SecretKeySpec spec =
        new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    mac.init(spec);
    byte[] result = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    return bytesToHex(result);
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
    byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
    if (aBytes.length != bBytes.length) {
      return false;
    }
    int result = 0;
    for (int i = 0; i < aBytes.length; i++) {
      result |= aBytes[i] ^ bBytes[i];
    }
    return result == 0;
  }

  private static void saveCache(Plugin plugin, Credentials data) {
    try {
      File file = new File(plugin.getDataFolder(), CACHE_FILE);
      //noinspection ResultOfMethodCallIgnored
      file.getParentFile().mkdirs();
      JsonObject obj = new JsonObject();
      obj.addProperty("team_id", data.teamId);
      obj.addProperty("product_id", data.productId);
      obj.addProperty("public_key", data.publicKey);
      obj.addProperty("license_key", data.licenseKey);
      obj.addProperty("signature", data.signature);
      obj.addProperty("cached_at", System.currentTimeMillis());
      Files.writeString(file.toPath(), obj.toString(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      // ignore cache write failures
    }
  }

  private static Credentials loadCache(Plugin plugin) {
    try {
      File file = new File(plugin.getDataFolder(), CACHE_FILE);
      if (!file.exists()) {
        return null;
      }
      String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
      return parse(json);
    } catch (Exception e) {
      return null;
    }
  }
}
