package me.bill.fakePlayerPlugin.extension;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppAddon;
import me.bill.fakePlayerPlugin.api.FppApi;
import me.bill.fakePlayerPlugin.api.FppExtension;
import me.bill.fakePlayerPlugin.util.FppLogger;

public final class ExtensionLoader {

    private static final String BUNDLE_MANIFEST_ATTRIBUTE = "FPP-Extension-Bundle";
    private static final String BUNDLE_JARS_MANIFEST_ATTRIBUTE = "FPP-Extension-Jars";

    private static final class ExtensionContext {
        final File dataFolder;
        final URLClassLoader classLoader;
        volatile YamlConfiguration config;

        ExtensionContext(File dataFolder, URLClassLoader classLoader) {
            this.dataFolder = dataFolder;
            this.classLoader = classLoader;
        }
    }

    private static final ConcurrentHashMap<FppExtension, ExtensionContext> EXTENSIONS = new ConcurrentHashMap<>();

    private final FakePlayerPlugin plugin;
    private final List<URLClassLoader> classLoaders = new ArrayList<>();
    private final List<ExtensionAddonWrapper> activeWrappers = new ArrayList<>();

    public ExtensionLoader(@NotNull FakePlayerPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Static helpers called by FppExtension default methods ──────────────────

    public static @Nullable File getDataFolder(@NotNull FppExtension ext) {
        ExtensionContext ctx = EXTENSIONS.get(ext);
        return ctx != null ? ctx.dataFolder : null;
    }

    public static @NotNull YamlConfiguration getConfig(@NotNull FppExtension ext) {
        ExtensionContext ctx = EXTENSIONS.get(ext);
        if (ctx == null) {
            return new YamlConfiguration();
        }
        YamlConfiguration cfg = ctx.config;
        if (cfg != null) {
            return cfg;
        }
        synchronized (ctx) {
            cfg = ctx.config;
            if (cfg == null) {
                cfg = loadExtensionConfig(ext, ctx);
                ctx.config = cfg;
            }
            return cfg;
        }
    }

    public static void saveDefaultConfig(@NotNull FppExtension ext) {
        ExtensionContext ctx = EXTENSIONS.get(ext);
        if (ctx == null) return;
        File configFile = new File(ctx.dataFolder, "config.yml");
        if (configFile.exists()) {
            if (replaceCoreConfigCopy(ext, ctx, configFile)) {
                ctx.config = null;
                return;
            }
            syncConfigKeys(ext, ctx, configFile);
            return;
        }
        configFile.getParentFile().mkdirs();
        InputStream jarStream = getConfigStreamFromJar(ext, ctx);
        if (jarStream != null) {
            try (jarStream) {
                Files.copy(jarStream, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                FppLogger.info("[Extensions] Extracted default config for '" + ext.getName() + "'.");
            } catch (IOException e) {
                FppLogger.warn(
                        "[Extensions] Failed to extract default config for '" + ext.getName() + "': " + e.getMessage());
            }
        } else {
            try {
                configFile.createNewFile();
                FppLogger.info("[Extensions] Created empty config for '" + ext.getName() + "' (no default in JAR).");
            } catch (IOException e) {
                FppLogger.warn("[Extensions] Failed to create config for '" + ext.getName() + "': " + e.getMessage());
            }
        }
        ctx.config = null; // force reload on next getConfig()
    }

    public static void extractResources(@NotNull FppExtension ext) {
        ExtensionContext ctx = EXTENSIONS.get(ext);
        if (ctx == null) return;
        File jarFile = getJarFileForExtension(ext, ctx);
        if (jarFile == null || !jarFile.exists()) return;

        String prefix = "extension-resources/";
        try (JarFile jf = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jf.entries();
            int extracted = 0;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(prefix) || entry.isDirectory()) continue;

                String relativePath = name.substring(prefix.length());
                if (relativePath.isEmpty()) continue;

                File outFile = new File(ctx.dataFolder, relativePath);
                if (outFile.exists()) continue; // never overwrite user files

                outFile.getParentFile().mkdirs();
                try (InputStream in = jf.getInputStream(entry)) {
                    Files.copy(in, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    extracted++;
                }
            }
            if (extracted > 0) {
                FppLogger.info("[Extensions] Extracted " + extracted + " resource(s) for '" + ext.getName() + "'.");
            }
        } catch (IOException e) {
            FppLogger.warn("[Extensions] Failed to extract resources for '" + ext.getName() + "': " + e.getMessage());
        }
    }

    public static @Nullable File saveResource(@NotNull FppExtension ext, @NotNull String jarPath) {
        ExtensionContext ctx = EXTENSIONS.get(ext);
        if (ctx == null) return null;

        InputStream resourceIn = getResourceStreamFromOwnJar(ext, ctx, jarPath);
        if (resourceIn == null) {
            FppLogger.warn("[Extensions] Resource '" + jarPath + "' not found for '" + ext.getName() + "'.");
            return null;
        }

        File outFile = new File(ctx.dataFolder, new File(jarPath).getName());
        outFile.getParentFile().mkdirs();
        try (InputStream in = resourceIn) {
            Files.copy(in, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            FppLogger.warn("[Extensions] Failed to save resource '"
                    + jarPath
                    + "' for '"
                    + ext.getName()
                    + "': "
                    + e.getMessage());
            return null;
        }
        return outFile;
    }

    public static void reloadConfig(@NotNull FppExtension ext) {
        ExtensionContext ctx = EXTENSIONS.get(ext);
        if (ctx == null) return;
        synchronized (ctx) {
            ctx.config = loadExtensionConfig(ext, ctx);
        }
    }

    // ── Public API methods ─────────────────────────────────────────────────────

    public @Nullable File getExtensionDataFolder(@NotNull String extensionName) {
        for (var entry : EXTENSIONS.entrySet()) {
            if (entry.getKey().getName().equalsIgnoreCase(extensionName)) {
                return entry.getValue().dataFolder;
            }
        }
        return null;
    }

    public @Nullable YamlConfiguration getExtensionConfig(@NotNull String extensionName) {
        for (var entry : EXTENSIONS.entrySet()) {
            if (entry.getKey().getName().equalsIgnoreCase(extensionName)) {
                return getConfig(entry.getKey());
            }
        }
        return null;
    }

    public boolean isExtensionLoaded(@NotNull String extensionName) {
        for (ExtensionAddonWrapper wrapper : activeWrappers) {
            if (wrapper.getName().equalsIgnoreCase(extensionName)) return true;
        }
        return false;
    }

    public void saveDefaultExtensionConfig(@NotNull String extensionName) {
        for (var entry : EXTENSIONS.entrySet()) {
            if (entry.getKey().getName().equalsIgnoreCase(extensionName)) {
                saveDefaultConfig(entry.getKey());
                return;
            }
        }
    }

    public @NotNull List<FppAddon> getLoadedExtensions() {
        return activeWrappers.stream().map(w -> (FppAddon) w).toList();
    }

    public void reloadExtensionConfigs() {
        for (var entry : EXTENSIONS.entrySet()) {
            try {
                reloadConfig(entry.getKey());
                FppLogger.info(
                        "[Extensions] Reloaded config for '" + entry.getKey().getName() + "'.");
            } catch (Throwable t) {
                FppLogger.warn("[Extensions] Failed to reload config for '"
                        + entry.getKey().getName()
                        + "': "
                        + t.getMessage());
            }
        }
    }

    // ── Extension loading ─────────────────────────────────────────────────────

    public void loadExtensions() {
        File extensionsDir = new File(plugin.getDataFolder(), "extensions");
        if (!extensionsDir.exists()) {
            return;
        }

        File[] jars = extensionsDir.listFiles(f -> f.isFile() && f.getName().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            return;
        }

        List<ExtensionAddonWrapper> wrappers = new ArrayList<>();

        for (File jar : jars) {
            int found = isExtensionBundle(jar) ? loadBundleJar(jar, wrappers) : loadJar(jar, wrappers);
            if (found > 0) {
                FppLogger.info("[Extensions] Scanned " + jar.getName() + " — " + found + " extension(s) found.");
            }
        }

        if (wrappers.isEmpty()) {
            return;
        }

        wrappers.sort(Comparator.comparingInt(FppAddon::getPriority)
                .thenComparing(a -> a.getName().toLowerCase()));

        for (ExtensionAddonWrapper wrapper : wrappers) {
            plugin.getFppApi().registerAddon(wrapper);
        }
        activeWrappers.addAll(wrappers);

        FppLogger.info("[Extensions] Loaded " + wrappers.size() + " extension(s) from jar file(s).");
    }

    public void reload() {
        for (ExtensionAddonWrapper wrapper : activeWrappers) {
            try {
                plugin.getFppApi().unregisterAddon(wrapper);
            } catch (Throwable t) {
                FppLogger.warn(
                        "[Extensions] Failed to unregister extension '" + wrapper.getName() + "': " + t.getMessage());
            }
        }
        unregisterBukkitListeners();
        EXTENSIONS.clear();
        activeWrappers.clear();
        closeClassLoaders();
        loadExtensions();
    }

    private int loadJar(File jar, List<ExtensionAddonWrapper> wrappers) {
        return loadJar(jar, wrappers, null);
    }

    private int loadJar(File jar, List<ExtensionAddonWrapper> wrappers, @Nullable File bundledDataRoot) {
        URLClassLoader classLoader = null;
        int found = 0;

        try {
            URL[] urls = {jar.toURI().toURL()};
            classLoader = new URLClassLoader(urls, plugin.getClass().getClassLoader());

            try (JarFile jarFile = new JarFile(jar)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.endsWith(".class") || name.contains("$")) {
                        continue;
                    }

                    String className = name.replace('/', '.').substring(0, name.length() - 6);
                    try {
                        Class<?> clazz = Class.forName(className, true, classLoader);
                        if (FppExtension.class.isAssignableFrom(clazz)
                                && !clazz.isInterface()
                                && !Modifier.isAbstract(clazz.getModifiers())) {
                            FppExtension ext = (FppExtension)
                                    clazz.getDeclaredConstructor().newInstance();
                            registerContext(ext, jar, classLoader, bundledDataRoot);
                            wrappers.add(new ExtensionAddonWrapper(plugin, ext));
                            found++;
                        }
                    } catch (NoClassDefFoundError ignored) {
                    } catch (Throwable t) {
                        FppLogger.warn("[Extensions] Could not load class "
                                + className
                                + " from "
                                + jar.getName()
                                + ": "
                                + t.getMessage());
                    }
                }
            }

            if (found > 0) {
                classLoaders.add(classLoader);
            }
        } catch (IOException e) {
            FppLogger.warn("[Extensions] Failed to load " + jar.getName() + ": " + e.getMessage());
        } finally {
            if (found == 0 && classLoader != null) {
                try {
                    classLoader.close();
                } catch (IOException ignored) {
                }
            }
        }

        return found;
    }

    private boolean isExtensionBundle(File jar) {
        try (JarFile jarFile = new JarFile(jar)) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                return false;
            }
            Attributes attributes = manifest.getMainAttributes();
            return Boolean.parseBoolean(attributes.getValue(BUNDLE_MANIFEST_ATTRIBUTE));
        } catch (IOException e) {
            FppLogger.warn("[Extensions] Failed to inspect " + jar.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private int loadBundleJar(File bundleJar, List<ExtensionAddonWrapper> wrappers) {
        int found = 0;
        File cacheDir = new File(
                plugin.getDataFolder(),
                "extensions"
                        + File.separator
                        + ".cache"
                        + File.separator
                        + sanitizeFileName(stripJarSuffix(bundleJar.getName())));
        cacheDir.mkdirs();

        try (JarFile jarFile = new JarFile(bundleJar)) {
            List<JarEntry> nestedJars = getBundledExtensionEntries(jarFile);
            if (nestedJars.isEmpty()) {
                FppLogger.warn("[Extensions] Bundle " + bundleJar.getName() + " contains no nested jars.");
                return 0;
            }

            for (JarEntry nestedJar : nestedJars) {
                File extractedJar = new File(cacheDir, sanitizeFileName(new File(nestedJar.getName()).getName()));
                try (InputStream in = jarFile.getInputStream(nestedJar)) {
                    Files.copy(in, extractedJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                found += loadJar(extractedJar, wrappers, null);
            }
        } catch (IOException e) {
            FppLogger.warn(
                    "[Extensions] Failed to load extension bundle " + bundleJar.getName() + ": " + e.getMessage());
        }

        return found;
    }

    private List<JarEntry> getBundledExtensionEntries(JarFile jarFile) throws IOException {
        List<JarEntry> entries = new ArrayList<>();
        Manifest manifest = jarFile.getManifest();
        if (manifest != null) {
            String listedJars = manifest.getMainAttributes().getValue(BUNDLE_JARS_MANIFEST_ATTRIBUTE);
            if (listedJars != null && !listedJars.isBlank()) {
                for (String path : listedJars.split(",")) {
                    JarEntry entry = jarFile.getJarEntry(path.trim());
                    if (entry != null && !entry.isDirectory() && entry.getName().endsWith(".jar")) {
                        entries.add(entry);
                    }
                }
                return entries;
            }
        }

        Enumeration<JarEntry> jarEntries = jarFile.entries();
        while (jarEntries.hasMoreElements()) {
            JarEntry entry = jarEntries.nextElement();
            if (!entry.isDirectory()
                    && entry.getName().startsWith("extensions/")
                    && entry.getName().endsWith(".jar")) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparing(JarEntry::getName));
        return entries;
    }

    private static String stripJarSuffix(String name) {
        return name.endsWith(".jar") ? name.substring(0, name.length() - 4) : name;
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    public void closeClassLoaders() {
        for (URLClassLoader cl : classLoaders) {
            try {
                cl.close();
            } catch (IOException ignored) {
            }
        }
        classLoaders.clear();
    }

    private void unregisterBukkitListeners() {
        if (classLoaders.isEmpty()) return;
        Set<ClassLoader> extensionLoaders = new HashSet<>(classLoaders);
        int removed = 0;
        for (HandlerList handlerList : HandlerList.getHandlerLists()) {
            for (RegisteredListener registered : handlerList.getRegisteredListeners()) {
                ClassLoader listenerLoader = registered.getListener().getClass().getClassLoader();
                if (extensionLoaders.contains(listenerLoader)) {
                    handlerList.unregister(registered);
                    removed++;
                }
            }
        }
        if (removed > 0) {
            FppLogger.info("[Extensions] Unregistered " + removed + " Bukkit listener(s) from unloaded extensions.");
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void registerContext(FppExtension ext, File jar, URLClassLoader cl) {
        registerContext(ext, jar, cl, null);
    }

    private void registerContext(FppExtension ext, File jar, URLClassLoader cl, @Nullable File bundledDataRoot) {
        String sanitizedName = ext.getName().replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        File dataFolder = bundledDataRoot != null
                ? new File(bundledDataRoot, sanitizedName)
                : new File(plugin.getDataFolder(), "extensions" + File.separator + sanitizedName);
        dataFolder.mkdirs();
        EXTENSIONS.put(ext, new ExtensionContext(dataFolder, cl));
        FppLogger.info(
                "[Extensions] Registered data folder for '" + ext.getName() + "': " + dataFolder.getAbsolutePath());
    }

    private static @Nullable InputStream getConfigStreamFromJar(
            @NotNull FppExtension ext, @NotNull ExtensionContext ctx) {
        InputStream in = getResourceStreamFromOwnJar(ext, ctx, "config.yml");
        if (in != null) return in;
        return getResourceStreamFromOwnJar(ext, ctx, "extension-resources/config.yml");
    }

    private static @Nullable InputStream getResourceStreamFromOwnJar(
            @NotNull FppExtension ext, @NotNull ExtensionContext ctx, @NotNull String path) {
        File jarFile = getJarFileForExtension(ext, ctx);
        if (jarFile == null || !jarFile.exists()) return null;
        try (JarFile jf = new JarFile(jarFile)) {
            JarEntry entry = jf.getJarEntry(path);
            if (entry == null || entry.isDirectory()) return null;
            return new ByteArrayInputStream(jf.getInputStream(entry).readAllBytes());
        } catch (IOException e) {
            FppLogger.warn("[Extensions] Failed to read resource '"
                    + path
                    + "' from '"
                    + ext.getName()
                    + "': "
                    + e.getMessage());
            return null;
        }
    }

    private static @Nullable File getJarFileForExtension(@NotNull FppExtension ext, @NotNull ExtensionContext ctx) {
        try {
            URL[] urls = ctx.classLoader.getURLs();
            if (urls.length > 0) {
                return new File(urls[0].toURI());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static @NotNull YamlConfiguration loadExtensionConfig(
            @NotNull FppExtension ext, @NotNull ExtensionContext ctx) {
        File configFile = new File(ctx.dataFolder, "config.yml");
        if (!configFile.exists()) {
            // Extract default config first
            configFile.getParentFile().mkdirs();
            InputStream jarStream = getConfigStreamFromJar(ext, ctx);
            if (jarStream != null) {
                try (jarStream) {
                    Files.copy(jarStream, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    FppLogger.warn("[Extensions] Failed to extract default config for '"
                            + ext.getName()
                            + "': "
                            + e.getMessage());
                }
            } else {
                try {
                    configFile.createNewFile();
                } catch (IOException e) {
                    FppLogger.warn(
                            "[Extensions] Failed to create config for '" + ext.getName() + "': " + e.getMessage());
                }
            }
        } else {
            replaceCoreConfigCopy(ext, ctx, configFile);
        }

        YamlConfiguration diskCfg = YamlConfiguration.loadConfiguration(configFile);

        // Set defaults from JAR config
        InputStream jarStream = getConfigStreamFromJar(ext, ctx);
        if (jarStream != null) {
            try (jarStream) {
                YamlConfiguration defaults =
                        YamlConfiguration.loadConfiguration(new InputStreamReader(jarStream, StandardCharsets.UTF_8));
                diskCfg.setDefaults(defaults);
            } catch (IOException ignored) {
            }
        }

        return diskCfg;
    }

    private static void syncConfigKeys(
            @NotNull FppExtension ext, @NotNull ExtensionContext ctx, @NotNull File configFile) {
        InputStream jarStream = getConfigStreamFromJar(ext, ctx);
        if (jarStream == null) return;

        YamlConfiguration jarCfg;
        YamlConfiguration diskCfg;
        try (jarStream) {
            jarCfg = YamlConfiguration.loadConfiguration(new InputStreamReader(jarStream, StandardCharsets.UTF_8));
            diskCfg = YamlConfiguration.loadConfiguration(configFile);
        } catch (IOException e) {
            return;
        }

        List<String> missing = new ArrayList<>();
        for (String key : jarCfg.getKeys(true)) {
            if (jarCfg.isConfigurationSection(key)) continue;
            if (!diskCfg.contains(key)) missing.add(key);
        }

        if (missing.isEmpty()) return;

        for (String key : missing) {
            diskCfg.set(key, jarCfg.get(key));
        }

        try {
            diskCfg.save(configFile);
            FppLogger.info("[Extensions] " + ext.getName() + " config.yml: added " + missing.size() + " new key(s).");
        } catch (IOException e) {
            FppLogger.warn("[Extensions] Failed to save synced config for '" + ext.getName() + "': " + e.getMessage());
        }
    }

    private static boolean replaceCoreConfigCopy(
            @NotNull FppExtension ext, @NotNull ExtensionContext ctx, @NotNull File configFile) {
        InputStream jarStream = getConfigStreamFromJar(ext, ctx);
        if (jarStream == null) return false;

        YamlConfiguration jarCfg;
        YamlConfiguration diskCfg;
        try (jarStream) {
            jarCfg = YamlConfiguration.loadConfiguration(new InputStreamReader(jarStream, StandardCharsets.UTF_8));
            diskCfg = YamlConfiguration.loadConfiguration(configFile);
        } catch (IOException e) {
            return false;
        }

        if (!diskCfg.contains("config-version") || jarCfg.contains("config-version")) {
            return false;
        }

        InputStream replacement = getConfigStreamFromJar(ext, ctx);
        if (replacement == null) return false;
        try (replacement) {
            Files.copy(replacement, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            FppLogger.warn("[Extensions] Replaced core config.yml copy in '"
                    + ext.getName()
                    + "' extension folder with the extension default config.");
            return true;
        } catch (IOException e) {
            FppLogger.warn("[Extensions] Failed to repair config for '" + ext.getName() + "': " + e.getMessage());
            return false;
        }
    }

    private static final class ExtensionAddonWrapper implements FppAddon {

        private final FakePlayerPlugin plugin;
        private final FppExtension extension;

        ExtensionAddonWrapper(@NotNull FakePlayerPlugin plugin, @NotNull FppExtension extension) {
            this.plugin = plugin;
            this.extension = extension;
        }

        @Override
        public @NotNull String getName() {
            return extension.getName();
        }

        @Override
        public @NotNull String getVersion() {
            return extension.getVersion();
        }

        @Override
        public @NotNull Plugin getPlugin() {
            return plugin;
        }

        @Override
        public @NotNull String getDescription() {
            return extension.getDescription();
        }

        @Override
        public @NotNull List<String> getAuthors() {
            return extension.getAuthors();
        }

        @Override
        public @NotNull List<String> getDependencies() {
            return extension.getDependencies();
        }

        @Override
        public @NotNull List<String> getSoftDependencies() {
            return extension.getSoftDependencies();
        }

        @Override
        public int getPriority() {
            return extension.getPriority();
        }

        @Override
        public void onEnable(@NotNull FppApi api) {
            extension.onEnable(api);
        }

        @Override
        public void onDisable() {
            extension.onDisable();
        }
    }
}
