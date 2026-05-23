package me.bill.fakePlayerPlugin.license;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;

/**
 * Handles license verification and validation for the Lukittu-style licensing system.
 * Manages the communication with the license server, validates responses, and performs
 * cryptographic verification of license challenges.
 */
public final class LicenseManager {

  private static final String RESULT_KEY = "result";
  private static final String VALID_KEY = "valid";
  private static final String API_BASE_URL = "https://app.lukittu.com/api/v1/client/teams";
  private static final String VERIFY_ENDPOINT = "/verification/verify";
  private static final String HEARTBEAT_ENDPOINT = "/verification/heartbeat";
  private static final String VERSION = "1.0.0";
  private static final int TIMEOUT_MILLIS = 10000;
  private static final String ERROR_CODE_KEY = "code";
  private static final String ERROR_DETAILS_KEY = "details";

  private static final Gson GSON = new GsonBuilder()
      .disableHtmlEscaping()
      .create();

  private static final Map<String, String> ERROR_MESSAGES;

  static {
    Map<String, String> messages = new HashMap<>();
    messages.put("INTERNAL_SERVER_ERROR", "An unexpected error occurred on the server.");
    messages.put("BAD_REQUEST", "The request was malformed or missing required parameters.");
    messages.put("LICENSE_NOT_FOUND", "License not specified in config.yml, or it is invalid.");
    messages.put("VALID", "The license is valid.");
    messages.put("IP_LIMIT_REACHED",
        "License's IP address limit has been reached. Contact support if you have issues with this.");
    messages.put("HWID_LIMIT_REACHED", "You have reached the HWID-based usage limit. Try again later.");
    messages.put("PRODUCT_NOT_FOUND", "The specified product does not exist.");
    messages.put("CUSTOMER_NOT_FOUND", "The customer information could not be located.");
    messages.put("LICENSE_EXPIRED", "The license has expired.");
    messages.put("LICENSE_SUSPENDED", "The license is suspended and cannot be used.");
    messages.put("TEAM_NOT_FOUND", "The specified team could not be found.");
    messages.put("RATE_LIMIT",
        "Too many connections in a short time from the same IP address. Please wait a while!");
    messages.put("HARDWARE_IDENTIFIER_BLACKLISTED", "This hardware identifier is blacklisted. Contact support.");
    messages.put("COUNTRY_BLACKLISTED", "Your country is not allowed to use this license.");
    messages.put("IP_BLACKLISTED", "Your IP address is blacklisted.");
    messages.put("RELEASE_NOT_FOUND", "The specified release could not be found.");
    messages.put("FORBIDDEN", "Your team does not have access to classloader.");
    ERROR_MESSAGES = Collections.unmodifiableMap(messages);
  }

  private final FakePlayerPlugin plugin;
  private final String licenseKey;
  private final String teamId;
  private final String productId;
  private final String publicKey;
  private String hardwareIdentifier;
  private volatile boolean valid = false;
  private ScheduledExecutorService scheduler;

  public LicenseManager(FakePlayerPlugin plugin, LicenseCredentialsApi.Credentials credentials) {
    this.plugin = plugin;
    this.licenseKey = credentials.licenseKey();
    this.teamId = credentials.teamId();
    this.productId = credentials.productId();
    this.publicKey = credentials.publicKey();
  }

  public boolean isValid() {
    return valid;
  }

  public void verify() throws Exception {
    hardwareIdentifier = getHardwareIdentifier();
    String challenge = generateRandomChallenge();
    String url = API_BASE_URL + "/" + teamId + VERIFY_ENDPOINT;

    String jsonBody = String.format(
        "{\"licenseKey\":\"%s\",\"productId\":\"%s\",\"challenge\":\"%s\",\"version\":\"%s\",\"hardwareIdentifier\":\"%s\"}",
        licenseKey, productId, challenge, VERSION, hardwareIdentifier);

    boolean success = fetchAndHandleResponse(url, jsonBody, publicKey, challenge);
    if (!success) {
      throw new Exception("License verification failed");
    }
    valid = true;
    plugin.getLogger().info("License verification passed.");
  }

  public void startHeartbeat() {
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdownNow();
    }
    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "FPP-LicenseHeartbeat");
      t.setDaemon(true);
      return t;
    });
    scheduler.scheduleAtFixedRate(() -> {
      try {
        sendHeartbeat();
      } catch (Exception e) {
        plugin.getLogger().log(Level.WARNING, "License heartbeat failed", e);
      }
    }, 15, 15, TimeUnit.MINUTES);
  }

  public void shutdown() {
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  private String generateRandomChallenge() {
    SecureRandom secureRandom = new SecureRandom();
    byte[] randomBytes = new byte[32];
    secureRandom.nextBytes(randomBytes);
    return bytesToHex(randomBytes);
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      result.append(String.format("%02x", b));
    }
    return result.toString();
  }

  private boolean fetchAndHandleResponse(String urlString, String jsonBody, String publicKeyBase64,
      String challenge) throws IOException {
    HttpURLConnection connection = null;
    boolean success = false;

    try {
      var url = URI.create(urlString).toURL();
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setRequestProperty("User-Agent", buildUserAgent());
      connection.setConnectTimeout(TIMEOUT_MILLIS);
      connection.setReadTimeout(TIMEOUT_MILLIS);
      connection.setDoOutput(true);

      try (var os = connection.getOutputStream()) {
        byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
        os.write(input, 0, input.length);
      }

      int responseCode = connection.getResponseCode();

      if (responseCode == HttpURLConnection.HTTP_OK) {
        try (var inputStream = connection.getInputStream()) {
          success = handleJsonResponse(inputStream, publicKeyBase64, challenge);
        }
      } else {
        try (var errorStream = connection.getErrorStream()) {
          if (errorStream != null) {
            handleJsonResponse(errorStream, null, null);
          }
        }
        if (responseCode >= 400) {
          plugin.getLogger().warning("License HTTP Error: " + responseCode
              + " - Check your team ID, product ID and license key");
        }
      }
    } catch (Exception e) {
      plugin.getLogger().log(Level.SEVERE, "Connection to license service failed", e);
      plugin.getLogger().warning("License connection failure! Check server connectivity");
      try {
        if (connection != null && connection.getErrorStream() != null) {
          handleJsonResponse(connection.getErrorStream(), null, null);
        }
      } catch (IOException e1) {
        plugin.getLogger().log(Level.SEVERE, "Failed to parse error response", e1);
      }
      throw new IOException("Connection to license server failed", e);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }

    return success;
  }

  private boolean handleJsonResponse(InputStream inputStream, String publicKey, String challenge)
      throws IOException {
    if (inputStream == null) {
      throw new IOException("Input stream is null");
    }

    try (var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      JsonObject json = GSON.fromJson(reader, JsonObject.class);
      String respString = GSON.toJson(json);

      if (publicKey != null && challenge != null) {
        if (validateResponse(json) && validateChallenge(json, challenge, publicKey)) {
          return true;
        }
      }

      if (json.has(RESULT_KEY)) {
        JsonObject result = json.getAsJsonObject(RESULT_KEY);
        if (result.has(ERROR_CODE_KEY)) {
          String errorCode = result.get(ERROR_CODE_KEY).getAsString();
          String errorMessage = ERROR_MESSAGES.getOrDefault(errorCode,
              "License check failed with code: " + errorCode);
          if (result.has(ERROR_DETAILS_KEY)) {
            errorMessage += " (" + result.get(ERROR_DETAILS_KEY).getAsString() + ")";
          }
          plugin.getLogger().warning("License Error: " + errorMessage);
          return false;
        }
      }

      return !handleErrorCodes(respString);
    }
  }

  private boolean validateChallenge(JsonObject response, String originalChallenge, String base64PublicKey) {
    try {
      if (!validateResponse(response) || originalChallenge == null || base64PublicKey == null) {
        return false;
      }
      String signedChallenge = response.getAsJsonObject(RESULT_KEY)
          .get("challengeResponse").getAsString();
      return verifySignature(originalChallenge, signedChallenge, base64PublicKey);
    } catch (Exception e) {
      plugin.getLogger().log(Level.SEVERE, "Challenge validation failed", e);
      plugin.getLogger().warning("License signature verification failed! Possible tampering detected");
      return false;
    }
  }

  private boolean verifySignature(String challenge, String signatureHex, String base64PublicKey) {
    try {
      byte[] signatureBytes = hexStringToByteArray(signatureHex);
      byte[] decodedKeyBytes = Base64.getDecoder().decode(base64PublicKey);

      String decodedKeyString = new String(decodedKeyBytes, StandardCharsets.UTF_8)
          .replace("-----BEGIN PUBLIC KEY-----", "")
          .replace("-----END PUBLIC KEY-----", "")
          .replaceAll("\\s", "");

      byte[] publicKeyBytes = Base64.getDecoder().decode(decodedKeyString);
      X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      java.security.PublicKey publicKey = keyFactory.generatePublic(keySpec);

      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initVerify(publicKey);
      signature.update(challenge.getBytes(StandardCharsets.UTF_8));

      return signature.verify(signatureBytes);
    } catch (IllegalArgumentException e) {
      plugin.getLogger().log(Level.SEVERE, "Invalid Base64 input for public key", e);
      plugin.getLogger().warning("License: Invalid public key format! Contact support");
      return false;
    } catch (Exception e) {
      plugin.getLogger().log(Level.SEVERE, "Signature verification failed", e);
      return false;
    }
  }

  private static byte[] hexStringToByteArray(String hex) {
    int len = hex.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
          + Character.digit(hex.charAt(i + 1), 16));
    }
    return data;
  }

  private static boolean validateResponse(JsonObject json) {
    try {
      JsonObject result = json.getAsJsonObject(RESULT_KEY);
      return result != null && result.has(VALID_KEY) && result.get(VALID_KEY).getAsBoolean();
    } catch (Exception e) {
      return false;
    }
  }

  private String buildUserAgent() {
    return String.format("LukittuLoader/%s (%s %s; %s)",
        VERSION,
        System.getProperty("os.name"),
        System.getProperty("os.version"),
        System.getProperty("os.arch"));
  }

  private void logResponse(String response) {
    if (response != null) {
      plugin.getLogger().info("Received JSON response (pretty printed):");
      plugin.getLogger().info(response);
    }
  }

  private boolean handleErrorCodes(final String response) {
    if (response == null) {
      return false;
    }
    Optional<Map.Entry<String, String>> errorEntry = ERROR_MESSAGES.entrySet().stream()
        .filter(entry -> response.contains(entry.getKey()))
        .findFirst();
    if (errorEntry.isPresent()) {
      String errorMessage = errorEntry.get().getValue();
      plugin.getLogger().severe(errorMessage);
      plugin.getLogger().warning("License Error: " + errorMessage);
      return true;
    }
    if (response.contains("\"valid\":false")) {
      plugin.getLogger().warning("License: License validation failed. Check your license configuration");
      return true;
    }
    return false;
  }

  private void sendHeartbeat() throws Exception {
    String urlString = API_BASE_URL + "/" + teamId + HEARTBEAT_ENDPOINT;
    var url = URI.create(urlString).toURL();

    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("User-Agent", buildUserAgent());
    connection.setConnectTimeout(TIMEOUT_MILLIS);
    connection.setReadTimeout(TIMEOUT_MILLIS);
    connection.setDoOutput(true);

    String jsonBody = String.format(
        "{\"licenseKey\":\"%s\",\"productId\":\"%s\",\"hardwareIdentifier\":\"%s\"}",
        licenseKey, productId, hardwareIdentifier);

    try (var os = connection.getOutputStream()) {
      byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
      os.write(input, 0, input.length);
    }

    int responseCode = connection.getResponseCode();

    try (var is = (responseCode < HttpURLConnection.HTTP_BAD_REQUEST)
        ? connection.getInputStream()
        : connection.getErrorStream();
        var br = new BufferedReader(new InputStreamReader(is))) {
      StringBuilder response = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
        response.append(line);
      }
      if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
        plugin.getLogger().warning("Heartbeat failed with response code: " + responseCode);
        handleErrorCodes(response.toString());
      }
    } catch (IOException e) {
      plugin.getLogger().log(Level.WARNING, "Failed to read heartbeat response", e);
    } finally {
      connection.disconnect();
    }
  }

  private String getHardwareIdentifier() {
    try {
      String osName = System.getProperty("os.name");
      String osVersion = System.getProperty("os.version");
      String osArch = System.getProperty("os.arch");
      String hostname = InetAddress.getLocalHost().getHostName();
      String combinedIdentifier = osName + osVersion + osArch + hostname;
      return UUID.nameUUIDFromBytes(combinedIdentifier.getBytes(StandardCharsets.UTF_8)).toString();
    } catch (Exception e) {
      plugin.getLogger().warning("Failed to get hardware identifier: " + e.getMessage());
      plugin.getLogger().warning("License: Hostname retrieval failed, using random identifier");
      return UUID.randomUUID().toString();
    }
  }
}
