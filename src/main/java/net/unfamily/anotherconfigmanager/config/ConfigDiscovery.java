package net.unfamily.anotherconfigmanager.config;

import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers {@link ModConfig} instances registered for a mod id via {@link ModConfigs}.
 */
public final class ConfigDiscovery {
    private ConfigDiscovery() {}

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    public static List<ModConfig> configsFor(String modId) {
        if (modId == null || modId.isBlank()) {
            return List.of();
        }
        List<ModConfig> configs = ModConfigs.getModConfigs(modId);
        if (configs == null || configs.isEmpty()) {
            return List.of();
        }
        return List.copyOf(configs);
    }

    public static List<String> modIdsWithConfigs() {
        Map<String, ModConfig> fileMap = ModConfigs.getFileMap();
        if (fileMap == null || fileMap.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (ModConfig config : fileMap.values()) {
            String modId = config.getModId();
            if (modId != null && !modId.isBlank() && !ids.contains(modId)) {
                ids.add(modId);
            }
        }
        Collections.sort(ids);
        return ids;
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
        Map<String, List<ModConfig>> out = new LinkedHashMap<>();
        for (String modId : modIdsWithConfigs()) {
            out.put(modId, configsFor(modId));
        }
        return Map.copyOf(out);
    }
}
