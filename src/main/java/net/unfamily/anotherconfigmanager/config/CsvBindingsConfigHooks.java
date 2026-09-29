package net.unfamily.anotherconfigmanager.config;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.unfamily.anotherconfigmanager.AnotherConfigManager;

/**
 * Reloads CSV bindings from Library toml when the common config loads or reloads.
 */
@EventBusSubscriber(modid = AnotherConfigManager.MOD_ID)
public final class CsvBindingsConfigHooks {
    private CsvBindingsConfigHooks() {}

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getModId().equals(AnotherConfigManager.MOD_ID)) {
            CsvRuleRegistry.reloadFromAcmConfig();
            ColorRuleRegistry.reloadFromAcmConfig();
        }
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(AnotherConfigManager.MOD_ID)) {
            CsvRuleRegistry.reloadFromAcmConfig();
            ColorRuleRegistry.reloadFromAcmConfig();
        }
    }
}
