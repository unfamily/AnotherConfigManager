package net.unfamily.anotherconfigmanager.config;

import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.unfamily.anotherconfigmanager.AnotherConfigManager;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovers {@link ModConfig} instances registered for a mod id.
 * Uses {@link ConfigTracker} (NeoForge 26 removed {@code ModContainer#getConfigs()}).
 */
public final class ConfigDiscovery {
    private static final Field CONFIGS_BY_MOD;

    static {
        Field field;
        try {
            field = ConfigTracker.class.getDeclaredField("configsByMod");
            field.setAccessible(true);
        } catch (ReflectiveOperationException error) {
            field = null;
            AnotherConfigManager.LOGGER.error("Unable to access ConfigTracker.configsByMod", error);
        }
        CONFIGS_BY_MOD = field;
    }

    private ConfigDiscovery() {}

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    public static List<ModConfig> configsFor(String modId) {
        if (CONFIGS_BY_MOD == null || modId == null || modId.isBlank()) {
            return List.of();
        }
        try {
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, List<ModConfig>> map =
                    (ConcurrentHashMap<String, List<ModConfig>>) CONFIGS_BY_MOD.get(ConfigTracker.INSTANCE);
            if (map == null) {
                return List.of();
            }
            List<ModConfig> configs = map.get(modId);
            if (configs == null || configs.isEmpty()) {
                return List.of();
            }
            return List.copyOf(configs);
        } catch (ReflectiveOperationException | ClassCastException error) {
            AnotherConfigManager.LOGGER.warn("Failed to list configs for mod {}: {}", modId, error.toString());
            return List.of();
        }
    }

    public static List<String> modIdsWithConfigs() {
        if (CONFIGS_BY_MOD == null) {
            return List.of();
        }
        try {
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, List<ModConfig>> map =
                    (ConcurrentHashMap<String, List<ModConfig>>) CONFIGS_BY_MOD.get(ConfigTracker.INSTANCE);
            if (map == null) {
                return List.of();
            }
            List<String> ids = new ArrayList<>(map.keySet());
            Collections.sort(ids);
            return ids;
        } catch (ReflectiveOperationException | ClassCastException error) {
            AnotherConfigManager.LOGGER.warn("Failed to list mods with configs: {}", error.toString());
            return List.of();
        }
    }

    @Nullable
    public static ModConfig findConfig(String modId, ModConfig.Type type) {
        for (ModConfig config : configsFor(modId)) {
            if (config.getType() == type) {
                return config;
            }
        }
        return null;
    }

    public static Map<String, List<ModConfig>> snapshot() {
        if (CONFIGS_BY_MOD == null) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, List<ModConfig>> map =
                    (ConcurrentHashMap<String, List<ModConfig>>) CONFIGS_BY_MOD.get(ConfigTracker.INSTANCE);
            return map == null ? Map.of() : Map.copyOf(map);
        } catch (ReflectiveOperationException | ClassCastException error) {
            return Map.of();
        }
    }
}
