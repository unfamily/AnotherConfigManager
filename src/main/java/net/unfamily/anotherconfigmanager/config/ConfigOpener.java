package net.unfamily.anotherconfigmanager.config;

import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.unfamily.anotherconfigmanager.AnotherConfigManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Public API: open a ModConfigSpec tree UI for a loaded mod on the client.
 * Screen opening is delegated to {@code ConfigOpenerClient} so dedicated servers do not load client classes.
 *
 * <p>Policy: claimed mods always use Library UI. Other mods prefer any registered
 * {@code IConfigScreenFactory} (including NeoForge {@code ConfigurationScreen}). Else Library UI.
 * Pass {@code replaceNeoForgeUi=true} only when explicitly skipping ConfigurationScreen.
 */
public final class ConfigOpener {
    private ConfigOpener() {}

    /**
     * Runtime claim so this mod always uses Library UI (prefer toml {@code iska_config_opener}).
     */
    public static void claim(String modId) {
        ConfigClaim.claim(modId);
    }

    public static boolean isClaimed(String modId) {
        return ConfigClaim.isClaimed(modId);
    }

    /**
     * Opens the config screen for {@link AnotherConfigManager#MOD_ID}.
     *
     * @return true if a screen was opened
     */
    public static boolean open() {
        return open(AnotherConfigManager.MOD_ID, false);
    }

    /**
     * Opens the config screen for the given mod id (vanilla factory preference, including ConfigurationScreen).
     *
     * @return true if a screen was opened
     */
    public static boolean open(String modId) {
        return open(modId, false);
    }

    /**
     * Opens the config screen for the given mod id on the client.
     *
     * @param replaceNeoForgeUi when true, skip NeoForge {@code ConfigurationScreen} and use Library UI instead
     * @return true if a screen was opened
     */
    public static boolean open(String modId, boolean replaceNeoForgeUi) {
        if (!ConfigDiscovery.isModLoaded(modId)) {
            return false;
        }
        if (ConfigClaim.isClaimed(modId)) {
            return openOurs(modId);
        }
        if (invokeOpenViaFactory(modId, replaceNeoForgeUi)) {
            return true;
        }
        return openOurs(modId);
    }

    /**
     * Opens a specific config type for a mod, or returns false if missing / not allowed.
     * Always uses Library UI (type drill-down is ours).
     */
    public static boolean open(String modId, ModConfig.Type type) {
        ModConfig config = ConfigDiscovery.findConfig(modId, type);
        if (config == null) {
            return false;
        }
        return invokeClientOpenType(modId, config);
    }

    @Nullable
    public static Component failureReason(String modId) {
        if (!ConfigDiscovery.isModLoaded(modId)) {
            return Component.translatable("commands.another_config_manager.config.unknown_mod", modId);
        }
        if (ConfigDiscovery.configsFor(modId).isEmpty() && !ConfigClaim.isClaimed(modId)) {
            if (!hasAnyOpenPath(modId)) {
                return Component.translatable("commands.another_config_manager.config.no_configs", modId);
            }
        }
        if (ConfigDiscovery.configsFor(modId).isEmpty()) {
            return Component.translatable("commands.another_config_manager.config.no_configs", modId);
        }
        return null;
    }

    /**
     * Opens the searchable mod config browser on the client.
     *
     * @return true if a screen was opened
     */
    public static boolean openModBrowser() {
        try {
            Class<?> client = Class.forName("net.unfamily.anotherconfigmanager.config.ConfigOpenerClient");
            Object result = client.getMethod("openModBrowser").invoke(null);
            return result instanceof Boolean bool && bool;
        } catch (Throwable error) {
            AnotherConfigManager.LOGGER.debug("Config mod browser unavailable (not on client?): {}", error.toString());
            return false;
        }
    }

    private static boolean hasAnyOpenPath(String modId) {
        return !ConfigDiscovery.configsFor(modId).isEmpty();
    }

    private static boolean openOurs(String modId) {
        List<ModConfig> configs = ConfigDiscovery.configsFor(modId);
        if (configs.isEmpty()) {
            return false;
        }
        return invokeClientOpen(modId, configs);
    }

    private static boolean invokeOpenViaFactory(String modId, boolean replaceNeoForgeUi) {
        try {
            Class<?> bootstrap = Class.forName("net.unfamily.anotherconfigmanager.config.ConfigScreenFactoryBootstrap");
            Object result = bootstrap
                    .getMethod("openViaRegisteredFactory", String.class, boolean.class)
                    .invoke(null, modId, replaceNeoForgeUi);
            return result instanceof Boolean bool && bool;
        } catch (Throwable error) {
            AnotherConfigManager.LOGGER.debug("Config factory open unavailable: {}", error.toString());
            return false;
        }
    }

    private static boolean invokeClientOpen(String modId, List<ModConfig> configs) {
        try {
            Class<?> client = Class.forName("net.unfamily.anotherconfigmanager.config.ConfigOpenerClient");
            Object result = client.getMethod("open", String.class, List.class).invoke(null, modId, configs);
            return result instanceof Boolean bool && bool;
        } catch (Throwable error) {
            AnotherConfigManager.LOGGER.debug("Config opener unavailable (not on client?): {}", error.toString());
            return false;
        }
    }

    private static boolean invokeClientOpenType(String modId, ModConfig config) {
        try {
            Class<?> client = Class.forName("net.unfamily.anotherconfigmanager.config.ConfigOpenerClient");
            Object result = client.getMethod("openType", String.class, ModConfig.class).invoke(null, modId, config);
            return result instanceof Boolean bool && bool;
        } catch (Throwable error) {
            AnotherConfigManager.LOGGER.debug("Config opener unavailable (not on client?): {}", error.toString());
            return false;
        }
    }
}
