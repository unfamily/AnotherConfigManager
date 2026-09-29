package net.unfamily.anotherconfigmanager;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(AnotherConfigManager.MOD_ID)
public class AnotherConfigManager {
    public static final String MOD_ID = "another_config_manager";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AnotherConfigManager(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, AcmConfig.SPEC);
        net.unfamily.anotherconfigmanager.config.CsvRuleRegistry.registerBuiltinRules();
        net.unfamily.anotherconfigmanager.config.ColorRuleRegistry.registerBuiltinRules();
        if (isPhysicalClient()) {
            try {
                Class.forName("net.unfamily.anotherconfigmanager.config.ConfigScreenFactoryBootstrap")
                        .getMethod("register", IEventBus.class)
                        .invoke(null, modEventBus);
            } catch (Throwable error) {
                LOGGER.error("Failed to register config screen factories", error);
            }
        }
    }

    private static boolean isPhysicalClient() {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
