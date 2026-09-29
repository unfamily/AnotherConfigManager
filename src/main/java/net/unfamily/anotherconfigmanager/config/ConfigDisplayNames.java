package net.unfamily.anotherconfigmanager.config;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.Locale;

/**
 * Human-readable labels for config options and sections.
 */
public final class ConfigDisplayNames {
    private ConfigDisplayNames() {}

    /**
     * Localized mod display title for config screens.
     */
    public static Component modTitle(String modId) {
        String langKey = "another_config_manager.config.mod." + modId;
        if (Language.getInstance().has(langKey)) {
            return Component.translatable(langKey);
        }
        return ModList.get().getModContainerById(modId)
                .map(container -> Component.literal(container.getModInfo().getDisplayName()))
                .orElseGet(() -> Component.literal(modId == null ? "?" : modId));
    }

    public static String displayName(String modId, List<String> path, String rawKey) {
        String joined = ConfigSpecNodes.joinPath(path);
        String key = "config." + modId + ".option." + joined;
        if (Language.getInstance().has(key)) {
            return Component.translatable(key).getString();
        }
        return formatRawKey(rawKey);
    }

    public static String sectionDisplayName(String modId, List<String> path, String rawKey) {
        String joined = ConfigSpecNodes.joinPath(path);
        String key = "config." + modId + ".section." + joined;
        if (Language.getInstance().has(key)) {
            return Component.translatable(key).getString();
        }
        return formatRawKey(rawKey);
    }

    public static String formatRawKey(String rawKey) {
        if (rawKey == null || rawKey.isEmpty()) {
            return "";
        }
        String normalized = rawKey.replace('-', '_');
        StringBuilder out = new StringBuilder();
        for (String part : splitWords(normalized)) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out.toString();
    }

    private static String[] splitWords(String input) {
        String spaced = input
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2");
        return spaced.split("_+");
    }
}
