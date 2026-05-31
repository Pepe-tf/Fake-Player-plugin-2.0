package me.bill.fakePlayerPlugin.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class UpdateChecker {

    private static final String MODRINTH_API =
            "https://api.modrinth.com/v2/project/fake-player-plugin-(fpp)/version?limit=1";

    private static final String MODRINTH_PAGE = "https://modrinth.com/plugin/fake-player-plugin-(fpp)";

    private static final String VERCEL_API_BASE = "https://fpp.wtf";

    private static final Pattern VERSION_REGEX = Pattern.compile("v?\\d+(?:\\.\\d+)+");

    private static volatile UpdateInfo cachedResult = null;
    private static volatile long cacheTimestamp = 0L;
    private static final long CACHE_TTL_MS = 5L * 60L * 1000L;

    private UpdateChecker() {}

    public static void check(Plugin plugin) {
        if (!Config.updateCheckerEnabled()) {
            Config.debug("Update checker disabled in config.");
            return;
        }
        FppScheduler.runAsync(plugin, () -> {
            UpdateInfo info = fetchOrCached(plugin);
            FppScheduler.runSync(plugin, () -> handleResultOnMainThread(plugin, info));
        });
    }

    public static UpdateInfo checkBlocking(Plugin plugin, long timeoutMs) {
        if (!Config.updateCheckerEnabled()) {
            Config.debug("Update checker disabled in config.");
            return null;
        }
        final UpdateInfo[] out = {null};
        CountDownLatch latch = new CountDownLatch(1);
        FppScheduler.runAsync(plugin, () -> {
            out[0] = fetchOrCached(plugin);
            latch.countDown();
        });
        try {
            if (latch.await(Math.max(100, timeoutMs), TimeUnit.MILLISECONDS)) return out[0];
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    public static void handleResultOnMainThread(Plugin plugin, UpdateInfo info) {
        if (info == null) return;

        if (info.error != null) {

            Component failMsg = Lang.get("update-failed");
            FppLogger.warn(
                    stripLangPrefix(PlainTextComponentSerializer.plainText().serialize(failMsg))
                            + " ("
                            + info.error
                            + ")");

            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (Perm.hasOrOp(p, Perm.OP)) p.sendMessage(failMsg);
            }
            return;
        }

        String latestClean = stripV(info.latest);
        String currentClean = stripV(info.current);

        int cmp = compareVersions(info.current, info.latest);
        boolean updateAvailable = cmp < 0;
        boolean isBeta = cmp > 0;

        if (updateAvailable) {

            FppLogger.warn(
                    "Update available! v" + currentClean + " → v" + latestClean + " | Download: " + MODRINTH_PAGE);

            Component msg = Lang.get("update-available", "current", currentClean, "latest", latestClean);

            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (Perm.hasOrOp(p, Perm.OP)) p.sendMessage(msg);
            }

            if (plugin instanceof FakePlayerPlugin fpp) {
                fpp.setUpdateNotification(msg);
                fpp.setLatestKnownVersion(latestClean);
            }

        } else if (isBeta) {

            FppLogger.warn("Running BETA v"
                    + currentClean
                    + " (latest stable: v"
                    + latestClean
                    + "). "
                    + "Download stable: "
                    + MODRINTH_PAGE);

            Component msg = Lang.get("update-beta", "current", currentClean, "latest", latestClean);

            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (Perm.hasOrOp(p, Perm.OP)) p.sendMessage(msg);
            }

            if (plugin instanceof FakePlayerPlugin fpp) {
                fpp.setUpdateNotification(msg);
                fpp.setLatestKnownVersion(latestClean);
                fpp.setRunningBeta(true);
            }

        } else {

            Component ok = Lang.get("update-up-to-date", "current", currentClean);
            FppLogger.success(
                    stripLangPrefix(PlainTextComponentSerializer.plainText().serialize(ok)));

            if (plugin instanceof FakePlayerPlugin fpp) {
                fpp.setUpdateNotification(null);
                fpp.setRunningBeta(false);
            }
        }
    }

    public static void invalidateCache() {
        cachedResult = null;
        cacheTimestamp = 0L;
        Config.debug("UpdateChecker: cache invalidated.");
    }

    public static final class UpdateInfo {
        public String latest;
        public boolean latestStartsWithV;
        public String current;
        public boolean currentStartsWithV;
        public String error;
        public String downloadUrl;
    }

    private static UpdateInfo fetchOrCached(Plugin plugin) {
        long now = System.currentTimeMillis();
        if (cachedResult != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
            cachedResult.current = plugin.getPluginMeta().getVersion();
            cachedResult.currentStartsWithV = cachedResult.current.startsWith("v");
            Config.debug("UpdateChecker: using cached result (age " + ((now - cacheTimestamp) / 1000) + "s).");
            return cachedResult;
        }
        UpdateInfo fresh = fetchUpdateInfo(plugin);
        if (fresh != null && fresh.error == null) {
            cachedResult = fresh;
            cacheTimestamp = now;
        }
        return fresh;
    }

    static int compareVersions(String a, String b) {
        int[] pa = parseVersionParts(stripV(a));
        int[] pb = parseVersionParts(stripV(b));
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int na = i < pa.length ? pa[i] : 0;
            int nb = i < pb.length ? pb[i] : 0;
            if (na != nb) return Integer.compare(na, nb);
        }
        return 0;
    }

    private static int[] parseVersionParts(String v) {
        if (v == null || v.isBlank()) return new int[0];
        String[] raw = v.split("\\.", -1);
        int[] parts = new int[raw.length];
        for (int i = 0; i < raw.length; i++) {
            Matcher m = Pattern.compile("^(\\d+)").matcher(raw[i]);
            parts[i] = m.find() ? Integer.parseInt(m.group(1)) : 0;
        }
        return parts;
    }

    private static String stripV(String v) {
        return (v != null && v.startsWith("v")) ? v.substring(1) : (v != null ? v : "");
    }

    private static String stripLangPrefix(String plain) {
        try {
            String prefixRaw = Lang.raw("prefix");
            String prefixPlain = PlainTextComponentSerializer.plainText().serialize(TextUtil.colorize(prefixRaw));
            if (plain.startsWith(prefixPlain))
                return plain.substring(prefixPlain.length()).strip();
        } catch (Throwable ignored) {
        }
        return plain;
    }

    private static UpdateInfo fetchUpdateInfo(Plugin plugin) {
        UpdateInfo info = new UpdateInfo();
        info.current = plugin.getPluginMeta().getVersion();
        info.currentStartsWithV = info.current.startsWith("v");

        UpdateInfo modrinthResult = tryFetch(info.current, MODRINTH_API, true);
        if (modrinthResult != null && modrinthResult.error == null) {
            Config.debug("UpdateChecker: Modrinth → latest=" + stripV(modrinthResult.latest));
            return modrinthResult;
        }
        Config.debug("UpdateChecker: Modrinth unavailable - "
                + (modrinthResult != null ? modrinthResult.error : "null result"));

        String[] vercelCandidates = {
            VERCEL_API_BASE + "/api/check-update", VERCEL_API_BASE + "/api/status", VERCEL_API_BASE + "/api/latest",
        };
        for (String url : vercelCandidates) {
            UpdateInfo result = tryFetch(info.current, url, false);
            if (result != null && result.error == null) {
                Config.debug("UpdateChecker: Vercel → latest=" + stripV(result.latest) + " from " + url);
                return result;
            }
        }

        info.error = "could not reach Modrinth or Vercel API";
        return info;
    }

    private static UpdateInfo tryFetch(String currentVersion, String url, boolean arrayResponse) {
        UpdateInfo info = new UpdateInfo();
        info.current = currentVersion;
        info.currentStartsWithV = currentVersion.startsWith("v");
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6_000);
            conn.setReadTimeout(6_000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty(
                    "User-Agent",
                    "FakePlayerPlugin-UpdateChecker/"
                            + currentVersion
                            + " (https://modrinth.com/plugin/fake-player-plugin-(fpp))");

            int code = conn.getResponseCode();
            if (code != 200) {
                info.error = "HTTP " + code + " from " + url;
                return info;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            String body = sb.toString().trim();
            if (body.isEmpty()) {
                info.error = "empty response from " + url;
                return info;
            }

            String latest;
            String downloadUrl;

            if (arrayResponse) {

                String firstObj = extractFirstArrayElement(body);
                if (firstObj == null) {
                    info.error = "empty version array from " + url;
                    return info;
                }
                latest = extractJsonString(firstObj, "version_number");
                downloadUrl = extractFirstFileUrl(firstObj);

                if (downloadUrl == null || downloadUrl.isBlank()) {
                    downloadUrl = MODRINTH_PAGE;
                }
            } else {

                latest = extractVersion(body);
                downloadUrl = extractDownloadUrl(body);
            }

            if (latest == null || latest.isBlank()) {
                info.error = "no version found in response from " + url;
                return info;
            }

            info.latest = latest;
            info.latestStartsWithV = latest.startsWith("v");
            info.downloadUrl = downloadUrl;
            return info;

        } catch (SocketTimeoutException e) {
            info.error = "timeout connecting to " + url;
        } catch (Exception e) {
            info.error = e.getClass().getSimpleName() + ": " + e.getMessage() + " - " + url;
        }
        return info;
    }

    private static String extractFirstArrayElement(String body) {
        String trimmed = body.trim();
        if (!trimmed.startsWith("[")) return null;
        int start = trimmed.indexOf('{');
        if (start < 0) return null;
        int end = findMatchingBrace(trimmed, start);
        return end > start ? trimmed.substring(start, end + 1) : null;
    }

    private static String extractFirstFileUrl(String obj) {
        int filesIdx = obj.indexOf("\"files\"");
        if (filesIdx < 0) return null;
        int arrStart = obj.indexOf('[', filesIdx);
        if (arrStart < 0) return null;
        int objStart = obj.indexOf('{', arrStart);
        if (objStart < 0) return null;
        int objEnd = findMatchingBrace(obj, objStart);
        if (objEnd <= objStart) return null;
        return extractJsonString(obj.substring(objStart, objEnd + 1), "url");
    }

    private static String extractVersion(String body) {
        for (String key : new String[] {
            "remoteVersion", "remote_version", "version_number", "tag_name", "version", "latest", "name"
        }) {
            String val = extractJsonString(body, key);
            if (val != null && VERSION_REGEX.matcher(val).find()) return val;
        }
        for (String wrapper : new String[] {"remote", "data"}) {
            String nested = extractNestedObject(body, wrapper);
            if (nested != null) {
                for (String key : new String[] {"version", "version_number", "tag_name", "latest"}) {
                    String val = extractJsonString(nested, key);
                    if (val != null && VERSION_REGEX.matcher(val).find()) return val;
                }
            }
        }
        Matcher m = VERSION_REGEX.matcher(body);
        return m.find() ? m.group() : null;
    }

    private static String extractDownloadUrl(String body) {
        for (String key : new String[] {"downloadUrl", "download_url", "website"}) {
            String val = extractJsonString(body, key);
            if (val != null && !val.isBlank()) return val;
        }
        for (String wrapper : new String[] {"remote", "data"}) {
            String nested = extractNestedObject(body, wrapper);
            if (nested != null) {
                for (String key : new String[] {"downloadUrl", "download_url", "website"}) {
                    String val = extractJsonString(nested, key);
                    if (val != null && !val.isBlank()) return val;
                }
            }
        }
        return null;
    }

    private static String extractJsonString(String body, String key) {
        int idx = body.indexOf('"' + key + '"');
        if (idx < 0) return null;
        int colon = body.indexOf(':', idx + key.length() + 2);
        if (colon < 0) return null;
        int q1 = colon + 1;
        while (q1 < body.length() && body.charAt(q1) != '"') {
            char c = body.charAt(q1);
            if (c == '{' || c == '[') return null;
            q1++;
        }
        if (q1 >= body.length()) return null;
        int q2 = body.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return body.substring(q1 + 1, q2).trim();
    }

    private static String extractNestedObject(String body, String key) {
        int idx = body.indexOf('"' + key + '"');
        if (idx < 0) return null;
        int start = body.indexOf('{', idx + key.length() + 2);
        if (start < 0) return null;
        int end = findMatchingBrace(body, start);
        return end > start ? body.substring(start, end + 1) : null;
    }

    private static int findMatchingBrace(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                if (--depth == 0) return i;
            }
        }
        return -1;
    }
}
