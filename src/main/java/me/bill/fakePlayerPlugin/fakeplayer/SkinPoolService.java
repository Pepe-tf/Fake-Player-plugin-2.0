package me.bill.fakePlayerPlugin.fakeplayer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.SkinModelDetector.SkinModel;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;

/**
 * Rarity-based bot skin pools, fed from {@code plugins/FakePlayerPlugin/skins/}:
 *
 * <ul>
 *   <li>{@code main_skin.txt} — the default skin every bot spawns with.
 *   <li>{@code 1-<N>%.txt} — a "1 in N" pool ({@code 1-1000%.txt} = each spawn has a 1/1000 chance
 *       to draw a random skin from that file instead of the main skin). Any number of pool files
 *       can be added; rarest pools are rolled first.
 * </ul>
 *
 * <p>Files contain one NameMC skin URL per line ({@code https://namemc.com/skin/<id>}). NameMC only
 * hosts the PNG — a skin needs a Mojang-signed texture property to render on clients — so the PNG is
 * run through the MineSkin API once, the model (slim/classic) is detected from the raw pixels via
 * {@link SkinModelDetector} and passed as the upload variant, and the signed result is cached
 * permanently in {@code data/skin-cache.yml}. After first resolution a skin never touches the
 * network again.
 */
public final class SkinPoolService {

    private static final Pattern POOL_FILE = Pattern.compile("^1-(\\d+)%?\\.txt$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAMEMC_URL = Pattern.compile("namemc\\.com/skin/([0-9a-fA-F]{8,32})");
    private static final String MINESKIN_ENDPOINT = "https://api.mineskin.org/generate/url";
    private static final String USER_AGENT = "FakePlayerPlugin/2.0.0";
    private static final long MIN_MS_BETWEEN_UPLOADS = 6_000L;

    /**
     * Bundled resource name → seeded data-folder name. Resource names must stay {@code %}-free:
     * Bukkit resolves plugin resources through a URL, where {@code %} is an escape character, so a
     * bundled {@code 1-1000%.txt} can never be extracted ({@code %.t} is an invalid escape).
     */
    private static final Map<String, String> BUNDLED_FILES = Map.of(
            "main_skin.txt", "main_skin.txt",
            "1-1000.txt", "1-1000%.txt",
            "1-5000.txt", "1-5000%.txt",
            "1-10000.txt", "1-10000%.txt",
            "1-100000.txt", "1-100000%.txt");

    /** One rarity tier: denominator N ("1 in N") and its NameMC skin ids. */
    private record Pool(long denominator, List<String> skinIds) {}

    /** A rolled skin: which NameMC id to use and which rarity pool it came from (null = main). */
    public record Roll(String skinId, long rarityDenominator) {
        public boolean isRare() {
            return rarityDenominator > 1;
        }
    }

    private final FakePlayerPlugin plugin;
    private final File skinsDir;
    private final File cacheFile;
    private final Object cacheLock = new Object();
    private final Object uploadLock = new Object();

    private volatile List<Pool> pools = List.of();
    private volatile String mainSkinId = null;
    private YamlConfiguration cache;
    private long lastUploadMs = 0;

    public SkinPoolService(@NotNull FakePlayerPlugin plugin) {
        this.plugin = plugin;
        this.skinsDir = new File(plugin.getDataFolder(), "skins");
        this.cacheFile = new File(new File(plugin.getDataFolder(), "data"), "skin-cache.yml");
        reload();
        prewarmMainSkin();
    }

    /**
     * Resolves the main skin in the background at startup (cache-first, so this is a no-op on every
     * start after the first) — the common case of a fresh spawn then never waits on MineSkin.
     */
    private void prewarmMainSkin() {
        String main = mainSkinId;
        if (main == null) return;
        if (getCached(main, "prewarm") != null) {
            Config.debugSkinPool("prewarm: main skin " + main + " already cached - nothing to do.");
            return;
        }
        Config.debugSkinPool("prewarm: resolving main skin " + main + " in the background.");
        FppScheduler.runAsync(plugin, () -> fetchAndSign(main, "pool:" + main + ":main"));
    }

    public void reload() {
        seedBundledFiles();
        loadPools();
        synchronized (cacheLock) {
            cache = YamlConfiguration.loadConfiguration(cacheFile);
        }
    }

    private void seedBundledFiles() {
        // Seed when the directory is missing OR holds no skin files — the latter self-heals
        // installs where an earlier failed extraction left an empty directory behind.
        File[] existing =
                skinsDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".txt"));
        if (existing != null && existing.length > 0) return;
        if (!skinsDir.exists() && !skinsDir.mkdirs()) return;

        for (Map.Entry<String, String> entry : BUNDLED_FILES.entrySet()) {
            File target = new File(skinsDir, entry.getValue());
            try (InputStream in = plugin.getResource("skins/" + entry.getKey())) {
                if (in == null) {
                    FppLogger.warn("SkinPoolService: bundled resource skins/" + entry.getKey() + " missing from jar.");
                    continue;
                }
                Files.copy(in, target.toPath());
            } catch (IOException e) {
                FppLogger.warn("SkinPoolService: failed to seed " + entry.getValue() + ": " + e.getMessage());
            }
        }
    }

    private void loadPools() {
        List<Pool> loaded = new ArrayList<>();
        String main = null;

        File[] files =
                skinsDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".txt"));
        if (files != null) {
            for (File file : files) {
                List<String> ids = parseSkinIds(file);
                if ("main_skin.txt".equalsIgnoreCase(file.getName())) {
                    if (!ids.isEmpty()) main = ids.get(0);
                    continue;
                }
                Matcher m = POOL_FILE.matcher(file.getName());
                if (!m.matches()) {
                    FppLogger.warn("SkinPoolService: ignoring unrecognized skin file '" + file.getName()
                            + "' (expected main_skin.txt or 1-<N>%.txt)");
                    continue;
                }
                long denominator = Long.parseLong(m.group(1));
                if (denominator < 2 || ids.isEmpty()) {
                    Config.debugSkinPool("pool file " + file.getName() + " skipped (denominator=" + denominator
                            + ", skins=" + ids.size() + ").");
                    continue;
                }
                Config.debugSkinPool(
                        "pool 1-in-" + denominator + ": " + ids.size() + " skin(s) from " + file.getName());
                loaded.add(new Pool(denominator, ids));
            }
        }

        // Rarest first, so a lucky spawn always claims the best tier it rolled.
        loaded.sort(Comparator.comparingLong(Pool::denominator).reversed());
        this.pools = List.copyOf(loaded);
        this.mainSkinId = main;

        int total = loaded.stream().mapToInt(p -> p.skinIds().size()).sum();
        if (main == null) {
            FppLogger.warn("SkinPoolService: no main skin loaded (skins/main_skin.txt missing or empty)"
                    + " - bots will spawn with the vanilla default skin.");
        }
        FppLogger.info("SkinPoolService: loaded " + loaded.size() + " rarity pool(s) with " + total + " skin(s)"
                + (main != null ? " + main skin" : "") + ".");
    }

    private static List<String> parseSkinIds(File file) {
        List<String> ids = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                Matcher m = NAMEMC_URL.matcher(line.trim());
                if (m.find()) ids.add(m.group(1).toLowerCase(Locale.ROOT));
            }
        } catch (IOException e) {
            FppLogger.warn("SkinPoolService: failed to read " + file.getName() + ": " + e.getMessage());
        }
        return ids;
    }

    // ── Rolling ──────────────────────────────────────────────────────────────

    /** Rolls the rarity ladder: rarest pool first, main skin when nothing hits. */
    public @Nullable Roll roll() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Pool pool : pools) {
            if (random.nextLong(pool.denominator()) == 0) {
                String id = pool.skinIds().get(random.nextInt(pool.skinIds().size()));
                return new Roll(id, pool.denominator());
            }
        }
        String main = mainSkinId;
        return main != null ? new Roll(main, 1) : null;
    }

    /**
     * Rolls a skin for a freshly spawned bot and resolves it to a signed texture asynchronously.
     * The callback runs on the main thread with a valid {@link SkinProfile}, or {@code null} when
     * nothing could be resolved (no pools configured, network failure) — callers fall back to the
     * vanilla default skin exactly as before.
     */
    public void rollAndResolve(@NotNull FakePlayer fp, @NotNull Consumer<@Nullable SkinProfile> callback) {
        Roll roll = roll();
        if (roll == null) {
            Config.debugSkinPool("roll for '" + fp.getName() + "': nothing to roll (" + pools.size()
                    + " pool(s), mainSkin=" + mainSkinId + ") - vanilla default skin.");
            deliver(callback, null);
            return;
        }
        Config.debugSkinPool("roll for '" + fp.getName() + "': "
                + (roll.isRare() ? "RARE 1-in-" + roll.rarityDenominator() : "main") + " skin " + roll.skinId());
        if (roll.isRare()) {
            FppLogger.info("SkinPoolService: bot '" + fp.getName() + "' rolled a 1-in-" + roll.rarityDenominator()
                    + " rare skin (" + roll.skinId() + ")");
        }
        resolve(roll, fp, callback);
    }

    private void resolve(Roll roll, FakePlayer fp, Consumer<@Nullable SkinProfile> callback) {
        String source = "pool:" + roll.skinId() + (roll.isRare() ? ":1-in-" + roll.rarityDenominator() : ":main");

        SkinProfile cached = getCached(roll.skinId(), source);
        if (cached != null) {
            Config.debugSkinPool("skin " + roll.skinId() + ": cache HIT (model=" + getCachedModel(roll.skinId())
                    + ", signed=" + (cached.getSignature() != null) + ") - delivering to '" + fp.getName() + "'.");
            announceIfRare(fp, roll);
            deliver(callback, cached);
            return;
        }

        Config.debugSkinPool("skin " + roll.skinId() + ": cache MISS - fetching+signing async for '" + fp.getName()
                + "' (skin applies when done; bot wears default until then).");
        FppScheduler.runAsync(plugin, () -> {
            SkinProfile resolved = fetchAndSign(roll.skinId(), source);
            if (resolved != null) {
                announceIfRare(fp, roll);
                deliver(callback, resolved);
                return;
            }
            // Rare roll failed to resolve — degrade to the main skin rather than no skin at all.
            String main = mainSkinId;
            if (roll.isRare() && main != null && !main.equals(roll.skinId())) {
                Config.debugSkinPool(
                        "skin " + roll.skinId() + ": rare resolve failed - degrading to main skin " + main + ".");
                SkinProfile mainSkin = getCached(main, "pool:" + main + ":main");
                if (mainSkin == null) mainSkin = fetchAndSign(main, "pool:" + main + ":main");
                deliver(callback, mainSkin);
                return;
            }
            Config.debugSkinPool("skin " + roll.skinId() + ": resolve failed, no fallback - '" + fp.getName()
                    + "' keeps the vanilla default skin.");
            deliver(callback, null);
        });
    }

    private void announceIfRare(FakePlayer fp, Roll roll) {
        if (!roll.isRare()) return;
        FppScheduler.runSync(plugin, () -> {
            var ownerUuid = fp.getSpawnedByUuid();
            Player owner = ownerUuid != null ? Bukkit.getPlayer(ownerUuid) : null;
            if (owner != null && owner.isOnline()) {
                owner.sendMessage(Lang.get(
                        "skin-rare-rolled", "name", fp.getDisplayName(), "rarity", "1/" + roll.rarityDenominator()));
            }
        });
    }

    // ── Cache ────────────────────────────────────────────────────────────────

    private @Nullable SkinProfile getCached(String skinId, String source) {
        synchronized (cacheLock) {
            if (cache == null) return null;
            String value = cache.getString("skins." + skinId + ".value");
            String signature = cache.getString("skins." + skinId + ".signature");
            if (value == null || value.isBlank()) return null;
            return new SkinProfile(value, signature, source);
        }
    }

    /** The detected model for a cached pool skin, if known. */
    public @NotNull SkinModel getCachedModel(String skinId) {
        synchronized (cacheLock) {
            if (cache == null) return SkinModel.UNKNOWN;
            String model = cache.getString("skins." + skinId + ".model");
            if ("slim".equalsIgnoreCase(model)) return SkinModel.SLIM;
            if ("classic".equalsIgnoreCase(model)) return SkinModel.CLASSIC;
            return SkinModel.UNKNOWN;
        }
    }

    private void putCache(String skinId, String value, String signature, SkinModel model) {
        synchronized (cacheLock) {
            if (cache == null) cache = new YamlConfiguration();
            cache.set("skins." + skinId + ".value", value);
            cache.set("skins." + skinId + ".signature", signature);
            cache.set("skins." + skinId + ".model", model.variant());
            try {
                File parent = cacheFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                cache.save(cacheFile);
            } catch (IOException e) {
                FppLogger.warn("SkinPoolService: failed to save skin cache: " + e.getMessage());
            }
        }
    }

    // ── Network: PNG download → model detection → MineSkin signing ──────────

    /** NameMC's direct skin-image endpoint, plus the legacy texture mirror as a fallback. */
    private static String[] textureUrls(String skinId) {
        return new String[] {
            "https://s.namemc.com/i/" + skinId + ".png",
            "https://texture.namemc.com/" + skinId.substring(0, 2) + "/" + skinId.substring(2, 4) + "/" + skinId
                    + ".png"
        };
    }

    /** How long a failed resolve blocks re-attempts for that skin (protects the MineSkin quota). */
    private static final long FAILURE_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(10);

    private final Map<String, Long> failureCooldown = new ConcurrentHashMap<>();

    /** Blocking — must run off the main thread. */
    private @Nullable SkinProfile fetchAndSign(String skinId, String source) {
        Long retryAt = failureCooldown.get(skinId);
        if (retryAt != null && System.currentTimeMillis() < retryAt) {
            Config.debugSkinPool("skin " + skinId + ": in failure cooldown for another "
                    + (retryAt - System.currentTimeMillis()) / 1000 + "s - skipping fetch.");
            return null;
        }
        try {
            // The local PNG download only feeds model detection — MineSkin fetches the URL itself
            // from its own servers. NameMC's CDN bot-blocks some hosts (403 for datacenter IPs), so
            // a failed local download is NOT fatal: fall back to variant "auto" and let MineSkin
            // detect the model server-side; the signed texture still tells us the real model after.
            byte[] png = null;
            String workingUrl = null;
            for (String url : textureUrls(skinId)) {
                try {
                    png = download(url);
                    workingUrl = url;
                    Config.debugSkinPool("skin " + skinId + ": downloaded " + png.length + "B from " + url);
                    break;
                } catch (IOException e) {
                    Config.debugSkinPool("skin " + skinId + ": download failed from " + url + ": " + e.getMessage());
                }
            }

            SkinModel model;
            String uploadUrl;
            if (png != null) {
                model = SkinModelDetector.detectFromPng(png);
                uploadUrl = workingUrl;
                Config.debugSkinPool("skin " + skinId + ": model detected as " + model + " (pixel analysis).");
            } else {
                model = SkinModel.UNKNOWN; // variant() == "auto" — MineSkin detects server-side
                uploadUrl = textureUrls(skinId)[0];
                Config.debugSkinPool("skin " + skinId + ": local texture download blocked - deferring fetch and"
                        + " model detection to MineSkin (variant=auto).");
            }

            JsonObject result = mineSkinGenerate(skinId, uploadUrl, model.variant());
            if (result == null) {
                markFailed(skinId);
                return null;
            }
            String value = result.get("value").getAsString();
            String signature = result.has("signature") ? result.get("signature").getAsString() : null;
            if (value == null || value.isBlank()) {
                FppLogger.warn("SkinPoolService: MineSkin response for " + skinId + " carried no texture value.");
                markFailed(skinId);
                return null;
            }

            // Prefer the authoritative model from the signed texture over the pixel guess.
            SkinModel signedModel = SkinModelDetector.detectFromTextureValue(value);
            putCache(skinId, value, signature, signedModel != SkinModel.UNKNOWN ? signedModel : model);
            failureCooldown.remove(skinId);
            Config.debugSkinPool("skin " + skinId + ": signed OK (signature=" + (signature != null) + ", model="
                    + (signedModel != SkinModel.UNKNOWN ? signedModel : model) + ") - cached permanently.");
            return new SkinProfile(value, signature, source);
        } catch (Exception e) {
            FppLogger.warn("SkinPoolService: failed to resolve skin " + skinId + ": " + e.getMessage());
            markFailed(skinId);
            return null;
        }
    }

    private void markFailed(String skinId) {
        failureCooldown.put(skinId, System.currentTimeMillis() + FAILURE_COOLDOWN_MS);
    }

    private byte[] download(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(5_000);
        // Browser-like headers: NameMC's CDN bot-filters plain Java user agents on some networks.
        conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                        + " Chrome/124.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "image/png,image/*;q=0.8,*/*;q=0.5");
        try (InputStream in = conn.getInputStream()) {
            return in.readAllBytes();
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Uploads the PNG URL to MineSkin for Mojang signing; returns the texture object with
     * {@code value}/{@code signature}, or null. Serialized with a minimum gap between uploads to
     * respect MineSkin's anonymous rate limit — cache-first resolution means each skin pays this
     * cost exactly once, ever.
     */
    private @Nullable JsonObject mineSkinGenerate(String skinId, String pngUrl, String variant) {
        synchronized (uploadLock) {
            long wait = lastUploadMs + MIN_MS_BETWEEN_UPLOADS - System.currentTimeMillis();
            if (wait > 0) {
                Config.debugSkinPool("skin " + skinId + ": waiting " + wait + "ms before MineSkin upload"
                        + " (rate-limit spacing).");
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            lastUploadMs = System.currentTimeMillis();

            Config.debugSkinPool(
                    "skin " + skinId + ": requesting MineSkin sign (variant=" + variant + ", url=" + pngUrl + ")");
            try {
                HttpURLConnection conn = (HttpURLConnection)
                        URI.create(MINESKIN_ENDPOINT).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(30_000);
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setRequestProperty("Content-Type", "application/json");
                String apiKey = Config.skinMineSkinApiKey();
                if (apiKey != null && !apiKey.isBlank()) {
                    // Optional — a MineSkin API key grants a much larger quota than anonymous use.
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
                }
                conn.setDoOutput(true);
                String body = "{\"url\":\"" + pngUrl + "\",\"variant\":\"" + variant + "\",\"name\":\"fpp\"}";
                conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

                int code = conn.getResponseCode();
                Config.debugSkinPool("skin " + skinId + ": MineSkin responded HTTP " + code);
                if (code < 200 || code >= 300) {
                    String errBody = readErrorBody(conn);
                    FppLogger.warn("SkinPoolService: MineSkin returned HTTP " + code + " for skin " + skinId
                            + (errBody != null ? ": " + errBody : ""));
                    conn.disconnect();
                    return null;
                }
                JsonElement root;
                try (InputStream in = conn.getInputStream()) {
                    root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                } finally {
                    conn.disconnect();
                }
                if (!root.isJsonObject()) {
                    Config.debugSkinPool("skin " + skinId + ": MineSkin response was not a JSON object.");
                    return null;
                }
                JsonObject obj = root.getAsJsonObject();
                // v1 shape: data.texture.{value,signature}; v2 shape: skin.texture.data.{value,signature}
                JsonObject data = obj.getAsJsonObject("data");
                if (data != null && data.getAsJsonObject("texture") != null) {
                    return data.getAsJsonObject("texture");
                }
                JsonObject skin = obj.getAsJsonObject("skin");
                if (skin != null
                        && skin.getAsJsonObject("texture") != null
                        && skin.getAsJsonObject("texture").getAsJsonObject("data") != null) {
                    return skin.getAsJsonObject("texture").getAsJsonObject("data");
                }
                Config.debugSkinPool(
                        "skin " + skinId + ": MineSkin response had no recognizable texture shape: " + obj);
                return null;
            } catch (Exception e) {
                FppLogger.warn("SkinPoolService: MineSkin request failed for skin " + skinId + ": " + e.getMessage());
                return null;
            }
        }
    }

    private static @Nullable String readErrorBody(HttpURLConnection conn) {
        try (InputStream err = conn.getErrorStream()) {
            if (err == null) return null;
            return new String(err.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private void deliver(Consumer<@Nullable SkinProfile> callback, @Nullable SkinProfile profile) {
        if (Bukkit.isPrimaryThread()) {
            callback.accept(profile);
            return;
        }
        FppScheduler.runSync(plugin, () -> callback.accept(profile));
    }
}
