package net.unfamily.anotherconfigmanager.config;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Client-only helpers for {@link ConfigOpener}. Loaded via reflection from common code.
 */
public final class ConfigOpenerClient {
    private ConfigOpenerClient() {}

    public static boolean open(String modId, List<ModConfig> configs) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        minecraft.setScreen(new ConfigOpenerScreen(minecraft.screen, modId, configs));
        return true;
    }

    public static boolean openType(String modId, ModConfig config) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || config == null) {
            return false;
        }
        if (!ConfigAccessPolicy.canOpenType(config.getType())) {
            return false;
        }
        if (!(config.getSpec() instanceof ModConfigSpec spec) || spec.isEmpty()) {
            return false;
        }
        boolean editable = ConfigAccessPolicy.canEdit(config.getType());
        ConfigEditSession session = new ConfigEditSession(spec);
        ConfigOpenerScreen types = new ConfigOpenerScreen(minecraft.screen, modId, List.of(config));
        minecraft.setScreen(new ConfigOpenerScreen.SpecScreen(
                types, types, modId, config, session, List.of(), editable));
        return true;
    }

    public static boolean openModBrowser() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        minecraft.setScreen(new ConfigModBrowserScreen(minecraft.screen));
        return true;
    }
}
