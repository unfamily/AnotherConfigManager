package net.unfamily.anotherconfigmanager.config;

import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Adaptive labels and ordering for {@link ModConfig.Type} entries in the opener list.
 */
public final class ConfigTypeLabels {
    private ConfigTypeLabels() {}

    public static Component label(ModConfig.Type type) {
        String key = "screen.another_config_manager.config.type." + type.name().toLowerCase(Locale.ROOT);
        Component translated = Component.translatable(key);
        if (translated.getString().equals(key)) {
            return Component.literal(formatUnknown(type.name()));
        }
        return translated;
    }

    public static String formatUnknown(String typeName) {
        String lower = typeName.toLowerCase(Locale.ROOT).replace('_', ' ');
        if (lower.isEmpty()) {
            return typeName;
        }
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public static List<ModConfig> sorted(List<ModConfig> configs) {
        List<ModConfig> out = new ArrayList<>(configs);
        out.sort(Comparator
                .comparingInt((ModConfig c) -> typeOrder(c.getType()))
                .thenComparing(c -> c.getType().name(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ModConfig::getFileName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static int typeOrder(ModConfig.Type type) {
        return switch (type) {
            case CLIENT -> 0;
            case COMMON -> 1;
            case SERVER -> 2;
            case STARTUP -> 3;
            default -> 100;
        };
    }
}
