package me.bill.fakePlayerPlugin.fakeplayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class SkinProfileInjector {

    private static final String TEXTURES_KEY = "textures";

    private SkinProfileInjector() {}

    public static void apply(Object gameProfile, SkinProfile skin) throws Exception {
        if (skin == null) return;
        apply(gameProfile, skin.getValue(), skin.getSignature());
    }

    public static void clear(Object gameProfile) throws Exception {
        if (gameProfile == null) return;
        Object propertyMap = resolvePropertyMap(gameProfile);
        clearExistingTextures(propertyMap);
    }

    public static void apply(Object gameProfile, String value, String signature) throws Exception {
        if (gameProfile == null || value == null || value.isBlank()) return;

        ClassLoader cl = gameProfile.getClass().getClassLoader();
        Class<?> propertyClass = cl.loadClass("com.mojang.authlib.properties.Property");
        Object property = createProperty(propertyClass, value, signature);

        Object propertyMap = resolvePropertyMap(gameProfile);
        if (tryApply(propertyMap, property)) return;

        throw new IllegalStateException("Could not find a writable PropertyMap in GameProfile");
    }

    public static Object createGameProfile(Class<?> gameProfileClass, UUID uuid, String name, SkinProfile skin)
            throws Exception {
        if (skin == null || !skin.isValid()) {
            Constructor<?> ctor = gameProfileClass.getConstructor(UUID.class, String.class);
            return ctor.newInstance(uuid, name);
        }

        ClassLoader cl = gameProfileClass.getClassLoader();
        Class<?> propertyClass = cl.loadClass("com.mojang.authlib.properties.Property");
        Class<?> propertyMapClass = cl.loadClass("com.mojang.authlib.properties.PropertyMap");
        Class<?> multimapClass = cl.loadClass("com.google.common.collect.Multimap");
        Class<?> arrayListMultimapClass = cl.loadClass("com.google.common.collect.ArrayListMultimap");

        // Insert the texture property into the backing multimap BEFORE wrapping it in a
        // PropertyMap/GameProfile. This is a normal Multimap.put (mutating the multimap's contents),
        // never a reflective mutation of PropertyMap's final backing field — so it does not trip the
        // JVM "Final field ... mutated reflectively" warning.
        Object multimap = arrayListMultimapClass.getMethod("create").invoke(null);
        Object property = createProperty(propertyClass, skin.getValue(), skin.getSignature());
        multimapClass.getMethod("put", Object.class, Object.class).invoke(multimap, TEXTURES_KEY, property);

        Object propertyMap = propertyMapClass.getConstructor(multimapClass).newInstance(multimap);
        Constructor<?> ctor = gameProfileClass.getConstructor(UUID.class, String.class, propertyMapClass);
        return ctor.newInstance(uuid, name, propertyMap);
    }

    private static Object createProperty(Class<?> propertyClass, String value, String signature) throws Exception {
        if (signature == null || signature.isBlank()) {
            try {
                Constructor<?> ctor = propertyClass.getDeclaredConstructor(String.class, String.class);
                ctor.setAccessible(true);
                return ctor.newInstance(TEXTURES_KEY, value);
            } catch (NoSuchMethodException ignored) {
                Constructor<?> ctor = propertyClass.getDeclaredConstructor(String.class, String.class, String.class);
                ctor.setAccessible(true);
                return ctor.newInstance(TEXTURES_KEY, value, "");
            }
        }

        try {
            Constructor<?> ctor = propertyClass.getDeclaredConstructor(String.class, String.class, String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(TEXTURES_KEY, value, signature);
        } catch (NoSuchMethodException ignored) {
            Constructor<?> ctor = propertyClass.getDeclaredConstructor(String.class, String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(TEXTURES_KEY, value);
        }
    }

    private static Object resolvePropertyMap(Object gameProfile) throws Exception {
        Object propertyMap = invokeNoArg(gameProfile, "getProperties");
        if (propertyMap != null) return propertyMap;

        propertyMap = invokeNoArg(gameProfile, "properties");
        if (propertyMap != null) return propertyMap;

        for (Field field : getAllFields(gameProfile.getClass())) {
            if (!"properties".equals(field.getName())) continue;
            field.setAccessible(true);
            Object direct = field.get(gameProfile);
            if (direct != null) return direct;
        }

        throw new NoSuchMethodException(gameProfile.getClass().getName() + ".getProperties()");
    }

    private static Object invokeNoArg(Object target, String methodName) {
        for (Method method : getAllMethods(target.getClass())) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 0) continue;
            try {
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static boolean tryApply(Object candidate, Object property) {
        if (candidate == null) return false;

        // Old authlib: PropertyMap IS a Multimap (extends ForwardingMultimap), so put/removeAll
        // on the candidate itself delegate to its backing multimap. No field mutation.
        clearExistingTextures(candidate);
        if (invokePut(candidate, property)) return true;

        // Newer authlib (1.21.11): PropertyMap wraps a *final* backing Multimap in a `properties`
        // field/accessor and no longer exposes put/removeAll. Read that multimap and mutate its
        // CONTENTS — never reflectively reassign the final field (which the JVM now warns about and
        // will block in a future release).
        Object backing = resolveBackingMultimap(candidate);
        if (backing != null && backing != candidate) {
            clearExistingTextures(backing);
            if (invokePut(backing, property)) return true;
        }
        return false;
    }

    /**
     * Reads (never writes) the mutable backing {@code Multimap} that a {@code PropertyMap} delegates
     * to, via its record-style {@code properties()} accessor or the {@code properties} field.
     */
    private static Object resolveBackingMultimap(Object propertyMap) {
        Object viaAccessor = invokeNoArg(propertyMap, "properties");
        if (isMultimap(viaAccessor)) return viaAccessor;

        for (Field field : getAllFields(propertyMap.getClass())) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            if (!"properties".equals(field.getName())) continue;
            try {
                field.setAccessible(true);
                Object value = field.get(propertyMap);
                if (isMultimap(value)) return value;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static boolean isMultimap(Object obj) {
        if (obj == null) return false;
        try {
            ClassLoader cl = obj.getClass().getClassLoader();
            Class<?> multimapClass = cl != null
                    ? cl.loadClass("com.google.common.collect.Multimap")
                    : Class.forName("com.google.common.collect.Multimap");
            return multimapClass.isInstance(obj);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean invokePut(Object propertyMap, Object property) {
        for (Method method : getAllMethods(propertyMap.getClass())) {
            if (!"put".equals(method.getName()) || method.getParameterCount() != 2) continue;
            method.setAccessible(true);

            try {
                method.invoke(propertyMap, TEXTURES_KEY, property);
                return true;
            } catch (Exception ignored) {
            }

            try {
                List<Object> list = new ArrayList<>();
                list.add(property);
                method.invoke(propertyMap, TEXTURES_KEY, list);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static void clearExistingTextures(Object propertyMap) {

        for (Method method : getAllMethods(propertyMap.getClass())) {
            if (!"removeAll".equals(method.getName()) || method.getParameterCount() != 1) continue;
            try {
                method.setAccessible(true);
                method.invoke(propertyMap, TEXTURES_KEY);
                return;
            } catch (Exception ignored) {
            }
        }

        for (Method method : getAllMethods(propertyMap.getClass())) {
            if (!"remove".equals(method.getName()) || method.getParameterCount() != 1) continue;
            try {
                method.setAccessible(true);
                method.invoke(propertyMap, TEXTURES_KEY);
                return;
            } catch (Exception ignored) {
            }
        }
    }

    private static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
        }
        return fields;
    }

    private static List<Method> getAllMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            methods.addAll(Arrays.asList(current.getDeclaredMethods()));
        }
        methods.addAll(Arrays.asList(type.getMethods()));
        return methods;
    }
}
