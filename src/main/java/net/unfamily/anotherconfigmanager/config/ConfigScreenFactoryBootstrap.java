package net.unfamily.anotherconfigmanager.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.unfamily.anotherconfigmanager.AnotherConfigManager;

import java.util.List;
import java.util.Optional;

/**
 * Registers {@link IConfigScreenFactory} for claimed mods (always) and, when Configured
 * is absent, for other mods that have NeoForge configs and no factory yet.
 *
 * <p>Does not replace NeoForge {@link ConfigurationScreen} factories.
 *
 * <p>Runs at {@link FMLClientSetupEvent} HIGH so factories exist before Configured's
 * {@code FMLLoadComplete} sweep (Configured skips mods that already have a factory).
 */
public final class ConfigScreenFactoryBootstrap {
    private static boolean registered;

    private ConfigScreenFactoryBootstrap() {}

    /** Wire from {@link AnotherConfigManager} on the physical client. */
    public static void register(IEventBus modEventBus) {
        if (registered) {
            return;
        }
        registered = true;
        modEventBus.addListener(EventPriority.HIGH, ConfigScreenFactoryBootstrap::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ConfigScreenFactoryBootstrap::registerFactories);
    }

    private static void registerFactories() {
        boolean configuredLoaded = ModList.get().isLoaded("configured");
        ModList.get().forEachModContainer((modId, container) -> {
            List<ModConfig> configs = ConfigDiscovery.configsFor(modId);
            if (configs.isEmpty()) {
                return;
            }
            boolean claimed = ConfigClaim.isClaimed(modId);
            if (claimed) {
                registerOurFactory(container);
                return;
            }
            if (configuredLoaded) {
                return;
            }
            if (existingFactory(container).isPresent()) {
                return;
            }
            registerOurFactory(container);
        });
        AnotherConfigManager.LOGGER.info(
                "Config screen factories registered (configured present: {})",
                configuredLoaded);
    }

    private static Optional<IConfigScreenFactory> existingFactory(ModContainer container) {
        Optional<IConfigScreenFactory> forMod = IConfigScreenFactory.getForMod(container.getModInfo());
        if (forMod.isPresent()) {
            return forMod;
        }
        return container.getCustomExtension(IConfigScreenFactory.class);
    }

    /**
     * True when the factory is NeoForge's default {@link ConfigurationScreen} (method ref / lambda).
     */
    public static boolean isNeoForgeConfigurationFactory(IConfigScreenFactory factory) {
        if (factory == null) {
            return false;
        }
        return factory.getClass().getName().contains("ConfigurationScreen");
    }

    private static void registerOurFactory(ModContainer container) {
        String modId = container.getModId();
        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (ModContainer mod, Screen parent) -> createOurScreen(modId, parent));
    }

    private static Screen createOurScreen(String modId, Screen parent) {
        List<ModConfig> configs = ConfigDiscovery.configsFor(modId);
        return new ConfigOpenerScreen(parent, modId, configs);
    }

    /**
     * Opens via an existing {@link IConfigScreenFactory} on the mod.
     *
     * @param replaceNeoForgeUi when true, skip NeoForge {@link ConfigurationScreen} factories
     * @return true if a screen was opened
     */
    public static boolean openViaRegisteredFactory(String modId, boolean replaceNeoForgeUi) {
        return ModList.get().getModContainerById(modId).map(container -> {
            Optional<IConfigScreenFactory> factory = existingFactory(container);
            if (factory.isEmpty()) {
                return false;
            }
            if (replaceNeoForgeUi && isNeoForgeConfigurationFactory(factory.get())) {
                return false;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return false;
            }
            Screen screen = factory.get().createScreen(container, minecraft.screen);
            if (screen == null) {
                return false;
            }
            if (replaceNeoForgeUi && screen instanceof ConfigurationScreen) {
                return false;
            }
            minecraft.setScreen(screen);
            return true;
        }).orElse(false);
    }

    /** Compatibility: keep ConfigurationScreen factories. */
    public static boolean openViaRegisteredFactory(String modId) {
        return openViaRegisteredFactory(modId, false);
    }
}
